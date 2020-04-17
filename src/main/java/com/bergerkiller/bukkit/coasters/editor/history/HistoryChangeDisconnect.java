package com.bergerkiller.bukkit.coasters.editor.history;

import java.util.List;

import com.bergerkiller.bukkit.coasters.objects.TrackObject;
import com.bergerkiller.bukkit.coasters.tracks.TrackLockedException;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;

public class HistoryChangeDisconnect extends HistoryChangeConnect {

    public HistoryChangeDisconnect(TrackNode nodeA, TrackNode nodeB, List<TrackObject> objects) {
        super(nodeA, nodeB, objects);
    }

    @Override
    protected void run(boolean undo) throws TrackLockedException {
        super.run(!undo);
    }
}
