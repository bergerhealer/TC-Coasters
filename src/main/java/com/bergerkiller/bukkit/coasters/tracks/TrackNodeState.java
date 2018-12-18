package com.bergerkiller.bukkit.coasters.tracks;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.LogicUtil;

/**
 * All the information stored for a single Track Node.
 * Node states can be used in sets and will check that all properties are equal.
 */
public final class TrackNodeState {
    public final Vector position;
    public final Vector orientation;
    public final IntVector3 railBlock;

    private TrackNodeState(Vector position, Vector orientation, IntVector3 railBlock) {
        if (position == null) {
            throw new IllegalArgumentException("position vector is null");
        }
        if (orientation == null) {
            throw new IllegalArgumentException("orientation vector is null");
        }
        this.position = position;
        this.orientation = orientation;
        this.railBlock = railBlock;
    }

    @Override
    public int hashCode() {
        int hashcode = this.position.hashCode();
        hashcode = 31 * hashcode + this.orientation.hashCode();
        if (this.railBlock != null) {
            hashcode = 31 * hashcode + this.railBlock.hashCode();
        }
        return hashcode;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TrackNodeState)) {
            return false;
        }
        TrackNodeState other = (TrackNodeState) o;
        return this.position.equals(other.position) &&
               this.orientation.equals(other.orientation) &&
               LogicUtil.bothNullOrEqual(this.railBlock, other.railBlock);
    }

    @Override
    public String toString() {
        if (this.railBlock == null) {
            return "node{position=" + this.position +
                    ", orientation=" + this.orientation + "}";
        } else {
            return "node{position=" + this.position +
                    ", orientation=" + this.orientation +
                    ", railBlock=" + this.railBlock + "}";
        }
    }

    /**
     * Applies a transformation to the position, orientation and rail block of this state
     * 
     * @param transform
     * @return state transformed by the transform
     */
    public TrackNodeState transform(Matrix4x4 transform) {
        Vector position = this.position.clone();
        Vector orientation = this.orientation.clone().add(position);
        IntVector3 railBlock = this.railBlock;
        transform.transformPoint(position);
        transform.transformPoint(orientation);
        orientation.subtract(position);
        if (railBlock != null) {
            Vector tmp = new Vector(0.5 + railBlock.x, 0.5 + railBlock.y, 0.5 + railBlock.z);
            transform.transformPoint(tmp);
            railBlock = new IntVector3(tmp.getBlockX(), tmp.getBlockY(), tmp.getBlockZ());
        }
        return new TrackNodeState(position, orientation, railBlock);
    }

    public TrackNodeState changeRail(IntVector3 new_rail) {
        return new TrackNodeState(this.position, this.orientation, new_rail);
    }

    public static TrackNodeState create(Vector position, Vector orientation, IntVector3 railBlock) {
        return new TrackNodeState(position, orientation, railBlock);
    }

    public static TrackNodeState create(TrackNode node) {
        return new TrackNodeState(node.getPosition(), node.getOrientation(), node.getRailBlock(false));
    }
}
