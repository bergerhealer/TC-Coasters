package com.bergerkiller.bukkit.coasters.tracks;

import com.bergerkiller.bukkit.coasters.TCCoastersUtil;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.util.TrackNodePositionReference;

/**
 * The details required to unique identify a {@link TrackNode}. For obvious reasons,
 * a TrackNode implements this interface also. What uniquely identifies a TrackNode
 * is the position information, since no two nodes can exist at the same coordinates.
 */
public interface TrackNodeReference {

    /**
     * Attempts to find the node referred to by this TrackNodeReference
     * on the World specified.
     * 
     * @param world The world to find the node on
     * @param excludedNode If multiple track nodes exist at a position, makes sure to
     *                     exclude this node. Ignored if null.
     * @return track node found, null if not found
     */
    TrackNode findOnWorld(TrackWorld world, TrackNode excludedNode);

    /**
     * Gets whether this reference instance is an existing, non-removed Node.
     * In that case no further searching by position is required to find it.
     *
     * @return True if this instance is an existing, non-removed node. In that case
     *         this instance can be cast to {@link TrackNode}
     */
    boolean isExistingNode();

    /**
     * Gets the exact coordinates of this node
     * 
     * @return position
     */
    Vector getPosition();

    /**
     * Dereferences this track node reference, so that the position
     * is no longer live-updated if this node reference is a TrackNode already.
     * This makes sure the reference can be kept offline, without it changing.
     * 
     * @return dereferenced node reference
     */
    TrackNodeReference dereference();

    /**
     * References this track node reference to an actual node, so that the
     * position is live-updated. If the node is not found, this instance is
     * returned for future referencing.
     * 
     * @param world
     * @return referenced node reference
     */
    TrackNodeReference reference(TrackWorld world);

    /**
     * Gets whether this track node reference references the same node as another reference.
     * This compares the node positions.
     * 
     * @param reference
     * @return True if this reference and the one specified reference the same track node
     */
    default boolean isReference(TrackNodeReference reference) {
        return this == reference || TCCoastersUtil.isPositionSame(getPosition(), reference.getPosition());
    }

    /**
     * Creates a track node reference that references a node at the given x/y/z coordinates
     * 
     * @param x The x-coordinate of the node
     * @param y The y-coordinate of the node
     * @param z The z-coordinate of the node
     * @return Track node reference
     */
    public static TrackNodeReference at(double x, double y, double z) {
        return new TrackNodePositionReference(x, y, z);
    }

    /**
     * Creates a track node reference that references a node at the given x/y/z position
     * 
     * @param position The x/y/z coordinates of the node. Input Vector can be modified
     *                 afterwards without breaking things.
     * @return Track node reference
     */
    public static TrackNodeReference at(Vector position) {
        return new TrackNodePositionReference(position);
    }
}
