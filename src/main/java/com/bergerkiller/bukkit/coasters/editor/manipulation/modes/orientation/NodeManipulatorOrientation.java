package com.bergerkiller.bukkit.coasters.editor.manipulation.modes.orientation;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChangeCollection;
import com.bergerkiller.bukkit.coasters.editor.manipulation.ManipulatedTrackNode;
import com.bergerkiller.bukkit.coasters.editor.manipulation.ManipulatedTrackNodeSpacingEqualizer;
import com.bergerkiller.bukkit.coasters.editor.manipulation.NodeDragEvent;
import com.bergerkiller.bukkit.coasters.editor.manipulation.NodeManipulatorBase;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.util.PlaneBasis;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Node drag manipulator that alters the orientation of the edited nodes
 * based on where the player is looking.
 */
public class NodeManipulatorOrientation extends NodeManipulatorBase<ManipulatedTrackNodeWithRoll> {
    public static final Initializer INITIALIZER = NodeManipulatorOrientation::new;

    /** Chain of nodes being edited */
    private ManipulatedTrackNodeSpacingEqualizer.NodeChainComputation<ManipulatedTrackNodeWithRoll> activeChain = null;
    /** Plane basis of the nodes in the chain being edited */
    private PlaneBasis activePlaneBasis = null;
    /** Node in the chain that is being dragged */
    private ManipulatedTrackNodeWithRoll draggedNode = null;
    /** Virtual point of the node handle (lever) being dragged */
    private Vector handlePosition;

    public NodeManipulatorOrientation(PlayerEditState state, List<ManipulatedTrackNode> manipulatedNodes) {
        super(state, manipulatedNodes, ManipulatedTrackNodeWithRoll::new);
    }

    @Override
    public void onDragStarted(NodeDragEvent event) {
        this.handlePosition = event.current().toVector();

        // Is used to properly alter the orientation of a node looked at
        TrackNode lookingAt = state.findLookingAt();
        if (lookingAt != null) {
            // Is used to properly alter the orientation of a node looked at
            Vector forward = event.current().getRotation().forwardVector();
            double distanceTo = lookingAt.getPosition().distance(this.handlePosition);
            this.handlePosition.add(forward.multiply(distanceTo));

            ManipulatedTrackNodeSpacingEqualizer<ManipulatedTrackNodeWithRoll> equalizer = new ManipulatedTrackNodeSpacingEqualizer<>(manipulatedNodes);
            equalizer.findChains();
            for (ManipulatedTrackNodeSpacingEqualizer.NodeChainComputation<ManipulatedTrackNodeWithRoll> chain : equalizer.chains) {
                ManipulatedTrackNodeWithRoll n = chain.findNode(lookingAt);
                if (n != null) {
                    this.activeChain = chain;
                    this.draggedNode = n;
                    break;
                }
            }
        }

        // If an active chain is used, compute the direction vectors / roll / etc. for it.
        if (activeChain != null) {
            // Compute plane basis using the node positions of this chain
            // For curves and such, this produces a flat transformation relative to which roll can be computed
            // This ensures roll behavior works also on non-horizontal planes
            {
                List<Vector> positions = activeChain.nodes.stream()
                        .map(n -> n.node.getPosition())
                        .collect(Collectors.toList());

                Vector avgOrientation = activeChain.nodes.stream()
                        .map(n -> n.node.getOrientation())
                        .reduce(new Vector(), Vector::add);

                double length = avgOrientation.length();
                if (length > 1e-5) {
                    avgOrientation.multiply(1.0 / length);
                } else {
                    avgOrientation = new Vector(0, 1, 0);
                }

                activePlaneBasis = PlaneBasis.estimateFromPoints(positions, avgOrientation);
            }

            // Compute direction vector into the direction of the chain (so all node path directions point the same way)
            // This also computes the orientation of the node as if this direction was (0,0,1)
            double distance = 0.0;
            for (ManipulatedTrackNodeSpacingEqualizer.NodeConnectionPath<ManipulatedTrackNodeWithRoll> path = activeChain.start; path != null; path = path.next) {
                path.from.theta = distance / activeChain.fullDistance;
                distance += path.fullDistance;
                path.from.setDirection(activePlaneBasis.toPlane(path.from.getDirectionTo(path.to)));
                if (path.next == null) {
                    activeChain.last.setDirection(activePlaneBasis.toPlane(activeChain.last.getDirectionFrom(path.from)));
                }
            }
            activeChain.last.theta = 1.0;

            for (ManipulatedTrackNodeWithRoll n : activeChain.nodes) {
                n.computeAndSetRoll(activePlaneBasis);
            }
        }
    }

    @Override
    public void onDragUpdate(NodeDragEvent event) {
        if (!event.isStart()) {
            event.change().transformPoint(this.handlePosition);
        }

        if (activeChain != null) {
            // Update orientation/roll of the dragged node only
            draggedNode.setOrientation(this.handlePosition.clone().subtract(draggedNode.node.getPosition()));
            draggedNode.computeAndSetRoll(activePlaneBasis);

            if (draggedNode == activeChain.first || draggedNode == activeChain.last) {
                // Interpolate all the nodes smoothly inbetween based on theta
                for (ManipulatedTrackNodeWithRoll n : activeChain.middleNodes) {
                    n.roll = activeChain.first.roll + n.theta * (activeChain.last.roll - activeChain.first.roll);
                    n.applyRoll(activePlaneBasis);
                }
            } else {
                // Pin first/last node in place, and modify only orientation of the dragged node.
                // If the dragged node is not the first/last node, then the whole chain is rotated around the dragged node.
                // Interpolate between first/dragged & dragged/last
                for (ManipulatedTrackNodeWithRoll n : activeChain.middleNodes) {
                    if (n == draggedNode) {
                        continue;
                    }

                    double t;
                    if (draggedNode.theta < n.theta) {
                        t = (n.theta - draggedNode.theta) / (activeChain.last.theta - draggedNode.theta);
                        n.roll = draggedNode.roll + t * (activeChain.last.roll - draggedNode.roll);
                    } else {
                        t = (n.theta - draggedNode.theta) / (activeChain.first.theta - draggedNode.theta);
                        n.roll = draggedNode.roll + t * (activeChain.first.roll - draggedNode.roll);
                    }
                    n.applyRoll(activePlaneBasis);
                }
            }
        } else {
            // Simple dumb mode
            for (ManipulatedTrackNode manipulatedNode : manipulatedNodes) {
                manipulatedNode.setOrientation(this.handlePosition.clone().subtract(manipulatedNode.node.getPosition()));
            }
        }
    }

    @Override
    public void onDragFinished(HistoryChangeCollection history, NodeDragEvent event) throws ChangeCancelledException {
        recordEditedNodesInHistory(history);
    }
}
