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
 * A single track node being dragged. If a node is straightened, includes an additional
 * zero-distance neighbour node which should receive the same position/orientation updates.
 */
public class DraggedTrackNode {
    /** The track node being dragged */
    public final TrackNode node;
    /** The zero-distance neighbour node, if any */
    public final TrackNode node_zd;
    /** Tracks the initial state before dragging begun */
    public final TrackNodeState startState;
    /** Tracks the position while dragging */
    public Vector dragPosition = null;

    public DraggedTrackNode(TrackNode node) {
        this.node = node;
        this.node_zd = node.getZeroDistanceNeighbour();
        this.startState = node.getState();
    }

    protected DraggedTrackNode(DraggedTrackNode copy) {
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
    public TrackConnection findConnectionWith(DraggedTrackNode node) {
        TrackConnection conn = findConnectionWith(node.node);
        if (conn != null) {
            return conn;
        }
        if (node_zd != null) {
            return findConnectionWith(node.node_zd);
        }
        return null;
    }

    /**
     * Creates DraggedTrackNode instances for all provided nodes. De-duplicates nodes that
     * are zero-distance neighbours of each other.
     *
     * @param nodes Track Nodes
     * @return List of DraggedTrackNode instances
     */
    public static List<DraggedTrackNode> listOfNodes(Collection<TrackNode> nodes) {
        if (nodes.isEmpty()) {
            return Collections.emptyList();
        }

        Set<TrackNode> processed = new HashSet<>(nodes.size());
        List<DraggedTrackNode> draggedNodes = new ArrayList<>(nodes.size());
        for (TrackNode node : nodes) {
            DraggedTrackNode dragged = new DraggedTrackNode(node);
            if (!processed.add(dragged.node)) {
                continue;
            }
            if (dragged.node_zd != null && !processed.add(dragged.node_zd)) {
                continue;
            }
            draggedNodes.add(dragged);
        }
        return draggedNodes;
    }

    /**
     * Converts a list of dragged nodes into a map from TrackNode to DraggedTrackNode.
     * Both the main node and zero-distance neighbour node (if any) are mapped.
     *
     * @param draggedNodes List of dragged nodes
     * @param <N> Type of dragged node
     * @return Map from TrackNode to DraggedTrackNode
     */
    public static <N extends DraggedTrackNode> Map<TrackNode, N> listToMap(Collection<N> draggedNodes) {
        Map<TrackNode, N> map = new HashMap<>(draggedNodes.size());
        for (N draggedNode : draggedNodes) {
            map.put(draggedNode.node, draggedNode);
            if (draggedNode.node_zd != null) {
                map.put(draggedNode.node_zd, draggedNode);
            }
        }
        return map;
    }
}
