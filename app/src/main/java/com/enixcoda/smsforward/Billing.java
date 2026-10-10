package com.enixcoda.smsforward;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.preference.PreferenceManager;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Play Billing for Pro: buy / query / acknowledge. The entitlement is cached in default prefs
 * ({@link #isPro}) so the SMS receiver never touches Billing.
 */
// ponytail: verified locally (no server) and refreshed when the app opens, so a lapsed subscription stays
// Pro until the next open; add server verification / a periodic refresh if abuse shows up.
public final class Billing implements PurchasesUpdatedListener, BillingClientStateListener {
    /** Play Console product IDs (Monetize > Products / Subscriptions). */
    static final String PRO = "pro";                   // SUBS: base plans "annual" (offer "trial": free trial) and "monthly"
    static final String PRO_LIFETIME = "pro_lifetime"; // INAPP, one payment

    /** What the Pro screen sells. */
    enum Plan { ANNUAL, MONTHLY, LIFETIME }

    private static final String KEY_PRO = "pro_active";
    private static final long INITIAL_RETRY_MS = 1_000;
    private static final long MAX_RETRY_MS = 60_000;

    private static Billing instance;

    private final Context context;
    private final BillingClient client;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private long retryMs = INITIAL_RETRY_MS;

    volatile ProductDetails proDetails;
    volatile ProductDetails lifetimeDetails;
    /** Play couldn't be reached (no Play Store, signed out, offline) on the last connection attempt. */
    volatile boolean unavailable;
    /** A purchase is awaiting payment (e.g. cash / UPI pending) — not granted until it completes. */
    volatile boolean pending;
    volatile String lastError;

    static synchronized Billing get(Context context) {
        if (instance == null) instance = new Billing(context.getApplicationContext());
        return instance;
    }

    static boolean isPro(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY_PRO, false);
    }

    private Billing(Context context) {
        this.context = context;
        client = BillingClient.newBuilder(context)
                .setListener(this)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build();
        connect();
    }

    /** Listeners run on the main thread whenever products, purchases or errors change. */
    void addListener(Runnable listener) {
        listeners.add(listener);
    }

    void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void notifyChanged() {
        main.post(() -> {
            for (Runnable listener : listeners) listener.run();
        });
    }

    private void connect() {
        client.startConnection(this);
    }

    @Override
    public void onBillingSetupFinished(BillingResult result) {
        unavailable = result.getResponseCode() != BillingClient.BillingResponseCode.OK;
        if (unavailable) {
            notifyChanged();
            reconnectLater();
            return;
        }
        retryMs = INITIAL_RETRY_MS;
        query(PRO, BillingClient.ProductType.SUBS, details -> proDetails = details);
        query(PRO_LIFETIME, BillingClient.ProductType.INAPP, details -> lifetimeDetails = details);
        refresh();
    }

    // Play Store updates / gets killed mid-session: reconnect, or purchases would go unacknowledged
    // (auto-refunded after 3 days) until a cold restart.
    @Override
    public void onBillingServiceDisconnected() {
        reconnectLater();
    }

    private void reconnectLater() {
        main.postDelayed(this::connect, retryMs);
        retryMs = Math.min(retryMs * 2, MAX_RETRY_MS);
    }

    // One query per product type — Billing rejects a list that mixes INAPP and SUBS.
    private void query(String productId, String type, Consumer<ProductDetails> set) {
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId).setProductType(type).build()))
                .build();
        client.queryProductDetailsAsync(params, (result, details) -> {
            for (ProductDetails d : details.getProductDetailsList()) {
                if (d.getProductId().equals(productId)) set.accept(d);
            }
            notifyChanged();
        });
    }

    /** Re-reads owned purchases (call when a screen starts). Connection setup refreshes on its own. */
    void refresh() {
        if (!client.isReady()) return;
        client.queryPurchasesAsync(purchasesOf(BillingClient.ProductType.INAPP), (inappResult, inapp) ->
                client.queryPurchasesAsync(purchasesOf(BillingClient.ProductType.SUBS), (subsResult, subs) -> {
                    // A failed query must never be treated as "not owned" — that would re-mark a paying
                    // user as free on a transient network/Play blip.
                    if (inappResult.getResponseCode() != BillingClient.BillingResponseCode.OK
                            || subsResult.getResponseCode() != BillingClient.BillingResponseCode.OK) return;
                    List<Purchase> all = new ArrayList<>(inapp);
                    all.addAll(subs);
                    handle(all);
                }));
    }

    private static QueryPurchasesParams purchasesOf(String type) {
        return QueryPurchasesParams.newBuilder().setProductType(type).build();
    }

    // Play returns only active subscriptions, so an expired one drops Pro here.
    private void handle(List<Purchase> purchases) {
        boolean pro = false;
        boolean isPending = false;
        for (Purchase p : purchases) {
            if (!p.getProducts().contains(PRO) && !p.getProducts().contains(PRO_LIFETIME)) continue;
            if (p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                pro = true;
                acknowledgeIfNeeded(p);
            } else if (p.getPurchaseState() == Purchase.PurchaseState.PENDING) {
                isPending = true;
            }
        }
        pending = isPending;
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(KEY_PRO, pro).apply();
        notifyChanged();
    }

    // Unacknowledged purchases are auto-refunded after 3 days; re-acknowledged on the next refresh if this fails.
    private void acknowledgeIfNeeded(Purchase p) {
        if (p.isAcknowledged()) return;
        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.getPurchaseToken()).build(), result -> { });
    }

    @Override
    public void onPurchasesUpdated(BillingResult result, List<Purchase> purchases) {
        switch (result.getResponseCode()) {
            // Re-query everything: the callback lists only this purchase, not what the user already owns.
            case BillingClient.BillingResponseCode.OK:
            // Already bought (e.g. reinstall) — restore instead of leaving the user thinking they must pay again.
            case BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED:
                refresh();
                break;
            case BillingClient.BillingResponseCode.USER_CANCELED:
                break;
            default:
                lastError = result.getDebugMessage();
                notifyChanged();
        }
    }

    /** The subscription offer for a plan: annual prefers the free-trial offer (Play lists it only while the user is eligible). */
    ProductDetails.SubscriptionOfferDetails offerFor(Plan plan) {
        ProductDetails details = proDetails;
        if (details == null || details.getSubscriptionOfferDetails() == null || plan == Plan.LIFETIME) return null;
        String basePlan = plan == Plan.ANNUAL ? "annual" : "monthly";
        ProductDetails.SubscriptionOfferDetails base = null;
        for (ProductDetails.SubscriptionOfferDetails offer : details.getSubscriptionOfferDetails()) {
            if (!offer.getBasePlanId().equals(basePlan)) continue;
            if (plan == Plan.ANNUAL && "trial".equals(offer.getOfferId())) return offer;
            if (offer.getOfferId() == null) base = offer;
        }
        return base;
    }

    /** Opens Play's purchase sheet; false if the plan isn't available (products not loaded). */
    boolean buy(Activity activity, Plan plan) {
        ProductDetails details = plan == Plan.LIFETIME ? lifetimeDetails : proDetails;
        ProductDetails.SubscriptionOfferDetails offer = offerFor(plan);
        if (details == null || (plan != Plan.LIFETIME && offer == null)) return false;
        BillingFlowParams.ProductDetailsParams.Builder params = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details);
        // Subscriptions require an offer token; one-time products use their default offer.
        if (offer != null) params.setOfferToken(offer.getOfferToken());
        lastError = null;
        BillingResult result = client.launchBillingFlow(activity, BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(params.build()))
                .build());
        return result.getResponseCode() == BillingClient.BillingResponseCode.OK;
    }
}
