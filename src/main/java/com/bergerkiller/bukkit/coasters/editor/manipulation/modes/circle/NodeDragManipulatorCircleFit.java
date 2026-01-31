package com.bergerkiller.bukkit.coasters.editor.manipulation.modes.circle;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditNode;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.manipulation.NodeDragManipulatorBase;
import com.bergerkiller.bukkit.coasters.editor.manipulation.modes.NodeDragManipulatorPosition;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.common.math.Quaternion;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Node drag manipulator that computes a best-fit circle for the edited nodes on start.
 * The Taubin-style algebraic fit is used on the 2D-projected points.
 *
 * Note: onUpdate is intentionally left unimplemented for now.
 */
public abstract class NodeDragManipulatorCircleFit extends NodeDragManipulatorBase {
    public static final Initializer INITIALIZER = (state, editedNodes, event) -> {
        // If there's not enough nodes to form a circle, just return a position manipulator instead
        if (editedNodes.size() <= 2) {
            return new NodeDragManipulatorPosition(state, editedNodes);
        }

        // Attempt to detect a full sequence of nodes, with a start and end node, without any loops
        // If found, return a NodeDragManipulatorCircleFitConnected instance
        NodeSequenceFinder sequenceFinder = new NodeSequenceFinder(editedNodes);
        if (sequenceFinder.findSequence()) {
            return new NodeDragManipulatorCircleFitConnected(state, editedNodes, sequenceFinder.first.curr, sequenceFinder.last.curr);
        }

        // Fallback implementation that just scales the circle and moves it around
        return new NodeDragManipulatorCircleFitFallback(state, editedNodes);
    };

    public NodeDragManipulatorCircleFit(PlayerEditState state, Map<TrackNode, PlayerEditNode> editedNodes) {
        super(state, editedNodes);
    }

    private static class NodeSequenceFinder {
        private final Map<TrackNode, PlayerEditNode> editedNodes;
        private final Set<TrackNode> remaining;
        public ChainLink first, last;

        public NodeSequenceFinder(Map<TrackNode, PlayerEditNode> editedNodes) {
            this.editedNodes = editedNodes;
            this.remaining = new HashSet<>(editedNodes.keySet());
        }

        private static class ChainLink {
            public final PlayerEditNode curr;
            public final PlayerEditNode prev;

            public ChainLink(PlayerEditNode curr, PlayerEditNode prev) {
                this.curr = curr;
                this.prev = prev;
            }
        }

        public boolean findSequence() {
            // Find a node to start with that has a connection to two other selected nodes
            // This seeds or first/last values
            // If none are found, abort.
            first = last = null;
            for (PlayerEditNode editNode : editedNodes.values()) {
                List<PlayerEditNode> selectedNeighbours = editNode.node.getNeighbours().stream()
                        .map(editedNodes::get)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                if (selectedNeighbours.size() == 2) {
                    first = new ChainLink(selectedNeighbours.get(0), editNode);
                    last = new ChainLink(selectedNeighbours.get(1), editNode);
                    remaining.remove(editNode.node);
                    remaining.remove(first.curr.node);
                    remaining.remove(last.curr.node);
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
                List<PlayerEditNode> selectedNeighbours = currLink.curr.node.getNeighbours().stream()
                        .filter(n -> n != currLink.prev.node)
                        .map(editedNodes::get)
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
                if (!remaining.remove(selectedNeighbours.get(0).node)) {
                    return null;
                }

                link = new ChainLink(selectedNeighbours.get(0), link.curr);
            }
            return link;
        }
    }

    // Return any vector perpendicular to v (not necessarily normalized)
    protected static Vector perpendicular(Vector v) {
        // pick smallest component to avoid degeneracy
        double ax = Math.abs(v.getX()), ay = Math.abs(v.getY()), az = Math.abs(v.getZ());
        if (ax <= ay && ax <= az) {
            return new Vector(0, -v.getZ(), v.getY());
        } else if (ay <= ax && ay <= az) {
            return new Vector(-v.getZ(), 0, v.getX());
        } else {
            return new Vector(-v.getY(), v.getX(), 0);
        }
    }

    protected static Vector orthogonalize(Vector v, Vector reference) {
        double refLen2 = reference.lengthSquared();
        if (refLen2 < 1e-12) {
            return v.clone(); // can't project onto a near-zero vector
        }
        Vector proj = reference.clone().multiply(v.dot(reference) / refLen2);
        Vector res = v.clone().subtract(proj);
        if (res.lengthSquared() < 1e-12) {
            // fallback to any perpendicular vector (uses existing helper)
            return perpendicular(v);
        }
        return res;
    }
}
