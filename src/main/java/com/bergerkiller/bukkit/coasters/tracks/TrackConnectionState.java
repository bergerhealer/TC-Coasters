package com.bergerkiller.bukkit.coasters.tracks;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.objects.TrackObject;
import com.bergerkiller.bukkit.coasters.objects.TrackObjectHolder;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.LogicUtil;

/**
 * All information stored for connecting one node with another.
 */
public final class TrackConnectionState implements TrackObjectHolder {
    public static final TrackConnectionState[] EMPTY = new TrackConnectionState[0];
    public final TrackNodeReference node_a;
    public final TrackNodeReference node_b;
    private final TrackObject[] objects;

    private TrackConnectionState(TrackNodeReference node_a, TrackNodeReference node_b, TrackObject[] objects) {
        if (node_a == null) {
            throw new IllegalArgumentException("node_a can not be null");
        }
        if (node_b == null) {
            throw new IllegalArgumentException("node_b can not be null");
        }
        this.node_a = node_a;
        this.node_b = node_b;
        this.objects = objects;
    }

    @Override
    public int hashCode() {
        return node_a.hashCode() ^ node_b.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TrackConnectionState)) {
            return false;
        }

        TrackConnectionState other = (TrackConnectionState) o;

        // Make sure objects are the same
        int numObjects = this.objects.length;
        if (numObjects != other.objects.length) {
            return false;
        } else {
            for (int i = 0; i < numObjects; i++) {
                if (!(this.objects[i].equals(other.objects[i]))) {
                    return false;
                }
            }
        }

        // Check nodes
        return this.node_a.isReference(other.node_a) && this.node_b.isReference(other.node_b);
    }

    @Override
    public String toString() {
        return "connection{" + this.node_a.getPosition() + " | " + this.node_b.getPosition() + "}";
    }

    /**
     * Gets whether the node reference specified references a node used in this connection
     * 
     * @param nodeReference
     * @return True if connected
     */
    public boolean isConnected(TrackNodeReference nodeReference) {
        if (this.node_a == nodeReference || this.node_b == nodeReference) {
            return true;
        }
        Vector position = nodeReference.getPosition();
        return position.equals(this.node_a.getPosition()) || position.equals(this.node_b.getPosition());
    }

    /**
     * Gets whether the nodes A and B of this reference are flipped compared to a connection,
     * that is to say, A equals B and B equals A.
     * 
     * @param connection
     * @return True if the connection is the same, but flipped
     */
    public boolean isSameFlipped(TrackConnection connection) {
        return connection.getNodeA().isReference(this.node_b) && connection.getNodeB().isReference(this.node_a);
    }

    /**
     * Gets whether the nodes A and B of this reference equal the nodes of a connection
     * @param connection
     * @return True if the connection is the same
     */
    public boolean isSame(TrackConnection connection) {
        return connection.getNodeA().isReference(this.node_a) && connection.getNodeB().isReference(this.node_b);
    }

    /**
     * Gets the other node referenced by this connection
     * 
     * @param nodeReference
     * @return other node referenced by this connection, not the same as the one referenced by nodeReference
     */
    public TrackNodeReference getOtherNode(TrackNodeReference nodeReference) {
        return this.node_a.isReference(nodeReference) ? this.node_b : this.node_a;
    }

    /**
     * Attempts to find an actual TrackConnection that matches the nodes of this connection state
     * on a world
     * 
     * @param world The world to look for connections
     * @return found connection, null if not found
     */
    public TrackConnection findOnWorld(TrackWorld world) {
        TrackNode node_a = this.node_a.findOnWorld(world);
        return (node_a == null) ? null : node_a.findConnectionWith(this.node_b.reference(world));
    }

    /**
     * Applies a transformation to the position of the two nodes of this connection state
     * 
     * @param transform
     * @return state transformed by the transform
     */
    public TrackConnectionState transform(Matrix4x4 transform) {
        Vector pos_a = this.node_a.getPosition().clone();
        Vector pos_b = this.node_b.getPosition().clone();
        transform.transformPoint(pos_a);
        transform.transformPoint(pos_b);
        return new TrackConnectionState(
                TrackNodeReference.at(pos_a),
                TrackNodeReference.at(pos_b),
                this.objects);
    }

    /**
     * Dereferences the nodes and track objects of this track connection, so that future changes to it
     * are not reflected back in this object.
     * 
     * @return defererenced track connection state
     */
    public TrackConnectionState dereference() {
        TrackNodeReference node_a = this.node_a.dereference();
        TrackNodeReference node_b = this.node_b.dereference();
        return new TrackConnectionState(node_a, node_b, TrackObject.listToArray(Arrays.asList(this.objects), true));
    }

    /**
     * Clones the track objects of this track connection, so that they can be changed without
     * the returned state also changing them. The nodes are unchanged.
     * 
     * @return track connection state with objects cloned
     */
    public TrackConnectionState cloneObjects() {
        return new TrackConnectionState(this.node_a, this.node_b, TrackObject.listToArray(Arrays.asList(this.objects), true));
    }

    /**
     * References the nodes directly, if they exist on the world, so that future changes to the nodes
     * are reflected back in this object. If the nodes are already referenced directly, then
     * the same connection state object is returned.
     * 
     * @param world The world to look for the nodes, if not referenced
     * @return referenced track connection state
     */
    public TrackConnectionState reference(TrackWorld world) {
        TrackNodeReference node_a = this.node_a.reference(world);
        TrackNodeReference node_b = this.node_b.reference(world);
        if (this.node_a == node_a && this.node_b == node_b) {
            return this;
        } else {
            return new TrackConnectionState(node_a, node_b, this.objects);
        }
    }

    @Override
    public List<TrackObject> getObjects() {
        return LogicUtil.asImmutableList(this.objects);
    }

    @Override
    public boolean hasObjects() {
        return this.objects.length > 0;
    }

    /**
     * Creates a track connection state between two nodes. Future changes to the node positions
     * will be updated inside this state. Track objects are not updated.
     * 
     * @param node_a Reference to node A
     * @param node_b Reference to node B
     * @param objects The objects to add to the connection
     * @return referenced connection state
     */
    public static TrackConnectionState create(TrackNodeReference node_a, TrackNodeReference node_b, List<TrackObject> objects) {
        return new TrackConnectionState(node_a, node_b, TrackObject.listToArray(objects, true));
    }

    /**
     * Creates a track connection state between two nodes based on a TrackConnection. Future changes to the node positions
     * will be updated inside this state. Track objects are not updated.
     * 
     * @param connection The connection to turn into a connection state
     * @return referenced connection state
     */
    public static TrackConnectionState create(TrackConnection connection) {
        return create(connection.getNodeA(), connection.getNodeB(), connection.getObjects());
    }

    /**
     * Creates a dereferenced track connection state between two nodes, unaffected by future changes to the node
     * positions or the objects added to the connection.
     * 
     * @param node_pos_a The position of node A
     * @param node_pos_b The position of node B
     * @param objects The objects to add to the connection
     * @return dereferenced connection state
     */
    public static TrackConnectionState createDereferenced(Vector node_pos_a, Vector node_pos_b, List<TrackObject> objects) {
        return new TrackConnectionState(TrackNodeReference.at(node_pos_a),
                                        TrackNodeReference.at(node_pos_b),
                                        TrackObject.listToArray(objects, true));
    }

    /**
     * Creates a dereferenced track connection state between two nodes, unaffected by future changes to the node
     * positions or the objects added to the connection.
     * 
     * @param connection The connection to save to a connection state
     * @return dereferenced connection state
     */
    public static TrackConnectionState createDereferenced(TrackConnection connection) {
        return createDereferenced(connection.getNodeA().getPosition(),
                                  connection.getNodeB().getPosition(),
                                  connection.getObjects());
    }

    /**
     * Creates a dereferenced track connection state between two nodes, unaffected by future changes to the node
     * positions. Objects on the connection are not stored in this state.
     * 
     * @param connection The connection to save to a connection state
     * @return dereferenced connection state
     */
    public static TrackConnectionState createDereferencedNoObjects(TrackConnection connection) {
        return createDereferenced(connection.getNodeA().getPosition(),
                                  connection.getNodeB().getPosition(),
                                  Collections.emptyList());
    }
}
