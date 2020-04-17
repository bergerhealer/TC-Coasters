package com.bergerkiller.bukkit.coasters.animation;

import com.bergerkiller.bukkit.coasters.tracks.TrackConnectionState;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeState;

public class TrackAnimation {
    public final TrackNode node;
    public final TrackNodeState start;
    public final TrackNodeState target;
    public final TrackConnectionState[] connections;
    public final int ticks_total;
    public int ticks;

    public TrackAnimation(TrackNode node, TrackNodeState target, TrackConnectionState[] connections, int ticks_total) {
        this.node = node;
        this.start = node.getState();
        this.target = target;
        this.connections = connections;
        this.ticks_total = ticks_total;
        this.ticks = 1;
    }

    public boolean isAtStart() {
        return this.ticks == 1;
    }

    public boolean isAtEnd() {
        return this.ticks >= this.ticks_total;
    }
}
