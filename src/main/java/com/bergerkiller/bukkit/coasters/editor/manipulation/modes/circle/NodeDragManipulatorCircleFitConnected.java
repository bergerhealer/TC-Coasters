package com.bergerkiller.bukkit.coasters.editor.manipulation.modes.circle;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditNode;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChangeCollection;
import com.bergerkiller.bukkit.coasters.editor.manipulation.NodeDragEvent;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.math.Vector2;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Node drag manipulator that fits a circle pinned by the first and last nodes
 * of a connected sequence of nodes being edited. This is the most common type
 * of circle fit operation, namely curves and such.
 */
public class NodeDragManipulatorCircleFitConnected extends NodeDragManipulatorCircleFit {
    /**
     * Minimum distance kept between nodes while dragging to avoid nodes merging.
     * Nodes will move aside to maintain this distance.
     */
    private static final double MIN_NODE_DIST = 0.2;

    /** First node of the sequence of nodes that is selected */
    private final PlayerEditNode first;
    /** Last node of the sequence of nodes that is selected */
    private final PlayerEditNode last;
    /** Which of the nodes was clicked at the start (is dragged), or null if none (scale mode) */
    private PlayerEditNode clickedNode = null;

    /** Parameters at the time dragging was started */
    private PinnedParams startParams = null;
    /** Additional parameters that are used to compute the full circle */
    private PinnedParams pinnedParams = null;
    /** Middle nodes with their theta values along the arc from first->last */
    protected List<ConnectedMiddleNode> middleNodes = null;

    public NodeDragManipulatorCircleFitConnected(PlayerEditState state, Map<TrackNode, PlayerEditNode> editedNodes, PlayerEditNode first, PlayerEditNode last) {
        super(state, editedNodes);

        if (first == null) {
            throw new IllegalStateException("First node is null");
        }
        if (last == null) {
            throw new IllegalStateException("Last node is null");
        }

        this.first = first;
        this.last = last;
    }

    @Override
    public void onStarted(NodeDragEvent event) {
        // Compute pinned parameters for the constrained circle fit
        this.startParams = this.pinnedParams = this.computePinnedParams2D();

        // Fit middle nodes and store their thetas
        this.middleNodes = computeMiddleNodesFromCircle2D();

        TrackNode clickedNodeNode = state.findLookingAt();
        if (clickedNodeNode != null) {
            for (PlayerEditNode node : editedNodes) {
                if (node.node == clickedNodeNode) {
                    clickedNode = node;
                    clickedNode.dragPosition = clickedNode.node.getPosition().clone();
                    break;
                }
            }
        }
    }

    @Override
    public void onUpdate(NodeDragEvent event) {
        // Transform the first node (Test)
        if (clickedNode == first || clickedNode == last) {
            handleDrag(clickedNode, event, true).applyTo(clickedNode.node);
            applyMiddleNodesToCircle();
        } else if (clickedNode != null) {
            // Drag the middle node around
            NodeDragPosition dragPos = handleDrag(clickedNode, event, true);

            // Rotate the circle so that the node can be on it
            adjustCircleUp(dragPos.position);

            // Figure out what new radius is needed to place the dragged middle node on the circle
            adjustRadiusForNode(dragPos.position);

            // Find which of the middle nodes is the clicked node
            middleNodes.stream().filter(n -> n.node == clickedNode).findFirst().ifPresent(n -> {
                // Move this node theta to be on the circle at the dragged position
                moveNodeTheta(n, dragPos.position);
            });

            applyMiddleNodesToCircle();
        } else {
            // Move all nodes, same as position mode
            // We do re-apply the middle nodes to take on a circle shape again
            if (event.isStart()) {
                applyMiddleNodesToCircle();
            }
            for (PlayerEditNode editNode : editedNodes) {
                handleDrag(editNode, event, false).applyTo(editNode.node);
            }
        }
    }

    @Override
    public void onFinished(HistoryChangeCollection history, NodeDragEvent event) throws ChangeCancelledException {
        // Merge/record behavior can be copied from other manipulators when implementing finish behavior.
        // For now, do nothing special.
        recordEditedNodesInHistory(history);
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

    /**
     * Adjusts the theta of the provided node so that it lies on the circle at the provided position.
     *
     * @param node ConnectedMiddleNode
     * @param posOnCircle Point on the circle
     */
    private void moveNodeTheta(ConnectedMiddleNode node, Vector posOnCircle) {
        NodeThetaCalculator calc = createNodeThetaCalculator();

        // Compute theta fraction that corresponds to the minimum distance
        // Abort if this theta fraction is too small to move nodes around
        double arcLength = Math.abs(calc.arcAngle * calc.circle.r);
        double thetaLimit = MIN_NODE_DIST / arcLength;
        if (arcLength < MIN_NODE_DIST || (editedNodes.size() - 1) * thetaLimit >= 1.0) {
            return; // No room to move nodes
        }

        // Compute how much space is required left and right of this node
        // Also see at what theta value neighbouring nodes need to be moved
        double thetaLeftLimit = thetaLimit;
        double thetaRightLimit = 1.0 - thetaLimit;
        double leftTheta = -Double.MAX_VALUE;
        double rightTheta = Double.MAX_VALUE;
        for (ConnectedMiddleNode otherNode : middleNodes) {
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
            for (ConnectedMiddleNode otherNode : middleNodes) {
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
            for (ConnectedMiddleNode otherNode : middleNodes) {
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
    protected void applyMiddleNodesToCircle() {
        PlaneBasis basis = buildPlaneBasisFromPins();
        Circle2D circle = buildCircle2DFromPins();

        // Compute the angle of the first node on the circle
        // This is the reference angle for all middle nodes
        Vector p1v = first.node.getPosition().clone().subtract(basis.centroid);
        double p1x = p1v.dot(basis.ex), p1y = p1v.dot(basis.ey);
        double angleFirst = Math.atan2(p1y - circle.cy, p1x - circle.cx);

        // Compute the angle of the last node on the circle
        Vector p2v = last.node.getPosition().clone().subtract(basis.centroid);
        double p2x = p2v.dot(basis.ex), p2y = p2v.dot(basis.ey);
        double angleLast = Math.atan2(p2y - circle.cy, p2x - circle.cx);

        // Compute the arc angle difference from the first node to the last node, on the circle
        double arcAngle = Math.abs(normalizeAngleDiff(angleLast - angleFirst));
        if (arcAngle < 1e-8) {
            // treat as full circle when effectively identical angles
            arcAngle = 2.0 * Math.PI;
        }

        // Take into account whether the pinned arc is the minor arc or the major arc
        if (!pinnedParams.minorArc) {
            arcAngle = 2.0 * Math.PI - arcAngle;
        }

        // When taking the minor arc, the angle increases from first->last
        // When taking the major arc, this is reversed
        double angleSide = pinnedParams.minorArc ? 1.0 : -1.0;

        for (ConnectedMiddleNode cmn : middleNodes) {
            double ang = angleFirst + (angleSide * cmn.theta * arcAngle);
            double x2 = circle.cx + Math.cos(ang) * circle.r;
            double y2 = circle.cy + Math.sin(ang) * circle.r;
            Vector newPos = basis.centroid.clone()
                    .add(basis.ex.clone().multiply(x2))
                    .add(basis.ey.clone().multiply(y2));

            cmn.node.node.setPosition(newPos);
        }
    }

    /**
     * A middle node that is connected to the first and last nodes of the sequence.
     * Stores the normalized theta value along the arc from first->last.
     */
    protected static class ConnectedMiddleNode implements Comparable<ConnectedMiddleNode> {
        public final PlayerEditNode node;
        /**
         * Initial theta value at the time dragging begun.
         * Normalized fraction along arc from first->last (can be <0 or >1)
         */
        public final double initialTheta;
        /**
         * Calculated (new) theta value during dragging.
         * Normalized fraction along arc from first->last (can be <0 or >1).
         */
        public double theta;

        public ConnectedMiddleNode(PlayerEditNode node, double initialTheta) {
            this.node = node;
            this.initialTheta = initialTheta;
            this.theta = initialTheta;
        }

        @Override
        public int compareTo(ConnectedMiddleNode o) {
            return Double.compare(this.initialTheta, o.initialTheta);
        }
    }

    /**
     * Compute and store the middle node thetas for all edited nodes except first/last.
     * Circle2D and PlaneBasis must be in the same 2D coordinate system used by the fitter.
     * Returns the list stored in `middleNodes`.
     */
    protected List<ConnectedMiddleNode> computeMiddleNodesFromCircle2D() {
        NodeThetaCalculator calculator = createNodeThetaCalculator();

        List<ConnectedMiddleNode> middleNodes = new ArrayList<>(editedNodes.size() - 2);
        for (PlayerEditNode en : editedNodes) {
            if (en == first || en == last) continue;
            middleNodes.add(new ConnectedMiddleNode(en, calculator.computeTheta(en.node.getPosition())));
        }
        Collections.sort(middleNodes);

        return middleNodes;
    }

    private NodeThetaCalculator createNodeThetaCalculator() {
        PlaneBasis basis = buildPlaneBasisFromPins();
        Circle2D circle = buildCircle2DFromPins();

        // project first/last into plane coords
        Vector p1v = first.node.getPosition().clone().subtract(basis.centroid);
        Vector p2v = last.node.getPosition().clone().subtract(basis.centroid);
        double p1x = p1v.dot(basis.ex), p1y = p1v.dot(basis.ey);
        double p2x = p2v.dot(basis.ex), p2y = p2v.dot(basis.ey);

        double angleFirst = Math.atan2(p1y - circle.cy, p1x - circle.cx);
        double angleLast  = Math.atan2(p2y - circle.cy, p2x - circle.cx);

        // Compute the arc angle difference from the first node to the last node, on the circle
        double arcAngle = Math.abs(normalizeAngleDiff(angleLast - angleFirst));
        if (arcAngle < 1e-8) {
            // treat as full circle when effectively identical angles
            arcAngle = 2.0 * Math.PI;
        } else if (!pinnedParams.minorArc) {
            arcAngle = 2.0 * Math.PI - arcAngle;
        }

        // When taking the minor arc, the angle increases from first->last
        // When taking the major arc, this is reversed
        double angleSide = pinnedParams.minorArc ? 1.0 : -1.0;

        return new NodeThetaCalculator(basis, circle, angleFirst, arcAngle, angleSide);
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
     * Helper class to compute theta values for nodes based on a provided circle and plane basis.
     */
    private static class NodeThetaCalculator {
        public final PlaneBasis basis;
        public final Circle2D circle;
        public final double angleFirst;
        public final double arcAngle;
        public final double angleSide;

        public NodeThetaCalculator(PlaneBasis basis, Circle2D circle, double angleFirst, double arcAngle, double angleSide) {
            this.basis = basis;
            this.circle = circle;
            this.angleFirst = angleFirst;
            this.arcAngle = arcAngle;
            this.angleSide = angleSide;
        }

        public double computeTheta(Vector position) {
            Vector pv = position.clone().subtract(basis.centroid);
            double px = pv.dot(basis.ex), py = pv.dot(basis.ey);
            double ang = Math.atan2(py - circle.cy, px - circle.cx);
            double num = angleSide * (ang - angleFirst);
            if (num < 0.0) {
                num += 2.0 * Math.PI;
            }

            // Make negative if it is left of the first point somehow (broken?)
            if (num > (arcAngle + 0.5 * (2.0 * Math.PI - arcAngle))) {
                num -= 2.0 * Math.PI;
            }

            return num / arcAngle;
        }
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
        List<Vector> pts = new ArrayList<>(editedNodes.size());
        Vector averageUp = new Vector();
        for (PlayerEditNode en : editedNodes) {
            pts.add(en.node.getPosition().clone());
            averageUp.add(en.node.getOrientation());
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
        List<Vector2> otherPoints = new ArrayList<>(editedNodes.size());
        for (PlayerEditNode en : editedNodes) {
            Vector p = en.node.getPosition().clone().subtract(basis.centroid);
            Vector2 p2d = new Vector2(p.dot(basis.ex), p.dot(basis.ey));
            if (en == first) {
                p1 = p2d;
            } else if (en == last) {
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

    private static double normalizeAngleDiff(double a) {
        while (a <= -Math.PI) a += 2.0 * Math.PI;
        while (a >   Math.PI) a -= 2.0 * Math.PI;
        return a;
    }
}
