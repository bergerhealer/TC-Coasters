package com.bergerkiller.bukkit.coasters.editor.manipulation;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChangeCollection;
import com.bergerkiller.bukkit.coasters.objects.TrackObject;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
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
 * @param <N> Manipulated Node type
 */
public class ManipulatedTrackNodeSpacingEqualizer<N extends ManipulatedTrackNode> {
    /**
     * Remaining nodes to be processed into chains, that are not part
     * of a chain already
     */
    private final Set<N> remainingNodes;
    /**
     * Map of TrackNode to ManipulatedTrackNode for quick lookup
     */
    private final Map<TrackNode, N> manipulatedNodesByNode;
    /**
     * Nodes that are junctions and should not be moved
     */
    private final Set<N> junctionNodes;
    /**
     * Resulting chains found
     */
    public final List<NodeChainComputation<N>> chains = new ArrayList<>(2);

    public ManipulatedTrackNodeSpacingEqualizer(List<N> manipulatedNodes) {
        remainingNodes = manipulatedNodes.stream()
                .filter(n -> !n.node.getConnections().isEmpty())
                .collect(Collectors.toCollection(HashSet::new));

        manipulatedNodesByNode = ManipulatedTrackNode.listToMap(remainingNodes);

        junctionNodes = manipulatedNodes.stream()
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

        NodeChainComputation<N> comp = new NodeChainComputation<>();

        // Seed the computation with an initial path
        NodeConnectionPath<N> curr = new NodeConnectionPath<N>(startNode, otherNode, into);
        comp.first = startNode;
        comp.start = curr;
        comp.numConnections = 1;
        comp.fullDistance = curr.fullDistance;

        // Try to find the next chain. To do anything we need at least 2 connections (3 nodes)
        curr.next = curr.findNext(this);
        if (curr.next == null) {
            return;
        }

        while (true) {
            comp.middleNodes.add(curr.to);
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

        comp.last = curr.to;

        comp.nodes.add(comp.first);
        comp.nodes.addAll(comp.middleNodes);
        comp.nodes.add(comp.last);

        chains.add(comp);
    }

    public void makeFiner(PlayerEditState state, HistoryChangeCollection history) throws ChangeCancelledException {
        for (NodeChainComputation<N> chain : chains) {
            chain.makeFiner(state, history);
        }
    }

    public void makeCourser(PlayerEditState state, HistoryChangeCollection history) throws ChangeCancelledException {
        for (NodeChainComputation<N> chain : chains) {
            chain.makeCourser(state, history);
        }
    }

    public void equalizeSpacing(PlayerEditState state, HistoryChangeCollection history) throws ChangeCancelledException {
        for (NodeChainComputation<N> chain : chains) {
            chain.equalizeSpacing(state, history);
        }
    }

    private N getOtherNode(N node, TrackConnection connection) {
        if (connection.getNodeA() == node.node) {
            return manipulatedNodesByNode.get(connection.getNodeB());
        } else if (node.node_zd != null && connection.getNodeA() == node.node_zd) {
            return manipulatedNodesByNode.get(connection.getNodeB());
        } else {
            return manipulatedNodesByNode.get(connection.getNodeA());
        }
    }

    /**
     * Creates a new instance of the equalizer for an already known sequence of connected nodes.
     * This can be used to directly create a single chain computation without needing to find the chains first.
     * Only valid if the manipulated nodes are all connected in a single sequence and do not contain junctions.
     *
     * @param manipulatedNodes Nodes in the sequence, in order from first to last
     * @return Equalizer instance with a single chain computation for the given sequence
     * @param <N> Manipulated Node type
     */
    public static <N extends ManipulatedTrackNode> NodeChainComputation<N> ofSequence(List<N> manipulatedNodes) {
        if (manipulatedNodes.size() < 2) {
            throw new IllegalArgumentException("Sequence must have at least 2 nodes");
        }

        NodeChainComputation<N> comp = new NodeChainComputation<>();
        Iterator<N> iter = manipulatedNodes.iterator();
        comp.first = iter.next();
        comp.middleNodes.addAll(manipulatedNodes.subList(1, manipulatedNodes.size() - 1));

        N prev = comp.first;
        NodeConnectionPath<N> prevPath = null;
        while (iter.hasNext()) {
            N node = iter.next();
            TrackConnection connection = prev.findConnectionWith(node);
            if (connection == null) {
                throw new IllegalArgumentException("Nodes are not connected in a sequence");
            }
            NodeConnectionPath<N> path = new NodeConnectionPath<>(prev, node, connection);

            if (prevPath == null) {
                comp.start = path;
            } else {
                prevPath.next = path;
                path.prev = prevPath;
            }

            comp.fullDistance += path.fullDistance;
            comp.numConnections++;
            prev = node;
            prevPath = path;
        }

        comp.last = (prevPath == null) ? comp.first : prevPath.to;

        comp.nodes.add(comp.first);
        comp.nodes.addAll(comp.middleNodes);
        comp.nodes.add(comp.last);

        return comp;
    }

    /**
     * Represents a single chain of nodes to be equalized in spacing.
     *
     * @param <N> Manipulated Node type
     */
    public static class NodeChainComputation<N extends ManipulatedTrackNode> {
        /** Starting connection path */
        public NodeConnectionPath<N> start;
        /** First node. Pinned in place. */
        public N first;
        /** Last node. Pinned in place. */
        public N last;
        /**
         * List of nodes in the same order as the path connections.
         * Includes the first/last node of the sequence.
         */
        public final List<N> nodes = new ArrayList<>();
        /**
         * List of nodes in the same order as the path connections.
         * Does not include the first/last node of the sequence, which is
         * considered pinned in place.
         */
        public final List<N> middleNodes = new ArrayList<>();
        /**
         * Full distance of all connections in the chain
         */
        public double fullDistance = 0.0;
        /**
         * Number of connections in the chain
         */
        public int numConnections = 0;

        public void equalizeSpacing(PlayerEditState state, HistoryChangeCollection history) throws ChangeCancelledException {
            final double stepDistance = fullDistance / (numConnections);
            if (stepDistance <= 0.05) {
                return; // This would cause absolute chaos and lag
            }

            // Positions a step distance apart
            List<TrackConnection.Point> points = new ArrayList<>(middleNodes.size());

            // Walk the path, setting positions along the way
            double stepRemainingDistance = stepDistance;
            NodeConnectionPath<N> path = start;
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

            // Apply positions to the middle nodes
            applyMiddleNodePositions(state, history, points);
        }

        /**
         * Applies the given positions to the middle nodes in order. The first position is applied to the first middle node, etc.
         * If there are more middle nodes than positions, the remaining middle nodes are not modified.<br>
         * <br>
         * Also preserves the positions of the track objects by tracking the distance offset of those track objects relative
         * to the first node of this chain, and applying that same offset to the new positions.
         *
         * @param state Player edit state
         * @param history History change collection to record finalized manipulation changes into
         * @param points New middle node points. Must be in the same order as the middle nodes.
         * @throws ChangeCancelledException If the change is cancelled (moving track objects)
         */
        public void applyMiddleNodePositions(PlayerEditState state, HistoryChangeCollection history, List<? extends TrackConnection.Point> points) throws ChangeCancelledException {
            // Save the current position of all track objects on the edited nodes
            // Remove all track objects from the connections between the manipulated nodes
            double totalDistance = 0.0;
            List<MovedTrackObject> movedObjects = new ArrayList<>();
            for (NodeConnectionPath<N> path = start; path != null; path = path.next) {
                TrackConnection conn = path.connection;
                if (conn.hasObjects()) {
                    boolean reverse = path.isConnectionReversed();
                    for (TrackObject object : conn.getObjects()) {
                        double fullDistance = totalDistance + (reverse ? (conn.getFullDistance() - object.getDistance()) : object.getDistance());
                        movedObjects.add(new MovedTrackObject(conn, object, reverse, fullDistance));
                    }
                }
                totalDistance += conn.getFullDistance();
            }

            // Sort by distance from start. Needed for applying later.
            movedObjects.sort(Comparator.comparingDouble(a -> a.fullDistance));

            // Move all the nodes to their new positions
            // Fire events and store this change in history, so that it can be undone and redone properly
            for (int i = 0; i < middleNodes.size(); i++) {
                if (i >= points.size()) {
                    break;
                }
                TrackConnection.Point point = points.get(i);
                N node = middleNodes.get(i);

                // Only fire event for the main node, since we know the zero-distance neighbour occupies the same spot
                history.handleChangeBefore(state.getPlayer(), node.node);
                TrackNodeState startState = node.node.getState();
                node.setPosition(point.position);
                node.setOrientation(point.orientation.upVector());
                node.dragPosition = point.position.clone();
                try {
                    history.addChangeAfterChangingNode(state.getPlayer(), node.node, startState);
                } catch (ChangeCancelledException ex) {
                    // Restore zero-distance neighbour position and orientation too
                    if (node.node_zd != null) {
                        node.node_zd.setPosition(startState.position);
                        node.node_zd.setOrientation(startState.orientation);
                    }
                    throw ex;
                }
            }

            // Recompute the connection shapes
            first.node.getWorld().getTracks().updateAll();
            fullDistance = 0.0;
            for (NodeConnectionPath<N> path = start; path != null; path = path.next) {
                path.fullDistance = path.connection.getFullDistance();
                fullDistance += path.fullDistance;
            }

            // Re-apply all the previously saved track objects, with event handling (can be cancelled)
            NodeConnectionPath<N> path = start;
            double distanceTraveled = 0.0;
            for (MovedTrackObject movedObject : movedObjects) {
                // If cancelled or not on the chain, don't operate on this object
                if (movedObject.fullDistance < distanceTraveled) {
                    continue;
                }

                // Until the distance is on a connection, iterate the paths
                while (path != null && movedObject.fullDistance > (distanceTraveled + path.fullDistance)) {
                    distanceTraveled += path.fullDistance;
                    path = path.next;
                }
                if (path == null) {
                    break;
                }

                // Compute distance on the connection the object is at
                double distance = movedObject.fullDistance - distanceTraveled;
                if (path.isConnectionReversed()) {
                    distance = path.fullDistance - distance;
                }

                // Save a snapshot
                TrackObject oldObject = movedObject.object.clone();

                // Remove and re-add object versus shifting position
                if (path.connection == movedObject.connection) {
                    // Same connection, just update position
                    movedObject.object.setDistanceFlipped(path.connection, distance, movedObject.object.isFlipped());
                } else {
                    // Different connection, move the object to the new connection
                    movedObject.connection.removeObject(movedObject.object);

                    // Flipped state could change if the connection reversed state is different between the two connections
                    boolean flipped = movedObject.object.isFlipped();
                    if (path.isConnectionReversed() != movedObject.reverse) {
                        flipped = !flipped;
                    }

                    // Adjust and re-add to the new connection
                    movedObject.object.setDistanceFlippedSilently(distance, flipped);
                    path.connection.addObject(movedObject.object);
                }

                // Fire event to confirm the track object being moved
                // If this throws, then all subsequence track objects aren't moved either and stay on their old connection
                history.addChangeAfterMovingTrackObject(state.getPlayer(), path.connection, movedObject.object, movedObject.connection, oldObject);
            }
        }

        public void makeFiner(PlayerEditState state, HistoryChangeCollection history) throws ChangeCancelledException {
            // Abort if node-node distance becomes too short
            if ((this.fullDistance / (this.numConnections + 1)) <= NodeManipulator.MINIMUM_CONNECTION_DISTANCE) {
                return;
            }

            // Find the connection with the largest distance between two nodes, and insert a new node there
            NodeConnectionPath<N> longestPath = null;
            for (NodeConnectionPath<N> path = start; path != null; path = path.next) {
                if (longestPath == null || path.fullDistance > longestPath.fullDistance) {
                    longestPath = path;
                }
            }
            if (longestPath == null) {
                return;
            }

            // Break the previous connection in half by inserting a new node in the middle (selected)
            // At this point this data structure becomes invalid and cannot be used again
            TrackNode node = state.splitConnection(longestPath.connection, history);
            state.setEditing(node, true);
        }

        public void makeCourser(PlayerEditState state, HistoryChangeCollection history) throws ChangeCancelledException {
            // Need at least one middle node to remove
            if (this.middleNodes.isEmpty()) {
                return;
            }

            // Find the connection with the smallest distance between two nodes, and remove one of its nodes
            TrackNode nodeWithShortestPaths = null;
            double shortestTotalDistance = Double.MAX_VALUE;
            for (NodeConnectionPath<N> path = start; path != null; path = path.next) {
                NodeConnectionPath next = path.next;
                if (next == null) {
                    break;
                }

                double nodeDistanceTotal = path.fullDistance + next.fullDistance;
                if (nodeWithShortestPaths == null || nodeDistanceTotal < shortestTotalDistance) {
                    nodeWithShortestPaths = path.to.node;
                    shortestTotalDistance = nodeDistanceTotal;
                }
            }
            if (nodeWithShortestPaths == null) {
                return;
            }

            state.mergeRemoveNode(nodeWithShortestPaths, history);
        }

        /**
         * Finds the manipulated node corresponding to the given track node, if it is part of this chain. Returns null if not found.
         *
         * @param node TrackNode
         * @return Manipulated node corresponding to the given track node, or null if not found
         */
        public N findNode(TrackNode node) {
            for (N mNode : nodes) {
                if (mNode.node == node || mNode.node_zd == node) {
                    return mNode;
                }
            }
            return null;
        }
    }

    /**
     * Represents a single connection between two nodes. Used to navigate
     * positions along the path when equalizing node spacing.
     * Is a linked list of connected paths.
     *
     * @param <N> Manipulated Node type
     */
    public static class NodeConnectionPath<N extends ManipulatedTrackNode> {
        public final N from;
        public final N to;
        public final TrackConnection connection;
        public double fullDistance;
        public NodeConnectionPath<N> prev;
        public NodeConnectionPath<N> next;

        public NodeConnectionPath(N from, N to, TrackConnection connection) {
            this.from = from;
            this.to = to;
            this.connection = connection;
            this.fullDistance = connection.getFullDistance();
        }

        public boolean isConnectionReversed() {
            return to.isNode(connection.getNodeA());
        }

        public TrackConnection.PointOnPath getPoint(double distance) {
            TrackNode p0 = connection.getNodeA();
            if (p0 == from.node || p0 == from.node_zd) {
                return this.connection.findPointAtDistance(distance);
            } else {
                return this.connection.findPointAtDistance(this.fullDistance - distance);
            }
        }

        public NodeConnectionPath<N> findNext(ManipulatedTrackNodeSpacingEqualizer<N> equalizer) {
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
                N nextManipulatedNode = equalizer.manipulatedNodesByNode.get(nextNode);
                if (nextManipulatedNode == null || !equalizer.remainingNodes.contains(nextManipulatedNode)) {
                    return null;
                }

                next = new NodeConnectionPath<N>(to, nextManipulatedNode, conn);
                next.prev = this;

                return next;
            }
            return null;
        }
    }

    /**
     * Helper container class for storing track objects together with the connection they are part of.
     * Can be used with, for example, the history tracker.
     * Also stores a full distance value relative to the first node of a sequence of nodes.
     */
    public static final class MovedTrackObject {
        /** Original connection the track object was on */
        public final TrackConnection connection;
        /** Original track object that was on the connection, to be moved */
        public final TrackObject object;
        /** Whether the connection is reversed in order */
        public final boolean reverse;
        /** Full distance of the track object relative to the first node of the sequence, after moving */
        public final double fullDistance;

        public MovedTrackObject(TrackConnection connection, TrackObject object, boolean reverse, double fullDistance) {
            this.connection = connection;
            this.object = object;
            this.reverse = reverse;
            this.fullDistance = fullDistance;
        }
    }
}
