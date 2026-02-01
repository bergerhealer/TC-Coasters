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
            return new NodeDragManipulatorCircleFitConnected(state, draggedNodes, sequenceFinder.first.curr, sequenceFinder.last.curr);
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

    private static class NodeSequenceFinder {
        private final List<DraggedTrackNode> draggedNodes;
        private final Map<TrackNode, DraggedTrackNode> draggedNodesByNode;
        private final Set<DraggedTrackNode> remaining;
        public ChainLink first, last;

        public NodeSequenceFinder(List<DraggedTrackNode> draggedNodes) {
            this.draggedNodes = draggedNodes;
            this.draggedNodesByNode = DraggedTrackNode.listToMap(draggedNodes);
            this.remaining = new HashSet<>(draggedNodes);
        }

        private static class ChainLink {
            public final DraggedTrackNode curr;
            public final DraggedTrackNode prev;

            public ChainLink(DraggedTrackNode curr, DraggedTrackNode prev) {
                this.curr = curr;
                this.prev = prev;
            }
        }

        public boolean findSequence() {
            // Find a node to start with that has a connection to two other selected nodes
            // This seeds or first/last values
            // If none are found, abort.
            first = last = null;
            for (DraggedTrackNode editNode : draggedNodes) {
                List<DraggedTrackNode> selectedNeighbours = editNode.getNeighbours().stream()
                        .map(draggedNodesByNode::get)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                if (selectedNeighbours.size() == 2) {
                    first = new ChainLink(selectedNeighbours.get(0), editNode);
                    last = new ChainLink(selectedNeighbours.get(1), editNode);
                    remaining.remove(editNode);
                    remaining.remove(first.curr);
                    remaining.remove(last.curr);
                    break;
                }
            }
            if (first != null && last != null) {
                first = extendLink(first);
                last = extendLink(last);
            }

            // Ends must both be valid and there must not be any remaining nodes
            return first != null && last != null && remaining.isEmpty();
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
                        .filter(n -> n != currLink.prev.node)
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

                link = new ChainLink(selectedNeighbours.get(0), link.curr);
            }
            return link;
        }
    }
}
