package com.bergerkiller.bukkit.coasters.editor.history;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.world.CoasterWorldAccess;

public class HistoryChangeDisconnect extends HistoryChangeConnect {

    public HistoryChangeDisconnect(TrackNode nodeA, TrackNode nodeB) {
        super(nodeA, nodeB);
    }

    public HistoryChangeDisconnect(CoasterWorldAccess world, Vector nodePosA, Vector nodePosB) {
        super(world, nodePosA, nodePosB);
    }

    @Override
    protected void run(boolean undo) {
        super.run(!undo);
    }
}
