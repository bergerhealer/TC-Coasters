package com.bergerkiller.bukkit.coasters.editor.history;

import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeState;
import com.bergerkiller.bukkit.coasters.world.CoasterWorldAccess;

/**
 * Changes the position and/or orientation of a node
 */
public class HistoryChangeNode extends HistoryChange {
    private final TrackNodeState from;
    private final TrackNodeState to;

    public HistoryChangeNode(CoasterWorldAccess world,
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
    protected final void run(boolean undo) {
        if (undo) {
            TrackNode node = world.getTracks().findNodeExact(this.to.position);
            if (node != null) {
                node.setPosition(this.from.position);
                node.setOrientation(this.from.orientation);
                node.setRailBlock(this.from.railBlock);
            }
        } else {
            TrackNode node = world.getTracks().findNodeExact(this.from.position);
            if (node != null) {
                node.setPosition(this.to.position);
                node.setOrientation(this.to.orientation);
                node.setRailBlock(this.to.railBlock);
            }
        }
    }

}
