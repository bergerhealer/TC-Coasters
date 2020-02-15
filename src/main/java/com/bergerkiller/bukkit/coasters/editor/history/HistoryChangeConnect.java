package com.bergerkiller.bukkit.coasters.editor.history;

import com.bergerkiller.bukkit.coasters.tracks.TrackConnectionState;
import com.bergerkiller.bukkit.coasters.tracks.TrackLockedException;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.world.CoasterWorld;

/**
 * Connects or disconnects two nodes
 */
public class HistoryChangeConnect extends HistoryChange {
    private final TrackConnectionState state;

    public HistoryChangeConnect(TrackNode nodeA, TrackNode nodeB) {
        this(nodeA.getWorld(), TrackConnectionState.create(nodeA.getPosition(), nodeB.getPosition()));
    }

    public HistoryChangeConnect(CoasterWorld world, TrackConnectionState state) {
        super(world);
        this.state = state;
    }

    @Override
    protected void run(boolean undo) throws TrackLockedException {
        TrackNode nodeA = this.world.getTracks().findNodeExact(this.state.node_pos_a);
        TrackNode nodeB = this.world.getTracks().findNodeExact(this.state.node_pos_b);
        if (nodeA != null && nodeB != null) {
            if (nodeA.isLocked() || nodeB.isLocked()) {
                throw new TrackLockedException();
            }
            if (undo) {
                this.world.getTracks().disconnect(nodeA, nodeB);
            } else {
                this.world.getTracks().connect(nodeA, nodeB);
            }
        }
    }
}
