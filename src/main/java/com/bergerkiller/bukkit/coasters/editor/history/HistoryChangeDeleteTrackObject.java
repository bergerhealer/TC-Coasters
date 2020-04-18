package com.bergerkiller.bukkit.coasters.editor.history;

import com.bergerkiller.bukkit.coasters.objects.TrackObject;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackLockedException;

/**
 * Removes a track object from a connection between two nodes
 */
public class HistoryChangeDeleteTrackObject extends HistoryChangeCreateTrackObject {

    public HistoryChangeDeleteTrackObject(TrackConnection connection, TrackObject object) {
        super(connection, object);
    }

    @Override
    protected void run(boolean undo) throws TrackLockedException {
        super.run(!undo);
    }
}
