package com.bergerkiller.bukkit.coasters.editor.manipulation.modes.circle;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.manipulation.DraggedTrackNode;
import com.bergerkiller.bukkit.coasters.editor.manipulation.NodeDragManipulatorBase;
import com.bergerkiller.bukkit.coasters.editor.manipulation.modes.NodeDragManipulatorPosition;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Node drag manipulator that computes a best-fit circle for the edited nodes on start.
 * The Taubin-style algebraic fit is used on the 2D-projected points.
 *
 * @param <N> Dragged Node type
 */
public abstract class NodeDragManipulatorCircleFit<N extends DraggedTrackNode> extends NodeDragManipulatorBase<N> {
    public static final Initializer INITIALIZER = (state, draggedNodes, event) -> {
        // If there's not enough nodes to form a circle, just return a position manipulator instead
        if (draggedNodes.size() <= 2) {
            return new NodeDragManipulatorPosition(state, draggedNodes);
        }

        // Attempt to detect a full sequence of nodes, with a start and end node, without any loops
        // If found, return a NodeDragManipulatorCircleFitConnected instance
        NodeSequenceFinder sequenceFinder = new NodeSequenceFinder(draggedNodes);
        if (sequenceFinder.findSequence()) {
            return new NodeDragManipulatorCircleFitConnected(state, sequenceFinder.nodes);
        }

        // Fallback implementation that just scales the circle and moves it around
        return new NodeDragManipulatorCircleFitFallback(state, draggedNodes);
    };

    public NodeDragManipulatorCircleFit(PlayerEditState state, List<DraggedTrackNode> draggedNodes, Function<DraggedTrackNode, N> converter) {
        super(state, draggedNodes, converter);
    }

    public NodeDragManipulatorCircleFit(PlayerEditState state, List<N> draggedNodes) {
        super(state, draggedNodes);
    }

    /**
     * Normalizes an angle difference abs(a-b) to the range (-PI, PI]
     *
     * @param a Angle (radians)
     * @param b Angle (radians)
     * @return Normalize angle (radians)
     */
    protected static double getNormalizedAngleDifference(double a, double b) {
        double diff = a - b;
        while (diff <= -Math.PI) diff += 2.0 * Math.PI;
        while (diff >   Math.PI) diff -= 2.0 * Math.PI;
        return Math.abs(diff);
    }

    private static class NodeSequenceFinder {
        private final List<DraggedTrackNode> inputDraggedNodes;
        private final Map<TrackNode, DraggedTrackNode> draggedNodesByNode;
        private final Set<DraggedTrackNode> remaining;
        public ChainLink first, last;
        /** Input nodes in order of the found sequence */
        public final List<DraggedTrackNode> nodes;

        public NodeSequenceFinder(List<DraggedTrackNode> draggedNodes) {
            this.inputDraggedNodes = draggedNodes;
            this.draggedNodesByNode = DraggedTrackNode.listToMap(draggedNodes);
            this.remaining = new HashSet<>(draggedNodes);
            this.nodes = new ArrayList<>(draggedNodes.size());
        }

        private static class ChainLink {
            public final DraggedTrackNode curr;
            public final ChainLink prev;
            public final DraggedTrackNode prevNode;

            public ChainLink(DraggedTrackNode curr, ChainLink prev) {
                this(curr, prev, prev.curr);
            }

            public ChainLink(DraggedTrackNode curr, ChainLink prev, DraggedTrackNode prevNode) {
                this.curr = curr;
                this.prev = prev;
                this.prevNode = prevNode;
            }
        }

        public boolean findSequence() {
            // Find a node to start with that has a connection to two other selected nodes
            // This seeds our first/last values
            // If none are found, abort.
            first = last = null;
            for (DraggedTrackNode editNode : inputDraggedNodes) {
                List<DraggedTrackNode> selectedNeighbours = editNode.getNeighbours().stream()
                        .map(draggedNodesByNode::get)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                if (selectedNeighbours.size() == 2) {
                    first = new ChainLink(selectedNeighbours.get(0), null, editNode);
                    last = new ChainLink(selectedNeighbours.get(1), null, editNode);
                    remaining.remove(editNode);
                    remaining.remove(first.curr);
                    remaining.remove(last.curr);
                    nodes.add(editNode);
                    break;
                }
            }
            if (first != null && last != null) {
                first = extendLink(first);
                last = extendLink(last);
            }

            // Ends must both be valid and there must not be any remaining nodes
            if (first == null || last == null || !remaining.isEmpty()) {
                return false;
            }

            // Collect middle nodes between first and the start node
            for (ChainLink link = first; link != null; link = link.prev) {
                nodes.add(nodes.size() - 1, link.curr);
            }

            // Collect middle nodes between the start node and last
            int addIndex = nodes.size();
            for (ChainLink link = last; link != null; link = link.prev) {
                nodes.add(addIndex, link.curr);
            }

            return true;
        }

        /**
         * Walks the full chain until the end (or a loop/error condition) is reached. Returns the maximally navigated
         * chain link end, or null if failure occurred.
         *
         * @param initialLink Current link
         * @return Link at the end of the chain, or null if failure occurred
         */
        private ChainLink extendLink(ChainLink initialLink) {
            ChainLink link = initialLink;
            while (true) {
                final ChainLink currLink = link;
                List<DraggedTrackNode> selectedNeighbours = currLink.curr.getNeighbours().stream()
                        .filter(n -> n != currLink.prevNode.node && n != currLink.prevNode.node_zd)
                        .map(draggedNodesByNode::get)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                // Fail if connected in a complex way (junction?)
                if (selectedNeighbours.size() >= 2) {
                    return null;
                }

                // Complete if there are no neighbours
                if (selectedNeighbours.isEmpty()) {
                    break;
                }

                // Next node in the chain but not already have been consumed (circular reference)
                if (!remaining.remove(selectedNeighbours.get(0))) {
                    return null;
                }

                link = new ChainLink(selectedNeighbours.get(0), link);
            }
            return link;
        }
    }
}
