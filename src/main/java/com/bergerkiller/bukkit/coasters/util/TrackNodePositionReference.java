package com.bergerkiller.bukkit.coasters.util;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeReference;
import com.bergerkiller.bukkit.coasters.tracks.TrackWorld;

/**
 * TrackNodeReference that specifies the x/y/z values
 */
public final class TrackNodePositionReference implements TrackNodeReference {
    // Note: MUST use Vector, because otherwise Vector.equals() will break
    // We already avoid using that function, but other people might still use it
    // Do NOT make this class extend Vector in some vain attempt to get performance
    private final Vector position;

    public TrackNodePositionReference(double x, double y, double z) {
        this.position = new Vector(x, y, z);
    }

    public TrackNodePositionReference(Vector position) {
        this.position = position.clone(); // Fuck Bukkit
    }

    @Override
    public TrackNode findOnWorld(TrackWorld world, TrackNode excludedNode) {
        return world.findNodeExact(position, excludedNode);
    }

    @Override
    public Vector getPosition() {
        return position;
    }

    @Override
    public TrackNodeReference dereference() {
        return this;
    }

    @Override
    public TrackNodeReference reference(TrackWorld world) {
        TrackNode node = world.findNodeExact(position);
        return (node == null) ? this : node;
    }
}
