package com.bergerkiller.bukkit.coasters.tracks;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Matrix4x4;

/**
 * All information stored for connecting one node with another.
 * Connection states can be used in sets, where swapping the two positions
 * has no effect on hashcode or equality.
 */
public final class TrackConnectionState {
    public final Vector node_pos_a;
    public final Vector node_pos_b;

    private TrackConnectionState(Vector node_pos_a, Vector node_pos_b) {
        if (node_pos_a == null) {
            throw new IllegalArgumentException("node_pos_a can not be null");
        }
        if (node_pos_b == null) {
            throw new IllegalArgumentException("node_pos_b can not be null");
        }
        this.node_pos_a = node_pos_a;
        this.node_pos_b = node_pos_b;
    }

    @Override
    public int hashCode() {
        return node_pos_a.hashCode() ^ node_pos_b.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TrackConnectionState)) {
            return false;
        }

        // Note: swapping pos_a and pos_b is still equal!
        TrackConnectionState other = (TrackConnectionState) o;
        if (this.node_pos_a.equals(other.node_pos_a)) {
            return this.node_pos_b.equals(other.node_pos_b);
        } else if (this.node_pos_a.equals(other.node_pos_b)) {
            return this.node_pos_b.equals(other.node_pos_a);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "connection{" + this.node_pos_a + " | " + this.node_pos_b + "}";
    }

    /**
     * Applies a transformation to the position of the two nodes of this connection state
     * 
     * @param transform
     * @return state transformed by the transform
     */
    public TrackConnectionState transform(Matrix4x4 transform) {
        Vector pos_a = this.node_pos_a.clone();
        Vector pos_b = this.node_pos_b.clone();
        transform.transformPoint(pos_a);
        transform.transformPoint(pos_b);
        return new TrackConnectionState(pos_a, pos_b);
    }

    public static TrackConnectionState create(Vector node_pos_a, Vector node_pos_b) {
        return new TrackConnectionState(node_pos_a, node_pos_b);
    }

    public static TrackConnectionState create(TrackConnection connection) {
        return new TrackConnectionState(
                connection.getNodeA().getPosition(),
                connection.getNodeB().getPosition());
    }
}
