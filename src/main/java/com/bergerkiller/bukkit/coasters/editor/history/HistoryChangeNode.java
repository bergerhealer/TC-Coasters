package com.bergerkiller.bukkit.coasters.editor.history;

import com.bergerkiller.bukkit.coasters.tracks.TrackLockedException;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeState;
import com.bergerkiller.bukkit.coasters.world.CoasterWorld;

/**
 * Changes the position and/or orientation of a node
 */
public class HistoryChangeNode extends HistoryChange {
    private final TrackNodeState from;
    private final TrackNodeState to;

    public HistoryChangeNode(CoasterWorld world,
            TrackNodeState from, TrackNodeState to)
    {
        super(world);
        if (from == null) {
            throw new IllegalArgumentException("from can not be null");
        }
        if (to == null) {
            throw new IllegalArgumentException("to can not be null");
        }
        this.from = from;
        this.to = to;
    }

    @Override
    protected final void run(boolean undo) throws TrackLockedException {
        if (undo) {
            TrackNode node = world.getTracks().findNodeExact(this.to.position);
            if (node != null) {
                if (node.isLocked()) {
                    throw new TrackLockedException();
                }
                node.setState(this.from);
            }
        } else {
            TrackNode node = world.getTracks().findNodeExact(this.from.position);
            if (node != null) {
                if (node.isLocked()) {
                    throw new TrackLockedException();
                }
                node.setState(this.to);
            }
        }
    }

}
