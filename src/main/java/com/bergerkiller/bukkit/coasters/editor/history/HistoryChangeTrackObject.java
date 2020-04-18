package com.bergerkiller.bukkit.coasters.editor.history;

import com.bergerkiller.bukkit.coasters.objects.TrackObject;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnectionState;
import com.bergerkiller.bukkit.coasters.tracks.TrackLockedException;

/**
 * Changes the position, bound connection or other properties of a track object
 */
public class HistoryChangeTrackObject extends HistoryChange {
    private final TrackConnectionState old_connection, new_connection;
    private final TrackObject old_object, new_object;

    public HistoryChangeTrackObject(TrackConnection old_connection, TrackConnection new_connection, TrackObject old_object, TrackObject new_object) {
        super(old_connection.getWorld());
        this.old_connection = TrackConnectionState.createDereferencedNoObjects(old_connection);
        this.new_connection = TrackConnectionState.createDereferencedNoObjects(new_connection);
        this.old_object = old_object.clone();
        this.new_object = new_object.clone();
    }

    @Override
    protected void run(boolean undo) throws TrackLockedException {
        TrackConnection old_connection = this.old_connection.findOnWorld(this.world.getTracks());
        TrackConnection new_connection = this.new_connection.findOnWorld(this.world.getTracks());
        if (old_connection == null || new_connection == null) {
            return;
        } else if (old_connection != new_connection) {
            // Changed between connections. Remove from old, add to new
            if (old_connection.isLocked() || new_connection.isLocked()) {
                throw new TrackLockedException();
            }

            // Simply re-add
            if (undo) {
                new_connection.removeObject(this.new_object);
                old_connection.addObject(this.old_object.clone());
            } else {
                old_connection.removeObject(this.old_object);
                new_connection.addObject(this.new_object.clone());
            }            
        } else {
            // Changed on the same connection
            if (new_connection.isLocked()) {
                throw new TrackLockedException();
            }

            //TODO: This could be improved by NOT respawning it!
            if (undo) {
                new_connection.removeObject(this.new_object);
                new_connection.addObject(this.old_object.clone());
            } else {
                new_connection.removeObject(this.old_object);
                new_connection.addObject(this.new_object.clone());
            }
        }
    }
}
