package com.bergerkiller.bukkit.coasters.util;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeReference;
import com.bergerkiller.bukkit.coasters.tracks.TrackWorld;

/**
 * TrackNodeReference that specifies the x/y/z values
 */
public final class TrackNodePositionReference extends Vector implements TrackNodeReference {

    public TrackNodePositionReference(double x, double y, double z) {
        super(x, y, z);
    }

    @Override
    public TrackNode findOnWorld(TrackWorld world) {
        return world.findNodeExact(this);
    }

    @Override
    public Vector getPosition() {
        return this;
    }

    @Override
    public TrackNodeReference dereference() {
        return this;
    }

    @Override
    public TrackNodeReference reference(TrackWorld world) {
        TrackNode node = world.findNodeExact(this);
        return (node == null) ? this : node;
    }
}
