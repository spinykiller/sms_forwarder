package com.enixcoda.smsforward;

import android.content.ComponentName;
import android.content.Context;
import android.service.quicksettings.TileService;

import androidx.preference.PreferenceManager;

/** The pause switch, shared by settings, the Quick Settings tile and the widget. */
final class Pause {
    private Pause() {
    }

    static void toggle(Context context) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(context.getString(R.string.key_paused), !Dispatcher.isPaused(context)).apply();
        refresh(context);
    }

    /** Redraws the tile and widgets after the switch changes anywhere. */
    static void refresh(Context context) {
        TileService.requestListeningState(context, new ComponentName(context, PauseTileService.class));
        PauseWidget.updateAll(context);
    }
}
