package com.enixcoda.smsforward;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.billingclient.api.ProductDetails;

import java.util.List;

/**
 * The Pro paywall (also where "Remove ads" leads). No ad on this screen. Annual (with the free trial
 * when eligible) is pre-selected; monthly and lifetime are the alternatives. Prices come from Play.
 */
public class ProActivity extends AppCompatActivity {
    private Billing billing;
    private RadioGroup plans;
    private RadioButton annual, monthly, lifetime;
    private TextView terms, status;
    private Button buy;
    private final Runnable render = this::render;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pro);
        SystemBars.apply(this, findViewById(R.id.header));
        billing = Billing.get(this);
        plans = findViewById(R.id.plans);
        annual = findViewById(R.id.plan_annual);
        monthly = findViewById(R.id.plan_monthly);
        lifetime = findViewById(R.id.plan_lifetime);
        terms = findViewById(R.id.terms);
        status = findViewById(R.id.status);
        buy = findViewById(R.id.buy);

        plans.setOnCheckedChangeListener((group, id) -> render());
        buy.setOnClickListener(v -> {
            if (!billing.buy(this, selectedPlan())) Toast.makeText(this, R.string.pro_store_unavailable, Toast.LENGTH_LONG).show();
        });
        findViewById(R.id.restore).setOnClickListener(v -> {
            billing.refresh();
            Toast.makeText(this, R.string.pro_restoring, Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.manage).setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/account/subscriptions?package=" + getPackageName()))));
    }

    @Override
    protected void onStart() {
        super.onStart();
        billing.addListener(render);
        billing.refresh();
        render();
    }

    @Override
    protected void onStop() {
        super.onStop();
        billing.removeListener(render);
    }

    private Billing.Plan selectedPlan() {
        int id = plans.getCheckedRadioButtonId();
        return id == R.id.plan_monthly ? Billing.Plan.MONTHLY : id == R.id.plan_lifetime ? Billing.Plan.LIFETIME : Billing.Plan.ANNUAL;
    }

    private void render() {
        ProductDetails.SubscriptionOfferDetails annualOffer = billing.offerFor(Billing.Plan.ANNUAL);
        ProductDetails.SubscriptionOfferDetails monthlyOffer = billing.offerFor(Billing.Plan.MONTHLY);
        ProductDetails lifetimeDetails = billing.lifetimeDetails;
        String annualPrice = recurringPrice(annualOffer);
        String monthlyPrice = recurringPrice(monthlyOffer);
        String lifetimePrice = lifetimeDetails != null && lifetimeDetails.getOneTimePurchaseOfferDetails() != null
                ? lifetimeDetails.getOneTimePurchaseOfferDetails().getFormattedPrice() : null;
        boolean trial = annualOffer != null && "trial".equals(annualOffer.getOfferId());

        annual.setText(getString(trial ? R.string.pro_plan_annual_trial : R.string.pro_plan_annual, priceOrDots(annualPrice)));
        monthly.setText(getString(R.string.pro_plan_monthly, priceOrDots(monthlyPrice)));
        lifetime.setText(getString(R.string.pro_plan_lifetime, priceOrDots(lifetimePrice)));
        annual.setEnabled(annualPrice != null);
        monthly.setEnabled(monthlyPrice != null);
        lifetime.setEnabled(lifetimePrice != null);

        // Renewal terms before the purchase, not after (trust > a surprise charge).
        Billing.Plan plan = selectedPlan();
        terms.setText(plan == Billing.Plan.LIFETIME ? getString(R.string.pro_terms_lifetime)
                : plan == Billing.Plan.ANNUAL && trial ? getString(R.string.pro_terms_trial, priceOrDots(annualPrice))
                : getString(R.string.pro_terms_subscription, priceOrDots(plan == Billing.Plan.ANNUAL ? annualPrice : monthlyPrice)));

        boolean pro = Billing.isPro(this);
        boolean available = plan == Billing.Plan.ANNUAL ? annualPrice != null : plan == Billing.Plan.MONTHLY ? monthlyPrice != null : lifetimePrice != null;
        buy.setEnabled(!pro && available);
        status.setText(pro ? getString(R.string.pro_active)
                : billing.pending ? getString(R.string.pro_pending)
                : billing.lastError != null ? billing.lastError
                : billing.unavailable ? getString(R.string.pro_store_unavailable)
                : "");
    }

    /** The price the plan renews at (the last pricing phase; earlier phases are the trial). */
    private static String recurringPrice(ProductDetails.SubscriptionOfferDetails offer) {
        if (offer == null) return null;
        List<ProductDetails.PricingPhase> phases = offer.getPricingPhases().getPricingPhaseList();
        return phases.isEmpty() ? null : phases.get(phases.size() - 1).getFormattedPrice();
    }

    private static String priceOrDots(String price) {
        return price != null ? price : "…";
    }
}
