package com.bergerkiller.bukkit.coasters.editor.history;

import com.bergerkiller.bukkit.coasters.objects.TrackObject;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnectionState;
import com.bergerkiller.bukkit.coasters.tracks.TrackLockedException;

/**
 * Creates a track object on a connection
 */
public class HistoryChangeCreateTrackObject extends HistoryChange {
    private final TrackConnectionState connection;
    private final TrackObject object;

    public HistoryChangeCreateTrackObject(TrackConnection connection, TrackObject object) {
        super(connection.getWorld());
        this.connection = TrackConnectionState.createDereferencedNoObjects(connection);
        this.object = object.clone();
    }

    @Override
    protected void run(boolean undo) throws TrackLockedException {
        TrackConnection connection = this.connection.findOnWorld(this.world.getTracks());
        if (connection == null) {
            return;
        } else if (connection.isLocked()) {
            throw new TrackLockedException();
        }
        if (undo) {
            // Delete track object
            connection.removeObject(this.object);
        } else {
            // Create track object
            connection.addObject(this.object.clone());
        }
    }
}
