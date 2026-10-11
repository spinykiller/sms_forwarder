package com.enixcoda.smsforward;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick Settings tile: tap to pause or resume forwarding. */
public class PauseTileService extends TileService {
    @Override
    public void onStartListening() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean paused = Dispatcher.isPaused(this);
        tile.setState(paused ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);
        tile.setContentDescription(getString(paused ? R.string.widget_paused : R.string.widget_on));
        tile.updateTile();
    }

    @Override
    public void onClick() {
        Pause.toggle(this);
        onStartListening();
    }
}
