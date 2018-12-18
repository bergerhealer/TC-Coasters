package com.bergerkiller.bukkit.coasters.editor.history;

import com.bergerkiller.bukkit.coasters.tracks.TrackConnectionState;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.world.CoasterWorldAccess;

public class HistoryChangeDisconnect extends HistoryChangeConnect {

    public HistoryChangeDisconnect(TrackNode nodeA, TrackNode nodeB) {
        super(nodeA, nodeB);
    }

    public HistoryChangeDisconnect(CoasterWorldAccess world, TrackConnectionState state) {
        super(world, state);
    }

    @Override
    protected void run(boolean undo) {
        super.run(!undo);
    }
}
