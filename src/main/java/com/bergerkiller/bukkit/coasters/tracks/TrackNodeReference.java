package com.bergerkiller.bukkit.coasters.tracks;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Matrix4x4;

/**
 * Stores a reference to a TrackNode by its unique x/y/z position coordinates.
 * Also caches the last-obtained track node. Provided the track node was cached,
 * the position is updated when the node is moved.
 */
public class TrackNodeReference {
    private TrackWorld world;
    private TrackNode node;
    private Vector position;
    public static final TrackNodeReference[] EMPTY_ARR = new TrackNodeReference[0];

    public TrackNodeReference(TrackWorld world, Vector position) {
        this.world = world;
        this.node = null;
        this.position = position;
    }

    public TrackNodeReference(TrackNode node) {
        this.world = node.getWorld().getTracks();
        this.node = node;
        this.position = null;
    }

    /**
     * Gets the world where the node can be found
     * 
     * @return world
     */
    public TrackWorld getWorld() {
        return this.world;
    }

    /**
     * Gets the last known or current position of the track node
     * 
     * @return position
     */
    public Vector getPosition() {
        return (node == null) ? this.position : this.node.getPosition();
    }

    /**
     * Gets the node at the position and on the world indicated by this reference.
     * If the node can not be found, null is returned.
     * If found, the node is cached and {@link #getPosition()} will refer to the live
     * position of the node instead.
     * 
     * @return node, null if not found
     */
    public TrackNode getNode() {
        if (this.node != null) {
            if (this.node.isRemoved()) {
                // Node was removed, remember the last position it had and break reference
                this.position = this.node.getPosition();
                this.node = null;
            } else {
                return this.node;
            }
        }

        // Attempt finding the node at the coordinates
        // If found, we don't need to store the position anymore
        this.node = this.world.findNodeExact(this.position);
        if (this.node != null) {
            this.position = null;
        }
        return this.node;
    }

    /**
     * Returns a new TrackNodeReference that references just the position information
     */
    public TrackNodeReference dereference() {
        return new TrackNodeReference(getWorld(), getPosition());
    }

    /**
     * Transforms this reference using a transformation matrix. A new world
     * can also be specified.
     * 
     * @param world The new world for the track node reference
     * @param transform The transformation matrix to transform the node position with
     * @return new track node reference
     */
    public TrackNodeReference transform(TrackWorld world, Matrix4x4 transform) {
        Vector new_position = this.getPosition().clone();
        transform.transformPoint(new_position);
        return new TrackNodeReference(world, new_position);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof TrackNodeReference) {
            TrackNodeReference other = (TrackNodeReference) o;
            if (this.node != null) {
                return this.node == other.getNode();
            } else if (other.node != null) {
                return other.node == other.getNode();
            } else {
                return this.world.getBukkitWorld() == other.world.getBukkitWorld() &&
                       this.position.equals(other.position);
            }
        } else {
            return false;
        }
    }
}
