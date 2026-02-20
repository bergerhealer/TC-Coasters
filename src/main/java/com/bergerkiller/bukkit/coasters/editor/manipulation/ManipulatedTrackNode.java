package com.bergerkiller.bukkit.coasters.editor.manipulation;

import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeState;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A single track node being manipulated. If a node is straightened, includes an additional
 * zero-distance neighbour node which should receive the same position/orientation updates.
 */
public class ManipulatedTrackNode {
    /** The track node being manipulated */
    public final TrackNode node;
    /** The zero-distance neighbour node, if any */
    public final TrackNode node_zd;
    /** Tracks the initial state before manipulation begun */
    public final TrackNodeState startState;
    /** Tracks the position while dragging */
    public Vector dragPosition = null;

    public ManipulatedTrackNode(TrackNode node) {
        this.node = node;
        this.node_zd = node.getZeroDistanceNeighbour();
        this.startState = node.getState();
    }

    protected ManipulatedTrackNode(ManipulatedTrackNode copy) {
        this.node = copy.node;
        this.node_zd = copy.node_zd;
        this.startState = copy.startState;
        this.dragPosition = copy.dragPosition;
    }

    public void setPosition(Vector position) {
        node.setPosition(position);
        if (node_zd != null) {
            node_zd.setPosition(position);
        }
    }

    public void setOrientation(Vector up) {
        node.setOrientation(up);
        if (node_zd != null) {
            node_zd.setOrientation(up);
        }
    }

    public boolean isNode(TrackNode node) {
        return this.node == node || this.node_zd == node;
    }

    /**
     * Gets the neighbouring nodes of this dragged node. Omits the zero-distance neighbour,
     * but does include connections to that zero-distance neighbour.
     *
     * @return Neighbouring nodes
     */
    public List<TrackNode> getNeighbours() {
        if (node_zd == null) {
            return node.getNeighbours();
        }

        List<TrackNode> neighbours = new ArrayList<>(2 /* Presume connected to two other nodes */);
        for (TrackConnection conn : node.getConnections()) {
            TrackNode other = conn.getOtherNode(node);
            if (other != node_zd) {
                neighbours.add(other);
            }
        }
        for (TrackConnection conn : node_zd.getConnections()) {
            TrackNode other = conn.getOtherNode(node_zd);
            if (other != node) {
                neighbours.add(other);
            }
        }
        return neighbours;
    }

    /**
     * Gets all connections of this dragged node. Includes connections from the zero-distance
     * neighbour, if any, except for the connection between the node and the zero-distance one.
     *
     * @return Connections
     */
    public List<TrackConnection> getConnections() {
        if (node_zd == null) {
            return node.getConnections();
        } else {
            List<TrackConnection> connections = new ArrayList<>(node.getConnections());
            for (TrackConnection conn : node_zd.getConnections()) {
                if (conn.getOtherNode(node_zd) == node) {
                    continue;
                }
                connections.add(conn);
            }
            return connections;
        }
    }

    /**
     * Looks for a connection between this dragged node and the given other node.
     * Checks connections from both the main node and zero-distance neighbour, if any.
     *
     * @param other Other TrackNode
     * @return Connection to the other node, or null if not found
     */
    public TrackConnection findConnectionWith(TrackNode other) {
        for (TrackConnection conn : node.getConnections()) {
            if (conn.getOtherNode(node) == other) {
                return conn;
            }
        }
        if (node_zd != null) {
            for (TrackConnection conn : node_zd.getConnections()) {
                if (conn.getOtherNode(node_zd) == other) {
                    return conn;
                }
            }
        }
        return null;
    }

    /**
     * Looks for a connection between this dragged node and the given other node.
     * Checks connections from both the main node and zero-distance neighbour, if any.
     *
     * @param node Other dragged node to find a connection with
     * @return Connection to the other node, or null if not found
     */
    public TrackConnection findConnectionWith(ManipulatedTrackNode node) {
        TrackConnection conn = findConnectionWith(node.node);
        if (conn != null) {
            return conn;
        }
        if (node_zd != null) {
            return findConnectionWith(node.node_zd);
        }
        return null;
    }

    public Vector getDirectionTo(ManipulatedTrackNode other) {
        // Figure out which of the nodes are connected
        TrackConnection conn = findConnectionWith(other);
        if (conn == null) {
            return node.getDirection();
        } else if (node == conn.getNodeA() || node_zd == conn.getNodeA()) {
            return conn.getNodeA().getDirectionTo(conn.getNodeB());
        } else {
            return conn.getNodeA().getDirectionFrom(conn.getNodeB());
        }
    }

    public Vector getDirectionFrom(ManipulatedTrackNode other) {
        // Figure out which of the nodes are connected
        TrackConnection conn = findConnectionWith(other);
        if (conn == null) {
            return node.getDirection();
        } else if (node == conn.getNodeA() || node_zd == conn.getNodeA()) {
            return conn.getNodeA().getDirectionFrom(conn.getNodeB());
        } else {
            return conn.getNodeA().getDirectionTo(conn.getNodeB());
        }
    }

    /**
     * Creates ManipulatedTrackNode instances for all provided nodes. De-duplicates nodes that
     * are zero-distance neighbours of each other.
     *
     * @param nodes Track Nodes
     * @return List of ManipulatedTrackNode instances
     */
    public static List<ManipulatedTrackNode> listOfNodes(Collection<TrackNode> nodes) {
        if (nodes.isEmpty()) {
            return Collections.emptyList();
        }

        Set<TrackNode> processed = new HashSet<>(nodes.size());
        List<ManipulatedTrackNode> manipulatedNodes = new ArrayList<>(nodes.size());
        for (TrackNode node : nodes) {
            ManipulatedTrackNode manipulated = new ManipulatedTrackNode(node);
            if (!processed.add(manipulated.node)) {
                continue;
            }
            if (manipulated.node_zd != null && !processed.add(manipulated.node_zd)) {
                continue;
            }
            manipulatedNodes.add(manipulated);
        }
        return manipulatedNodes;
    }

    /**
     * Converts a list of manipulated nodes into a map from TrackNode to ManipulatedTrackNode.
     * Both the main node and zero-distance neighbour node (if any) are mapped.
     *
     * @param manipulatedNodes List of manipulated nodes
     * @param <N> Type of manipulated node
     * @return Map from TrackNode to ManipulatedTrackNode
     */
    public static <N extends ManipulatedTrackNode> Map<TrackNode, N> listToMap(Collection<N> manipulatedNodes) {
        Map<TrackNode, N> map = new HashMap<>(manipulatedNodes.size());
        for (N manipulatedNode : manipulatedNodes) {
            map.put(manipulatedNode.node, manipulatedNode);
            if (manipulatedNode.node_zd != null) {
                map.put(manipulatedNode.node_zd, manipulatedNode);
            }
        }
        return map;
    }
}
