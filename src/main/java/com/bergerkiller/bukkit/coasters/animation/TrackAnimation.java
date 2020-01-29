package com.bergerkiller.bukkit.coasters.animation;

import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeReference;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeState;

public class TrackAnimation {
    public final TrackNode node;
    public final TrackNodeState start;
    public final TrackNodeState target;
    public final TrackNodeReference[] target_connections;
    public final int ticks_total;
    public int ticks;

    public TrackAnimation(TrackNode node, TrackNodeState target, TrackNodeReference[] target_connections, int ticks_total) {
        this.node = node;
        this.start = node.getState();
        this.target = target;
        this.target_connections = target_connections;
        this.ticks_total = ticks_total;
        this.ticks = 0;
    }
}
