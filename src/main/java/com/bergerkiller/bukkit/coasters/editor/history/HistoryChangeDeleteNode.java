package com.bergerkiller.bukkit.coasters.editor.history;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.world.CoasterWorldAccess;

/**
 * Deletes a node. Does not create or restore connections
 * with other nodes, which should be done using child history changes.
 */
public class HistoryChangeDeleteNode extends HistoryChangeCreateNode {

    public HistoryChangeDeleteNode(TrackNode node) {
        super(node);
    }

    public HistoryChangeDeleteNode(CoasterWorldAccess world, String coasterName, Vector position, Vector up) {
        super(world, coasterName, position, up);
    }

    @Override
    protected void run(boolean undo) {
        super.run(!undo);
    }
}
