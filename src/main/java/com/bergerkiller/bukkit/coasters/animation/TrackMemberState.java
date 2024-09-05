package com.bergerkiller.bukkit.coasters.animation;

import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import org.bukkit.util.Vector;

/**
 * Stores the state of a Minecart Member on the tracks.
 * This snapshot is used to preserve the position on tracks
 * while an animation changes the tracks, and to restore it.
 */
public class TrackMemberState {
    public final MinecartMember<?> member;
    public final TrackConnection connection;
    public final Vector posA, posB;
    public final double theta;

    public TrackMemberState(MinecartMember<?> member, TrackConnection connection, double theta) {
        this.member = member;
        this.connection = connection;
        this.theta = theta;
        this.posA = connection.getEndA().getPosition();
        this.posB = connection.getEndB().getPosition();
    }

    public boolean isOnPath() {
        return theta > 0.0 && theta < 1.0;
    }
}
