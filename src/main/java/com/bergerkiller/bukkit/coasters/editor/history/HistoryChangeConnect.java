package com.bergerkiller.bukkit.coasters.editor.history;

import java.util.List;

import com.bergerkiller.bukkit.coasters.objects.TrackObject;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnectionState;
import com.bergerkiller.bukkit.coasters.tracks.TrackLockedException;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;

/**
 * Connects or disconnects two nodes
 */
public class HistoryChangeConnect extends HistoryChange {
    private final TrackConnectionState state;

    public HistoryChangeConnect(TrackNode nodeA, TrackNode nodeB, List<TrackObject> objects) {
        super(nodeA.getWorld());
        this.state = TrackConnectionState.createDereferenced(nodeA.getPosition(), nodeB.getPosition(), objects);
    }

    @Override
    protected void run(boolean undo) throws TrackLockedException {
        if (undo) {
            this.world.getTracks().disconnect(this.state);
        } else {
            this.world.getTracks().connect(this.state, true);
        }
    }
}
