package com.bergerkiller.bukkit.coasters.animation;

import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnectionState;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeState;
import com.bergerkiller.bukkit.tc.attachments.animation.AnimationEasing;

public class TrackAnimation {
    /** Name of the animation being played. Can be empty String if omitted. */
    public final String name;
    public final TrackNode node;
    public final TrackNodeState start;
    public final TrackNodeState target;
    public final TrackConnectionState[] connections;
    public final int ticks_total;
    public int ticks;
    public AnimationEasing easing = AnimationEasing.LINEAR;

    public TrackAnimation(String name, TrackNode node, TrackNodeState target, TrackConnectionState[] connections, int ticks_total) {
        this.name = name;
        this.node = node;
        this.start = node.getState();
        this.target = target;
        this.connections = connections;
        this.ticks_total = ticks_total;
        this.ticks = 1;
    }

    public TrackAnimation(String name, TrackNode node, TrackNodeState target, TrackConnectionState[] connections, int ticks_total, AnimationEasing easing) {
        this.name = name;
        this.node = node;
        this.start = node.getState();
        this.target = target;
        this.connections = connections;
        this.ticks_total = ticks_total;
        this.ticks = 1;
        this.easing = easing;
    }

    public boolean isAtStart() {
        return this.ticks == 1;
    }

    public boolean isAtEnd() {
        return this.ticks >= this.ticks_total;
    }

    public AnimationEasing getEasing() {
        return easing;
    }

    /**
     * Gets whether an existing connection of this track animation's node owner should be kept
     * after the animation completed.
     *
     * @param connection Connection of {@link #node}
     * @return True if it should be kept at the end of the animation
     */
    public boolean shouldKeepConnection(TrackConnection connection) {
        if (connections == null) {
            return true;
        }

        TrackNode otherNode = connection.getOtherNode(this.node);
        for (TrackConnectionState animConn : connections) {
            if (animConn.isConnected(otherNode)) {
                return true;
            }
        }
        return false;
    }
}
