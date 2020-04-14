package com.bergerkiller.bukkit.coasters.editor.history;

import java.util.List;

import com.bergerkiller.bukkit.coasters.objects.TrackObject;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnectionState;
import com.bergerkiller.bukkit.coasters.tracks.TrackLockedException;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.world.CoasterWorld;

public class HistoryChangeDisconnect extends HistoryChangeConnect {

    public HistoryChangeDisconnect(TrackNode nodeA, TrackNode nodeB, List<TrackObject> objects) {
        super(nodeA, nodeB, objects);
    }

    public HistoryChangeDisconnect(CoasterWorld world, TrackConnectionState state) {
        super(world, state);
    }

    @Override
    protected void run(boolean undo) throws TrackLockedException {
        super.run(!undo);
    }
}
