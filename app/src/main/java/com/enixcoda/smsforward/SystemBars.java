package com.enixcoda.smsforward;

import android.app.Activity;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

final class SystemBars {
    private SystemBars() {
    }

    /** Draws behind the system bars on every Android version (enforced from Android 15): the header pads below the status bar, the rest above the navigation bar. */
    static void apply(Activity activity, View header) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        int headerPadding = header.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(activity.findViewById(android.R.id.content), (content, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            header.setPadding(headerPadding + bars.left, headerPadding + bars.top, headerPadding + bars.right, headerPadding);
            content.setPadding(bars.left, 0, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
