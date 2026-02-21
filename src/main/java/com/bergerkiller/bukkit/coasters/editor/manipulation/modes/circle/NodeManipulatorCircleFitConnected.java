package com.bergerkiller.bukkit.coasters.editor.manipulation.modes.circle;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChangeCollection;
import com.bergerkiller.bukkit.coasters.editor.manipulation.ManipulatedTrackNode;
import com.bergerkiller.bukkit.coasters.editor.manipulation.ManipulatedTrackNodeSpacingEqualizer;
import com.bergerkiller.bukkit.coasters.editor.manipulation.NodeDragEvent;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.util.PlaneBasis;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.math.Vector2;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Node drag manipulator that fits a circle pinned by the first and last nodes
 * of a connected sequence of nodes being edited. This is the most common type
 * of circle fit operation, namely curves and such.
 */
class NodeManipulatorCircleFitConnected extends NodeManipulatorCircleFit<ManipulatedTrackNodeOnCircleArc> {
    /** First node of the sequence of nodes that is selected */
    private final ManipulatedTrackNodeOnCircleArc first;
    /** Last node of the sequence of nodes that is selected */
    private final ManipulatedTrackNodeOnCircleArc last;
    /** Middle nodes with their theta values along the arc from first->last */
    protected final List<ManipulatedTrackNodeOnCircleArc> middleNodes;
    /** Which of the nodes was clicked at the start (is dragged), or null if none (scale mode) */
    private ManipulatedTrackNodeOnCircleArc clickedNode = null;

    /** Parameters at the time dragging was started */
    private PinnedParams startParams = null;
    /** Additional parameters that are used to compute the full circle */
    private PinnedParams pinnedParams = null;

    /**
     * Creates a new node drag manipulator that fits a circle through the first and last node,
     *
     * @param state PlayerEditState
     * @param manipulatedNodes All manipulated nodes, sorted by order in the sequence (important!)
     */
    public NodeManipulatorCircleFitConnected(PlayerEditState state, List<ManipulatedTrackNode> manipulatedNodes) {
        super(state, manipulatedNodes, ManipulatedTrackNodeOnCircleArc::new);

        this.first = this.manipulatedNodes.get(0);
        this.last = this.manipulatedNodes.get(this.manipulatedNodes.size() - 1);
        this.middleNodes = this.manipulatedNodes.stream()
                .filter(n -> n != this.first && n != this.last)
                .collect(Collectors.toCollection(ArrayList::new));

        // Compute pinned parameters for the constrained circle fit
        this.startParams = this.pinnedParams = this.computePinnedParams2D();

        // This calculator handles the angle <> theta <> node position logic
        // It is based on the previously computed pinned parameters
        NodeBiSectorThetaCalculator calculator = createNodeThetaCalculator();

        // Compute initial theta values
        first.setInitialTheta(0.0);
        for (ManipulatedTrackNodeOnCircleArc middleNode : middleNodes) {
            middleNode.setInitialTheta(calculator.computeTheta(middleNode.node.getPosition()));
        }
        last.setInitialTheta(1.0);

        // Determine the tangent orientation of each node currently and compute the up vector relative to that
        for (ManipulatedTrackNodeOnCircleArc manipulatedNode : this.manipulatedNodes) {
            double ang = calculator.computeAngleFromTheta(manipulatedNode.theta);
            Vector up = manipulatedNode.node.getOrientation().clone();
            calculator.computeTangentOrientation(ang).invTransformPoint(up);
            manipulatedNode.up = up;
        }
    }

    @Override
    public void onDragStarted(NodeDragEvent event) {
        NodeBiSectorThetaCalculator calculator = createNodeThetaCalculator();

        // Recompute the list of middle nodes, and sort it by theta value
        // If the list is different, shuffle (re-assign) the theta values accordingly
        List<ThetaSortedNode> middleNodesSortedByTheta = middleNodes.stream()
                .map(ThetaSortedNode::new)
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));

        boolean hasSwappedNodes = false;
        for (int i = 0; i < middleNodes.size(); i++) {
            ManipulatedTrackNodeOnCircleArc node = middleNodes.get(i);
            ThetaSortedNode sortedNode = middleNodesSortedByTheta.get(i);
            if (node != sortedNode.node) {
                hasSwappedNodes = true;
                // Only assign initial theta, not theta, so that theta stays valid
                node.setInitialTheta(sortedNode.theta);

                // This also changes the "up" orientation of this node
                double ang = calculator.computeAngleFromTheta(node.theta);
                Vector up = node.node.getOrientation().clone();
                calculator.computeTangentOrientation(ang).invTransformPoint(up);
                node.up = up;
            }
        }

        TrackNode clickedNodeNode = state.findLookingAt();
        if (clickedNodeNode != null) {
            for (ManipulatedTrackNodeOnCircleArc manipulatedNode : manipulatedNodes) {
                if (manipulatedNode.node == clickedNodeNode) {
                    Vector position = manipulatedNode.node.getPosition().clone();

                    // Correct for swapped theta, as this node is still at the old position
                    if (hasSwappedNodes) {
                        final double theta = calculator.computeTheta(clickedNodeNode.getPosition());
                        manipulatedNode = manipulatedNodes.stream()
                                .min(Comparator.comparingDouble(a -> Math.abs(a.theta - theta)))
                                .orElse(manipulatedNode);
                    }

                    clickedNode = manipulatedNode;
                    clickedNode.dragPosition = position;
                    break;
                }
            }
        }
    }

    @Override
    public void onDragUpdate(NodeDragEvent event) {
        // Transform the first node (Test)
        if (clickedNode == first || clickedNode == last) {
            // Move the first/last node
            handleDrag(clickedNode, event, true).applyTo(clickedNode);

            // Figure out the optimal radius
            // Try to compute a preferred radius based on the preferred tangent direction at the endpoints.
            // Use whichever endpoint provides a finite solution, or pick the one closer to the current radius.
            PlaneBasis basis = buildPlaneBasisFromPins();
            PinnedParams prefFirst = computePreferredPinnedParams(basis, first);
            PinnedParams prefLast = computePreferredPinnedParams(basis, last);
            if (prefFirst != null && prefLast != null) {
                // Find an average of both, unless they differ in minor-major arc logic
                if (prefFirst.minorArc == prefLast.minorArc && prefFirst.side == prefLast.side) {
                    pinnedParams = new PinnedParams(
                            0.5 * (prefFirst.radius + prefLast.radius),
                            pinnedParams.up, // Keep the current up vector, as that is what the nodes are currently using
                            prefFirst.side, // Both candidates have the same side, so just use that
                            prefFirst.minorArc // Both candidates have the same minor-arc logic, so just use that
                    );
                } else {
                    // Prefer the candidate whose normalized radius is closest to the current pinned radius
                    double d1 = Math.abs(prefFirst.radius - pinnedParams.radius);
                    double d2 = Math.abs(prefLast.radius - pinnedParams.radius);
                    pinnedParams = (d1 <= d2) ? prefFirst : prefLast;
                }
            } else if (prefFirst != null) {
                pinnedParams = prefFirst;
            } else if (prefLast != null) {
                pinnedParams = prefLast;
            }

            applyNodesToCircle();
        } else if (clickedNode != null) {
            // Drag the middle node around
            NodeDragPosition dragPos = handleDrag(clickedNode, event, true);

            // Rotate the circle so that the node can be on it
            adjustCircleUp(dragPos.position);

            // Figure out what new radius is needed to place the dragged middle node on the circle
            adjustRadiusForNode(dragPos.position);

            // Find which of the middle nodes is the clicked node
            moveNodeTheta(clickedNode, dragPos.position);

            applyNodesToCircle();
        } else {
            // Move all nodes, same as position mode
            // We do re-apply the middle nodes to take on a circle shape again
            if (event.isStart()) {
                applyNodesToCircle();
            }
            for (ManipulatedTrackNodeOnCircleArc manipulatedNode : manipulatedNodes) {
                handleDrag(manipulatedNode, event, false).applyTo(manipulatedNode);
            }
        }
    }

    @Override
    public void onDragFinished(HistoryChangeCollection history, NodeDragEvent event) throws ChangeCancelledException {
        // Merge/record behavior can be copied from other manipulators when implementing finish behavior.
        // For now, do nothing special.
        recordEditedNodesInHistory(history);
    }

    @Override
    public void equalizeNodeSpacing(HistoryChangeCollection history) throws ChangeCancelledException {
        // Compute equalized theta and the new node positions for the middle nodes
        List<TrackConnection.Point> middleNodePoints = new ArrayList<>(middleNodes.size());
        NodeBiSectorThetaCalculator calc = createNodeThetaCalculator();
        for (int i = 0; i < middleNodes.size(); i++) {
            ManipulatedTrackNodeOnCircleArc middleNode = middleNodes.get(i);
            middleNode.theta = (double) (i + 1) / (middleNodes.size() + 1);

            // Circle angle -> position and orientation on the circle
            double ang = calc.computeAngleFromTheta(middleNode.theta);
            Vector newPos = calc.computePointAtAngle(ang);
            Vector newUpVec = middleNode.up.clone();
            Quaternion tangent = calc.computeTangentOrientation(ang);
            tangent.transformPoint(newUpVec);
            Quaternion newUp = Quaternion.fromLookDirection(tangent.forwardVector(), newUpVec);

            // Save
            middleNodePoints.add(new TrackConnection.Point(newPos, newUp));
        }

        // Move all the nodes and preserve the position of track objects
        ManipulatedTrackNodeSpacingEqualizer.NodeChainComputation<ManipulatedTrackNodeOnCircleArc> chain = ManipulatedTrackNodeSpacingEqualizer.ofSequence(manipulatedNodes);
        chain.applyMiddleNodePositions(state, history, middleNodePoints);
    }

    @Override
    public void makeFiner(HistoryChangeCollection history) throws ChangeCancelledException {
        // Find the connection between nodes that is the longest along the circle, and insert a new node there
        double biggestTheta = -Double.MAX_VALUE;
        TrackConnection longestConnection = null;
        double longestConnectionMiddleTheta = 0.0;
        for (int i = 0; i <= middleNodes.size(); i++) {
            ManipulatedTrackNodeOnCircleArc left = (i > 0) ? middleNodes.get(i - 1) : first;
            ManipulatedTrackNodeOnCircleArc right = (i < middleNodes.size()) ? middleNodes.get(i) : last;
            double totalTheta = Math.abs(right.theta - left.theta);
            if (totalTheta > biggestTheta) {
                TrackConnection conn = left.findConnectionWith(right);
                if (conn != null) {
                    biggestTheta = totalTheta;
                    longestConnection = conn;
                    longestConnectionMiddleTheta = 0.5 * (left.theta + right.theta);
                }
            }
        }
        if (longestConnection == null) {
            throw new ChangeCancelledException(); // No connection we can split
        }

        // Compute the position & orientation at this angle
        NodeBiSectorThetaCalculator calc = createNodeThetaCalculator();
        Vector newPos = calc.computePointAtAngle(calc.computeAngleFromTheta(longestConnectionMiddleTheta));
        Vector newOri = Quaternion.average(Arrays.asList(
                Quaternion.fromLookDirection(longestConnection.getNodeA().getDirection(), longestConnection.getNodeA().getOrientation()),
                Quaternion.fromLookDirection(longestConnection.getNodeB().getDirection(), longestConnection.getNodeB().getOrientation())
        )).upVector();

        state.splitConnection(longestConnection, newPos, newOri, history);
    }

    @Override
    public void makeCourser(HistoryChangeCollection history) throws ChangeCancelledException {
        // Find the node that has the smallest angle that suffers the least from removing a node
        TrackNode bestNode = null;
        double smallestTheta = Double.MAX_VALUE;
        for (int i = 0; i < middleNodes.size(); i++) {
            ManipulatedTrackNodeOnCircleArc node = middleNodes.get(i);
            ManipulatedTrackNodeOnCircleArc left = (i > 0) ? middleNodes.get(i - 1) : first;
            ManipulatedTrackNodeOnCircleArc right = (i < middleNodes.size() - 1) ? middleNodes.get(i + 1) : last;
            double totalTheta = Math.abs(right.theta - left.theta);
            if (bestNode == null || totalTheta < smallestTheta) {
                bestNode = node.node;
                smallestTheta = totalTheta;
            }
        }
        if (bestNode == null) {
            throw new ChangeCancelledException(); // No nodes to remove
        }

        state.mergeRemoveNode(bestNode, history);
    }

    /**
     * Adjusts the circle orientation so that the provided position lies on the circle.
     * All this does is roll it around the bisector.
     *
     * @param posOnCircle Point on the circle
     */
    private void adjustCircleUp(Vector posOnCircle) {
        if (pinnedParams == null) throw new IllegalStateException("Pinned parameters have not been computed");

        PlaneBasis basis = buildPlaneBasisFromPins();

        // Compute the required roll around the bisector direction so that the dragged middle node lies on the circle
        Vector toPoint = posOnCircle.clone().subtract(basis.centroid);

        // Project out the component along the bisector to get the perpendicular direction from the line to the point
        Vector perp = toPoint.clone().subtract(basis.ex.clone().multiply(toPoint.dot(basis.ex)));
        double perpLen = perp.length();
        if (perpLen < 1e-8) {
            return; // Leave orientation unchanged
        }
        perp.multiply(1.0 / perpLen);

        // Compute the up vector as the cross product of bisector and perp
        Vector upVec = basis.ex.getCrossProduct(perp);
        double upVecLen = upVec.length();
        if (upVecLen < 1e-8) {
            return; // Leave orientation unchanged
        }
        upVec.multiply(1.0 / upVecLen);

        // Invert up-vector if needed to stay closer to drag start orientation
        if (upVec.dot(startParams.up.upVector()) < 0.0) {
            upVec.multiply(-1.0);
        }

        // Compute up-vector aligned to the bisector
        Quaternion upQuat = Quaternion.fromLookDirection(basis.ex, upVec);

        // Right side of the up-vector must be identical to perp
        double side = 1.0;
        if (upQuat.rightVector().dot(perp) < 0.0) {
            side = -side;
        }

        this.pinnedParams = new PinnedParams(this.pinnedParams.radius, upQuat, side, this.pinnedParams.minorArc);
    }

    /**
     * Adjusts the circle radius so that the provided position lies on the circle.
     *
     * @param posOnCircle Point on the circle
     */
    private void adjustRadiusForNode(Vector posOnCircle) {
        if (pinnedParams == null) throw new IllegalStateException("Pinned parameters have not been computed");

        // Project into current plane basis
        PlaneBasis basis = buildPlaneBasisFromPins();

        Vector pv = posOnCircle.clone().subtract(basis.centroid);
        double px = pv.dot(basis.ex);
        double py = pv.dot(basis.ey);

        // Invert py if long arc (opposite side of bisector)
        // Normally ey points towards circle center, but we want to know it into the direction of the nodes,
        // as the player is dragging nodes.
        if (!pinnedParams.minorArc) {
            py = -py;
        }

        // half chord length
        double h = 0.5 * first.node.getPosition().distance(last.node.getPosition());

        double cy, R;
        if (h < 1e-12) {
            // Degenerate chord -> radius is distance to point, pick short arc
            cy = 1.0;
            R = Math.hypot(px, py);
        } else if (Math.abs(py) > 1e-8) {
            // Analytical solution: cy = (px^2 + py^2 - h^2) / (2*py)
            cy = (px * px + py * py - h * h) / (2.0 * py);
            R = Math.hypot(cy, h);
        } else {
            double searchRange = Math.max(10.0 * h, 1.0);
            double left = -searchRange;
            double right = searchRange;

            // Ternary search for cy
            for (int iter = 0; iter < 80; iter++) {
                double a = left + (right - left) / 3.0;
                double b = right - (right - left) / 3.0;

                double Ra = Math.sqrt(a * a + h * h);
                double Rb = Math.sqrt(b * b + h * h);

                double da = Math.abs(Math.hypot(px, py - a) - Ra);
                double db = Math.abs(Math.hypot(px, py - b) - Rb);

                if (da > db) left = a;
                else right = b;
            }

            cy = (left + right) * 0.5;
            R = Math.hypot(cy, h);

            // Fallback
            double err = Math.abs(Math.hypot(px, py - cy) - R);
            if (err == Double.POSITIVE_INFINITY) {
                cy = 1.0;
                R = Math.hypot(px, py);
            }
        }

        // Ensure radius at least slightly larger than half chord
        if (h > 1e-12) R = Math.max(R, h + 1e-6);
        double normalizedRadius = (h > 1e-12) ? (R / h) : R;

        // Update pinned params (keep orientation/up)
        pinnedParams = new PinnedParams(normalizedRadius, pinnedParams.up, pinnedParams.side, cy >= 0.0);
    }

    private PinnedParams computePreferredPinnedParams(PlaneBasis basis, ManipulatedTrackNodeOnCircleArc node) {
        Vector dir = computePreferredDirection(node);
        if (dir == null) {
            return null;
        }

        // half chord length in world units
        double h = 0.5 * first.node.getPosition().distance(last.node.getPosition());
        if (h < 1e-12) {
            return null;
        }

        double dx = dir.dot(basis.ex);
        double dy = dir.dot(basis.ey);

        // First or last node?
        boolean isFirst = (node == first);

        // Compute whether to take the minor or the major arc. This depends on the tangent direction versus
        // the chord direction.
        boolean isMinorArc = (dx <= 0.0) != isFirst;

        Vector ex = computeBiSectorDirection();

        Quaternion upQuat = pinnedParams.up.clone();
        upQuat.multiply(Quaternion.fromToRotation(upQuat.forwardVector(), ex, upQuat.upVector()));
        Vector up = upQuat.upVector();

        if (Math.abs(ex.dot(up)) > 0.9999) {
            Vector alt = new Vector(1, 0, 0);
            if (Math.abs(ex.dot(alt)) > 0.9999) alt = new Vector(0, 0, 1);
            up = alt;
        }

        Vector ey = up.getCrossProduct(ex);
        double eyLen = ey.length();
        if (eyLen < 1e-8) {
            ey = new Vector(0, 1, 0).crossProduct(ex);
            if (ey.lengthSquared() < 1e-12) {
                ey = new Vector(0, 0, 1).crossProduct(ex);
            }
        } else {
            ey.multiply(1.0 / eyLen);
        }

        // Ensure pointing towards where the other nodes are (use existing pinned side)
        if ((ey.dot(upQuat.rightVector()) * pinnedParams.side) < 0.0) {
            ey.multiply(-1.0);
        }

        // For minor arc candidate, invert ey like the normal buildPlaneBasisFromPins does
        if (isMinorArc) ey.multiply(-1.0);

        // Project preferred tangent direction into this basis
        double dlen = Math.hypot(dx, dy);
        if (dlen < 1e-8) return null;
        double pdx = dx / dlen, pdy = dy / dlen;
        if (Math.abs(pdy) < 1e-8) return null;

        // Compute cy,R. If pdy is well-conditioned use analytic formula, otherwise recover via bounded ternary search.
        double cy = isFirst ? -h * (pdx / pdy) : h * (pdx / pdy);
        double R = Math.hypot(cy, h);

        // Compute center offset cy that yields a radius whose tangent matches direction
        if (h > 1e-12) R = Math.max(R, h + 1e-6);
        double normalized = R / h;

        // Compute center direction in 3D space and side sign consistent with computePinnedParams2D
        // This stuff appears broken: radius is correct but side is sometimes wrong
        // Use the temporary local ey (already adjusted above, including minor-arc inversion)
        Vector centerDir3 = ey.clone().multiply(cy);
        // Use the temporary upQuat (aligned to ex) to determine which side the center points to.
        double side = (upQuat.rightVector().dot(centerDir3) < 0.0) ? -1.0 : 1.0;
        if (isMinorArc) {
            side = -side;
        }

        // When changing minor/major arc compared to before, this has a big impact on the plane basis
        // that is used to compute the side. Invert the side when the minor/major arc choice differs from before, to compensate for this.
        if (isMinorArc != pinnedParams.minorArc) {
            side = -side;
        }

        return new PinnedParams(normalized, pinnedParams.up, side, isMinorArc);
    }

    /**
     * Computes the preferred direction vector for the provided node based on its connections to other nodes.
     * Returns null if the node has no neighbour node that constrains the direction.
     *
     * @param node Node
     * @return Preferred direction vector, or null if no preferred direction could be determined
     */
    private Vector computePreferredDirection(ManipulatedTrackNodeOnCircleArc node) {
        // Look at all connections of this node, and figure out the preferred direction vector
        // based on the other node in the connection. We want to strive for straight connections
        // leaving the circle, so this is a good heuristic for the preferred direction.
        Vector preferredDir = null;
        for (TrackNode neighbour : node.getNeighbours()) {
            // Ignore neighbours that are part of the middle nodes
            if (middleNodes.stream().anyMatch(n -> n.isNode(neighbour))) {
                continue;
            }

            // Junctions are not supported
            if (preferredDir != null) {
                return null;
            }

            preferredDir = node.node.getPosition().clone().subtract(neighbour.getPosition());
            double len = preferredDir.length();
            if (len > 1e-4) {
                preferredDir.multiply(1.0 / len);
            }
        }
        return preferredDir;
    }

    /**
     * Adjusts the theta of the provided node so that it lies on the circle at the provided position.
     *
     * @param node CircleNode
     * @param posOnCircle Point on the circle
     */
    private void moveNodeTheta(ManipulatedTrackNodeOnCircleArc node, Vector posOnCircle) {
        NodeBiSectorThetaCalculator calc = createNodeThetaCalculator();

        // Compute theta fraction that corresponds to the minimum distance
        // Abort if this theta fraction is too small to move nodes around
        double arcLength = Math.abs(calc.arcAngle * calc.circle.r);
        double thetaLimit = MINIMUM_CONNECTION_DISTANCE / arcLength;
        if (arcLength < MINIMUM_CONNECTION_DISTANCE || (manipulatedNodes.size() - 1) * thetaLimit >= 1.0) {
            return; // No room to move nodes
        }

        // Compute how much space is required left and right of this node
        // Also see at what theta value neighbouring nodes need to be moved
        double thetaLeftLimit = thetaLimit;
        double thetaRightLimit = 1.0 - thetaLimit;
        double leftTheta = -Double.MAX_VALUE;
        double rightTheta = Double.MAX_VALUE;
        for (ManipulatedTrackNodeOnCircleArc otherNode : middleNodes) {
            if (otherNode == node) continue;
            if (otherNode.initialTheta < 0.0 || otherNode.initialTheta > 1.0) continue;
            if (otherNode.initialTheta < node.initialTheta) {
                thetaLeftLimit += thetaLimit;
                leftTheta = Math.max(leftTheta, otherNode.initialTheta);
            } else {
                thetaRightLimit -= thetaLimit;
                rightTheta = Math.min(rightTheta, otherNode.initialTheta);
            }
        }
        if (thetaLeftLimit > thetaRightLimit) {
            return; // No room to move nodes
        }

        // Compute best theta for current pinnedParams using NodeThetaCalculator, limited by minThetaLeft/Right
        node.theta = MathUtil.clamp(calc.computeTheta(posOnCircle), thetaLeftLimit, thetaRightLimit);

        // If theta exceeds left limit, adjust left-side nodes proportionally
        if ((node.theta - thetaLimit) <= leftTheta) {
            double scale = (node.theta - thetaLimit) / leftTheta;
            for (ManipulatedTrackNodeOnCircleArc otherNode : middleNodes) {
                if (otherNode == node) continue;
                if (otherNode.initialTheta < 0.0 || otherNode.initialTheta > 1.0) continue;
                if (otherNode.initialTheta < node.initialTheta) {
                    otherNode.theta = scale * otherNode.initialTheta;
                }
            }
        }

        // If theta exceeds right limit, adjust right-side nodes proportionally
        if ((node.theta + thetaLimit) >= rightTheta) {
            double scale = (1.0 - (node.theta + thetaLimit)) / (1.0 - rightTheta);
            for (ManipulatedTrackNodeOnCircleArc otherNode : middleNodes) {
                if (otherNode == node) continue;
                if (otherNode.initialTheta < 0.0 || otherNode.initialTheta > 1.0) continue;
                if (otherNode.initialTheta > node.initialTheta) {
                    otherNode.theta = 1.0 - (1.0 - otherNode.initialTheta) * scale;
                }
            }
        }
    }

    /**
     * Apply stored middle node thetas to position nodes on the provided circle using the given plane basis.
     * Preserves the circle radius and center; reconstructs 3D positions and assigns them to nodes.
     */
    protected void applyNodesToCircle() {
        NodeBiSectorThetaCalculator calc = createNodeThetaCalculator();

        // Update all nodes, not just the middle ones
        // For the first/last node we do not update position, as it should be unchanged anyway
        for (ManipulatedTrackNodeOnCircleArc cn : manipulatedNodes) {
            double ang = calc.computeAngleFromTheta(cn.theta);

            if (cn != first && cn != last) {
                Vector newPos = calc.computePointAtAngle(ang);
                cn.setPosition(newPos);
            }

            Vector newUp = cn.up.clone();
            calc.computeTangentOrientation(ang).transformPoint(newUp);
            cn.setOrientation(newUp);
        }
    }

    private NodeBiSectorThetaCalculator createNodeThetaCalculator() {
        PlaneBasis basis = buildPlaneBasisFromPins();
        Circle2D circle = buildCircle2DFromPins();
        NodeAngleCalculator angleCalc = new NodeAngleCalculator(basis, circle);

        double angleFirst = angleCalc.computeAngle(first.node.getPosition());
        double angleLast  = angleCalc.computeAngle(last.node.getPosition());

        // Compute the arc angle difference from the first node to the last node, on the circle
        double arcAngle = getNormalizedAngleDifference(angleLast, angleFirst);
        if (arcAngle < 1e-8) {
            // treat as full circle when effectively identical angles
            arcAngle = 2.0 * Math.PI;
        } else if (!pinnedParams.minorArc) {
            arcAngle = 2.0 * Math.PI - arcAngle;
        }

        // When taking the minor arc, the angle increases from first->last
        // When taking the major arc, this is reversed
        double angleSide = pinnedParams.minorArc ? 1.0 : -1.0;

        return new NodeBiSectorThetaCalculator(basis, circle, angleFirst, arcAngle, angleSide);
    }

    protected PlaneBasis buildPlaneBasisFromPins() {
        if (pinnedParams == null) {
            throw new IllegalStateException("Pinned parameters have not been computed");
        }

        // midpoint = plane origin
        Vector mid = computeBiSectorMidPoint();

        // chord direction -> ex
        Vector ex = computeBiSectorDirection();

        // pinnedParams.up should be aligned (forwardVector) with chord direction (ex) from first->last
        Quaternion upQuat = pinnedParams.up.clone();
        upQuat.multiply(Quaternion.fromToRotation(upQuat.forwardVector(), ex, upQuat.upVector()));
        Vector up = upQuat.upVector();

        // If ex is almost parallel to upVec, choose an alternative up to avoid near-zero cross product
        if (Math.abs(ex.dot(up)) > 0.9999) {
            // try a few stable alternatives
            Vector alt = new Vector(1, 0, 0);
            if (Math.abs(ex.dot(alt)) > 0.9999) alt = new Vector(0, 0, 1);
            up = alt;
        }

        // ey = upVec x ex (ensures ey perpendicular to ex and aligned with pinned up)
        Vector ey = up.getCrossProduct(ex);
        {
            double eyLen = ey.length();
            if (eyLen < 1e-8) {
                // last-resort fallback
                ey = new Vector(0, 1, 0).crossProduct(ex);
                if (ey.lengthSquared() < 1e-12) {
                    ey = new Vector(0, 0, 1).crossProduct(ex);
                }
            } else {
                ey.multiply(1.0 / eyLen);
            }
        }

        // Ensure pointing towards where the other nodes are
        if ((ey.dot(upQuat.rightVector()) * pinnedParams.side) < 0.0) {
            ey.multiply(-1.0);
        }

        // But we request pointing towards circle center, so for minor arc we must invert ey
        if (pinnedParams.minorArc) {
            ey.multiply(-1.0);
        }

        // Build and return a PlaneBasis with centroid=mid, ex and ey set.
        return new PlaneBasis(mid, ex, ey, up);
    }

    protected Circle2D buildCircle2DFromPins() {
        if (this.pinnedParams == null) throw new IllegalStateException("Pinned parameters have not been computed");

        // Use the same plane basis as the rest of the code (centroid == midpoint, ex == chord direction)
        PlaneBasis basis = buildPlaneBasisFromPins();

        // Project endpoints into that basis (origin = basis.centroid)
        Vector p1 = first.node.getPosition().clone().subtract(basis.centroid);
        Vector p2 = last.node.getPosition().clone().subtract(basis.centroid);
        double p1x = p1.dot(basis.ex), p1y = p1.dot(basis.ey);
        double p2x = p2.dot(basis.ex), p2y = p2.dot(basis.ey);

        // chord half length in this local frame
        double chordLen = Math.hypot(p2x - p1x, p2y - p1y);
        double halfChord = 0.5 * chordLen;

        // radius in world units
        double r = halfChord * this.pinnedParams.radius;

        // If radius smaller than half-chord, center is at midpoint (t = 0)
        double t = 0.0;
        if (r > halfChord) {
            t = Math.sqrt(Math.max(0.0, r * r - halfChord * halfChord));
        }
        return new Circle2D(0.0, t, r);
    }

    /**
     * Minimal pinned parameters: radius and side sign along the perp bisector.
     * This is used to reconstruct the full circle in 3D space using the first and last
     * node's positions.
     */
    protected static class PinnedParams {
        /**
         * Radius of the constrained circle as a multiple of the half-distance between the first and last node.
         * A value of 1.0 indicates a 180 degrees semi-circle.
         */
        public final double radius;
        /** Roll/normal around the perp-bisector plane constrained by first/last node direction */
        public final Quaternion up;
        /** Multiplier for {@link #up}.rightVector() to find the side the other nodes are at */
        public final double side;
        /** Whether the arc the middle nodes go through is the minor (short) arc (inner, < 180), or the major (long) arc (outer, > 180) */
        public final boolean minorArc;

        PinnedParams(double radius, Quaternion up, double side, boolean minorArc) {
            this.radius = radius;
            this.side = side;
            this.minorArc = minorArc;
            this.up = up;
        }

        @Override
        public String toString() {
            return "PinnedParams{\n" +
                    "  radius: " + radius + " [" + (minorArc ? "MINOR" : "MAJOR") + "],\n" +
                    "  node_side: " + up.rightVector().multiply(side) + ",\n" +
                    "  minorArc: " + minorArc + ",\n" +
                    "  bisector: " + up.forwardVector() + ",\n" +
                    "  up: " + up.upVector() + "\n" +
                    "}";
        }
    }

    /**
     * Compute the pinned parameters (radius and side) for the constrained circle that passes
     * through the two pinned nodes (first and last) while best-fitting the remaining edited nodes.
     */
    protected PinnedParams computePinnedParams2D() {
        // compute base radius unit based on the distance between the first and last node
        double baseRadius = 0.5 * first.node.getPosition().distance(last.node.getPosition());

        // use node orientation to compute average up direction, which will flip the plane normal
        // if needed to be more aligned with the average up direction
        List<Vector> pts = new ArrayList<>(manipulatedNodes.size());
        Vector averageUp = new Vector();
        for (ManipulatedTrackNodeOnCircleArc dn : manipulatedNodes) {
            pts.add(dn.node.getPosition().clone());
            averageUp.add(dn.node.getOrientation());
        }
        double averageUpLen = averageUp.length();
        if (averageUpLen < 1e-8) {
            averageUp = new Vector(0, 1, 0);
        } else {
            averageUp.multiply(1.0 / averageUpLen);
        }

        // gather all positions and estimate a plane basis (stable orientation: world up as +Y)
        // keep in mind that the basis.ex and basis.ey directions are arbitrary
        // the only use of this basis is to compute a 2D projection of the points onto a flat surface
        // where circle fitting can be done.
        PlaneBasis basis = PlaneBasis.estimateFromPoints(pts, averageUp);

        // project each node into 2D plane coords (relative to basis.centroid)
        // compute the pinned coordinates, and the other points that are not pinned
        Vector2 p1 = null, p2 = null;
        List<Vector2> otherPoints = new ArrayList<>(manipulatedNodes.size());
        for (ManipulatedTrackNodeOnCircleArc dn : manipulatedNodes) {
            Vector p = dn.node.getPosition().clone().subtract(basis.centroid);
            Vector2 p2d = new Vector2(p.dot(basis.ex), p.dot(basis.ey));
            if (dn == first) {
                p1 = p2d;
            } else if (dn == last) {
                p2 = p2d;
            } else {
                otherPoints.add(p2d);
            }
        }

        // Probably should not happen, but just in case
        if (p1 == null || p2 == null) {
            return new PinnedParams(
                    1.0,
                    Quaternion.fromLookDirection(computeBiSectorDirection(), basis.normal),
                    1.0,
                    true
            );
        }

        // solve along perpendicular bisector minimizing SSE, return radius and side
        Circle2DBiSector circle = Circle2D.fitAlongPerpBisector(p1, p2, otherPoints);

        // compute center direction in 3D space
        Vector centerDir3 = basis.ex.clone().multiply(circle.sideX)
                .add(basis.ey.clone().multiply(circle.sideY));

        // compute orientation along bisector between the first/last nodes
        // use the basis normal (up vector) as up direction
        Quaternion up = Quaternion.fromLookDirection(computeBiSectorDirection(), basis.normal);

        // orientation right vector should point towards the side the other nodes are at
        double side = (up.rightVector().dot(centerDir3) < 0.0) ? -1.0 : 1.0;

        // circle center is the opposite side when on the minor arc
        if (circle.minorArc) {
            side = -side;
        }

        return new PinnedParams(circle.r / baseRadius, up, side, circle.minorArc);
    }

    private Vector computeBiSectorMidPoint() {
        return first.node.getPosition().clone().add(last.node.getPosition()).multiply(0.5);
    }

    private Vector computeBiSectorDirection() {
        Vector dir = last.node.getPosition().clone().subtract(first.node.getPosition());
        double dirLen = dir.length();
        if (dirLen < 1e-6) {
            dir = new Vector(1, 0, 0);
        } else {
            dir.multiply(1.0 / dirLen);
        }
        return dir;
    }

    private static class ThetaSortedNode implements Comparable<ThetaSortedNode> {
        public final ManipulatedTrackNodeOnCircleArc node;
        public final double theta;

        public ThetaSortedNode(ManipulatedTrackNodeOnCircleArc node) {
            this.node = node;
            this.theta = node.theta;
        }

        @Override
        public int compareTo(ThetaSortedNode o) {
            return Double.compare(this.theta, o.theta);
        }
    }
}
