package com.bergerkiller.bukkit.coasters.editor.history;

import com.bergerkiller.bukkit.coasters.tracks.TrackLockedException;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeState;
import com.bergerkiller.bukkit.coasters.world.CoasterWorld;

/**
 * Deletes a node. Does not create or restore connections
 * with other nodes, which should be done using child history changes.
 */
public class HistoryChangeDeleteNode extends HistoryChangeCreateNode {

    public HistoryChangeDeleteNode(TrackNode node) {
        super(node);
    }

    public HistoryChangeDeleteNode(CoasterWorld world, String coasterName, TrackNodeState state) {
        super(world, coasterName, state);
    }

    @Override
    protected void run(boolean undo) throws TrackLockedException {
        super.run(!undo);
    }
}
