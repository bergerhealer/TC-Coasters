package com.bergerkiller.bukkit.coasters.editor.manipulation;

import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles equalizing the spacing between track nodes.
 * This is done by first finding all unique groups of connected nodes (sequences),
 * then adjusting the positions of nodes in each sequence so that they are evenly spaced.
 * The ends of the connected sequences remain fixed in position.
 *
 * @param <N> Dragged Node type
 */
public class DraggedTrackNodeSpacingEqualizer<N extends DraggedTrackNode> {
    /**
     * Remaining dragged nodes to be processed into chains, that are not part
     * of a chain already
     */
    private final Set<N> remainingNodes;
    /**
     * Map of TrackNode to DraggedTrackNode for quick lookup
     */
    private final Map<TrackNode, N> draggedNodesByNode;
    /**
     * Nodes that are junctions and should not be moved
     */
    private final Set<N> junctionNodes;
    /**
     * Resulting chains found
     */
    public final List<NodeChainComputation<N>> chains = new ArrayList<>(2);

    public DraggedTrackNodeSpacingEqualizer(List<N> draggedNodes) {
        remainingNodes = draggedNodes.stream()
                .filter(n -> !n.node.getConnections().isEmpty())
                .collect(Collectors.toCollection(HashSet::new));

        draggedNodesByNode = DraggedTrackNode.listToMap(remainingNodes);

        junctionNodes = draggedNodes.stream()
                .filter(n -> n.node_zd == null && n.node.getConnections().size() >= 3)
                .collect(Collectors.toSet());
    }

    /**
     * Finds all chains of connected nodes from the remaining nodes.
     * Populates the {@link #chains} list with the found chains.
     */
    public void findChains() {
        // First process all the junction nodes
        for (N junctionNode : junctionNodes) {
            for (TrackConnection conn : junctionNode.getConnections()) {
                tryNavigate(junctionNode, conn);
            }
        }
        remainingNodes.removeAll(junctionNodes);

        // Try to find a node with only one connection to another remaining node
        boolean foundSingleConnNode;
        do {
            foundSingleConnNode = false;
            for (N n : remainingNodes) {
                List<TrackConnection> connections = n.getConnections();
                List<TrackConnection> validConnection = connections.stream()
                        .filter(c -> remainingNodes.contains(getOtherNode(n, c)))
                        .collect(Collectors.toList());
                if (validConnection.size() == 1) {
                    foundSingleConnNode = true;
                    tryNavigate(n, validConnection.get(0));
                    remainingNodes.remove(n);
                    break; // Iterator invalidated
                }
            }
        } while (foundSingleConnNode);

        // Process all remaining nodes until empty
        // This will probably only happen for loops, so the start node is arbitrary
        while (!remainingNodes.isEmpty()) {
            N n = remainingNodes.iterator().next();
            List<TrackConnection> connections = n.getConnections();
            if (!connections.isEmpty()) {
                tryNavigate(n, connections.get(0));
            }
            remainingNodes.remove(n);
        }
    }

    public void tryNavigate(N startNode, TrackConnection into) {
        N otherNode = getOtherNode(startNode, into);
        if (!remainingNodes.contains(otherNode) || junctionNodes.contains(otherNode)) {
            return;
        }

        NodeChainComputation<N> comp = new NodeChainComputation<N>();

        // Seed the computation with an initial path
        NodeConnectionPath<N> curr = new NodeConnectionPath<N>(startNode, otherNode, into);
        comp.start = curr;
        comp.numConnections = 1;
        comp.fullDistance = curr.fullDistance;

        // Try to find the next chain. To do anything we need at least 2 connections (3 nodes)
        curr.next = curr.findNext(this);
        if (curr.next == null) {
            return;
        }

        while (true) {
            comp.nodes.add(curr.to);
            remainingNodes.remove(curr.to);
            curr = curr.next;

            // Update computation stats
            comp.numConnections++;
            comp.fullDistance += curr.fullDistance;

            // Abort at junctions (do not consume the junction node)
            if (junctionNodes.contains(curr.to)) {
                break;
            }

            curr.next = curr.findNext(this);
            if (curr.next == null) {
                break;
            }
        }

        chains.add(comp);
    }

    public void equalizeSpacing() {
        for (NodeChainComputation<N> chain : chains) {
            final double stepDistance = chain.fullDistance / (chain.numConnections);
            if (stepDistance <= 0.05) {
                continue; // This would cause absolute chaos and lag
            }

            // Positions a step distance apart
            List<TrackConnection.PointOnPath> points = new ArrayList<>(chain.nodes.size());

            // Walk the path, setting positions along the way
            double stepRemainingDistance = stepDistance;
            NodeConnectionPath<N> path = chain.start;
            double pathTraveled = 0.0;
            while (true) {
                if (stepRemainingDistance > path.fullDistance - pathTraveled) {
                    // Move to next path
                    stepRemainingDistance -= (path.fullDistance - pathTraveled);
                    path = path.next;
                    pathTraveled = 0.0;
                    if (path == null) {
                        break;
                    }
                } else {
                    // Set position on this path
                    pathTraveled += stepRemainingDistance;
                    points.add(path.getPoint(pathTraveled));
                    stepRemainingDistance = stepDistance;
                }
            }

            // Apply positions to nodes
            for (int i = 0; i < chain.nodes.size(); i++) {
                if (i >= points.size()) {
                    break;
                }
                TrackConnection.PointOnPath point = points.get(i);
                N node = chain.nodes.get(i);
                node.setPosition(point.position);
                node.setOrientation(point.orientation.upVector());
                node.dragPosition = point.position.clone();
            }
        }
    }

    private N getOtherNode(N node, TrackConnection connection) {
        if (connection.getNodeA() == node.node) {
            return draggedNodesByNode.get(connection.getNodeB());
        } else if (node.node_zd != null && connection.getNodeA() == node.node_zd) {
            return draggedNodesByNode.get(connection.getNodeB());
        } else {
            return draggedNodesByNode.get(connection.getNodeA());
        }
    }

    /**
     * Represents a single chain of nodes to be equalized in spacing.
     *
     * @param <N> Dragged Node type
     */
    public static class NodeChainComputation<N extends DraggedTrackNode> {
        /** Starting connection path */
        public NodeConnectionPath<N> start;
        /**
         * List of nodes in the same order as the path connections.
         * Does not include the first/last node of the sequence, which is
         * considered pinned in place.
         */
        public final List<N> nodes = new ArrayList<>();
        /**
         * Full distance of all connections in the chain
         */
        public double fullDistance = 0.0;
        /**
         * Number of connections in the chain
         */
        public int numConnections = 0;
    }

    /**
     * Represents a single connection between two nodes. Used to navigate
     * positions along the path when equalizing node spacing.
     * Is a linked list of connected paths.
     *
     * @param <N> Dragged Node type
     */
    private static class NodeConnectionPath<N extends DraggedTrackNode> {
        public final N from;
        public final N to;
        public final TrackConnection connection;
        public final double fullDistance;
        public NodeConnectionPath<N> next;

        public NodeConnectionPath(N from, N to, TrackConnection connection) {
            this.from = from;
            this.to = to;
            this.connection = connection;
            this.fullDistance = connection.getFullDistance();
        }

        public TrackConnection.PointOnPath getPoint(double distance) {
            TrackNode p0 = connection.getNodeA();
            if (p0 == from.node || p0 == from.node_zd) {
                return this.connection.findPointAtDistance(distance);
            } else {
                return this.connection.findPointAtDistance(this.fullDistance - distance);
            }
        }

        public NodeConnectionPath<N> findNext(DraggedTrackNodeSpacingEqualizer<N> equalizer) {
            TrackNode toNode = to.node;
            if (to.node_zd != null && connection.isConnected(to.node)) {
                toNode = to.node_zd;
            }

            for (TrackConnection conn : toNode.getConnections()) {
                TrackNode nextNode = conn.getOtherNode(toNode);

                // Don't go backwards
                if (nextNode == from.node || nextNode == from.node_zd) {
                    continue;
                }

                // Map it
                N nextDraggedNode = equalizer.draggedNodesByNode.get(nextNode);
                if (nextDraggedNode == null || !equalizer.remainingNodes.contains(nextDraggedNode)) {
                    return null;
                }

                return next = new NodeConnectionPath<N>(to, nextDraggedNode, conn);
            }
            return null;
        }
    }
}
