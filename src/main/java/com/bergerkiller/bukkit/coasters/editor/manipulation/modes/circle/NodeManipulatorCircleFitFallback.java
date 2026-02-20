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
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Node drag manipulator that fits a circle going through all selected nodes.
 * The circle is manipulated by dragging a node around the circle, while the opposite
 * side is pinned in place. Middle nodes maintain their angle around the circle.<br>
 * <br>
 * Relative node positions on the circle cannot be changed, but that can be done by
 * creating a sub-arc selection and adjusting the nodes there.
 */
class NodeManipulatorCircleFitFallback extends NodeManipulatorCircleFit<ManipulatedTrackNodeOnCircle> {
    /** Parameters at the time dragging was started */
    private PinnedParams startParams = null;
    /** Additional parameters that are used to compute the full circle */
    private PinnedParams pinnedParams = null;

    /** Which of the nodes was clicked at the start (is dragged), or null if none (scale mode) */
    private ManipulatedTrackNodeOnCircle clickedNode = null;
    /** Opposite position on the circle of the clicked node to pin the circle to */
    private Vector pinnedOppositePosition = null;

    public NodeManipulatorCircleFitFallback(PlayerEditState state, List<ManipulatedTrackNode> manipulatedNodes) {
        super(state, manipulatedNodes, ManipulatedTrackNodeOnCircle::new);

        // Compute pinned parameters for the constrained circle fit
        this.startParams = this.pinnedParams = this.computePinnedParams2D();

        // Compute initial angle and up-vector for all dragged nodes
        NodeAngleCalculator calculator = createNodeAngleCalculator();
        for (ManipulatedTrackNodeOnCircle manipulatedNode : this.manipulatedNodes) {
            manipulatedNode.angle = calculator.computeAngle(manipulatedNode.node.getPosition());
            manipulatedNode.up = manipulatedNode.node.getOrientation().clone();
            pinnedParams.orientation.invTransformPoint(manipulatedNode.up);
        }
    }

    @Override
    public void onDragStarted(NodeDragEvent event) {
        TrackNode clickedNodeNode = state.findLookingAt();
        if (clickedNodeNode != null) {
            for (ManipulatedTrackNodeOnCircle manipulatedNode : manipulatedNodes) {
                if (manipulatedNode.node == clickedNodeNode) {
                    clickedNode = manipulatedNode;
                    clickedNode.dragPosition = clickedNode.node.getPosition().clone();
                    break;
                }
            }
        }

        if (clickedNode != null) {
            // Opposite position of the clicked node
            NodeAngleCalculator calculator = createNodeAngleCalculator();
            pinnedOppositePosition = calculator.computePointAtAngle(clickedNode.angle + Math.PI);

            // Adjust angles so that clicked node is at angle 0 to simplify the node-dragging maths
            double angleOffset = clickedNode.angle;
            Quaternion orientationChange = new Quaternion();
            orientationChange.rotateY(Math.toDegrees(angleOffset));
            for (ManipulatedTrackNodeOnCircle manipulatedNode : manipulatedNodes) {
                manipulatedNode.angle -= angleOffset;
                orientationChange.invTransformPoint(manipulatedNode.up);
            }
            pinnedParams.orientation.multiply(orientationChange);
        }
    }

    @Override
    public void onDragUpdate(NodeDragEvent event) {
        if (clickedNode != null) {
            // Drag the node around
            NodeDragPosition dragPos = handleDrag(clickedNode, event, true);

            // Align the circle according to the pinned opposite position
            alignCircle(pinnedOppositePosition, dragPos.position);
        }

        applyCircleNodesToCircle();
    }

    @Override
    public void onDragFinished(HistoryChangeCollection history, NodeDragEvent event) throws ChangeCancelledException {
        // Merge/record behavior can be copied from other manipulators when implementing finish behavior.
        // For now, do nothing special.
        recordEditedNodesInHistory(history);
    }

    @Override
    public void equalizeNodeSpacing(HistoryChangeCollection history) throws ChangeCancelledException {
        ManipulatedTrackNodeSpacingEqualizer<ManipulatedTrackNodeOnCircle> equalizer = new ManipulatedTrackNodeSpacingEqualizer<>(manipulatedNodes);
        equalizer.findChains();
        if (equalizer.chains.isEmpty()) {
            throw new ChangeCancelledException();
        }

        NodeAngleCalculator calc = createNodeAngleCalculator();

        for (ManipulatedTrackNodeSpacingEqualizer.NodeChainComputation<ManipulatedTrackNodeOnCircle> chain : equalizer.chains) {
            double arcAngle;
            double arcSide = 1.0;
            if (chain.first == chain.last) {
                // Full circle
                arcAngle = 2.0 * Math.PI;
            } else {
                // Arc between first and last node
                arcAngle = getNormalizedAngleDifference(chain.last.angle, chain.first.angle);

                // Determine using the other nodes which side of the arc to use
                int ord = 0;
                for (ManipulatedTrackNodeOnCircle dn : chain.middleNodes) {
                    double diffFromFirst = dn.angle - chain.first.angle;
                    while (diffFromFirst < 0.0) {
                        diffFromFirst += 2.0 * Math.PI;
                    }
                    if (diffFromFirst <= arcAngle) {
                        ord++;
                    } else {
                        ord--;
                    }
                }
                if (ord < 0) {
                    arcSide = -1.0;
                }
            }

            // Compute angles for all nodes in-between first and last
            List<TrackConnection.Point> middleNodePoints = new ArrayList<>(chain.middleNodes.size());
            double anglePerNode = arcSide * arcAngle / (double) (chain.middleNodes.size() + 1);
            for (int i = 0; i < chain.middleNodes.size(); i++) {
                ManipulatedTrackNodeOnCircle middleNode = chain.middleNodes.get(i);
                double ang = chain.first.angle + anglePerNode * (i + 1);
                middleNode.angle = ang;

                // Circle angle -> position and orientation on the circle
                Vector newPos = calc.computePointAtAngle(ang);
                Vector newUpVec = middleNode.up.clone();
                Quaternion tangent = calc.computeTangentOrientation(ang);
                tangent.transformPoint(newUpVec);
                Quaternion newUp = Quaternion.fromLookDirection(tangent.forwardVector(), newUpVec);

                // Save
                middleNodePoints.add(new TrackConnection.Point(newPos, newUp));
            }

            chain.applyMiddleNodePositions(state, history, middleNodePoints);
        }
    }

    /**
     * Apply stored middle node thetas to position nodes on the provided circle using the given plane basis.
     * Preserves the circle radius and center; reconstructs 3D positions and assigns them to nodes.
     */
    protected void applyCircleNodesToCircle() {
        NodeAngleCalculator calculator = createNodeAngleCalculator();

        for (ManipulatedTrackNodeOnCircle dn : manipulatedNodes) {
            Vector newPos = calculator.computePointAtAngle(dn.angle);
            Vector newUp = dn.up.clone();
            pinnedParams.orientation.transformPoint(newUp);
            dn.setPosition(newPos);
            dn.setOrientation(newUp);
        }
    }

    /**
     * Re-aligns the circle so that the center is between the two points, the radius is the distance
     * between the two points divided by two, and the orientation is aligned accordingly.
     * This requires that p2 sits at angle 0.
     *
     * @param p1 Start (pinned) point
     * @param p2 Dragged point at angle 0
     */
    private void alignCircle(Vector p1, Vector p2) {
        double radius = p1.distance(p2) * 0.5;
        if (radius < 1e-6) {
            return;
        }

        Vector center = p1.clone().add(p2).multiply(0.5);
        Vector dir = p2.clone().subtract(p1).multiply(1.0 / (2.0 * radius));
        Quaternion newOrientation = Quaternion.fromLookDirection(dir, startParams.orientation.upVector());

        // Store new pinned parameters
        this.pinnedParams = new PinnedParams(radius, center, newOrientation);
    }

    protected PlaneBasis buildPlaneBasisFromPins() {
        if (pinnedParams == null) {
            throw new IllegalStateException("Pinned parameters have not been computed");
        }

        // Build and return a PlaneBasis with centroid=mid, ex and ey set.
        return new PlaneBasis(pinnedParams.center,
                pinnedParams.orientation.forwardVector(),
                pinnedParams.orientation.rightVector(),
                pinnedParams.orientation.upVector());
    }

    protected Circle2D buildCircle2DFromPins() {
        if (this.pinnedParams == null) throw new IllegalStateException("Pinned parameters have not been computed");

        // Already aligned at the center position, so only needs radius
        return new Circle2D(0.0, 0.0, this.pinnedParams.radius);
    }

    private NodeAngleCalculator createNodeAngleCalculator() {
        PlaneBasis basis = buildPlaneBasisFromPins();
        Circle2D circle = buildCircle2DFromPins();
        return new NodeAngleCalculator(basis, circle);
    }

    /**
     * Minimal pinned parameters: radius and side sign along the perp bisector.
     * This is used to reconstruct the full circle in 3D space using the first and last
     * node's positions.
     */
    protected static class PinnedParams {
        /** Radius of the constrained circle in absolute units */
        public final double radius;
        /** The center of the circle in 3D space */
        public final Vector center;
        /** Base orientation of the circle. Computed node angles are relative to this. */
        public final Quaternion orientation;

        PinnedParams(double radius, Vector center, Quaternion orientation) {
            this.radius = radius;
            this.center = center;
            this.orientation = orientation;
        }

        @Override
        public String toString() {
            return "PinnedParams{\n" +
                    "  radius: " + radius + ",\n" +
                    "  center: " + center + ",\n" +
                    "  up: " + orientation.upVector() + ",\n" +
                    "  fwd: " + orientation.forwardVector() + "\n" +
                    "}";
        }
    }

    /**
     * Compute the pinned parameters (radius and orientation) for the constrained circle that passes
     * through all edited nodes.
     */
    protected PinnedParams computePinnedParams2D() {
        // use node orientation to compute average up direction, which will flip the plane normal
        // if needed to be more aligned with the average up direction
        List<Vector> pts = new ArrayList<>(manipulatedNodes.size());
        Vector averageUp = new Vector();
        for (ManipulatedTrackNodeOnCircle dn : manipulatedNodes) {
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
        List<Vector2> points = new ArrayList<>(manipulatedNodes.size());
        for (ManipulatedTrackNodeOnCircle dn : manipulatedNodes) {
            Vector p = dn.node.getPosition().clone().subtract(basis.centroid);
            Vector2 p2d = new Vector2(p.dot(basis.ex), p.dot(basis.ey));
            points.add(p2d);
        }

        // solve circle using taubin algorithm in 2D
        Circle2D circle = Circle2D.fitCircleTaubinLike(points);

        // compute center of the circle, taubin will slightly offset it compared to the centroid
        Vector center = basis.ex.clone().multiply(circle.cx)
                .add(basis.ey.clone().multiply(circle.cy))
                .add(basis.centroid);

        // there is not really a stable orientation for the circle, so just use the basis orientation
        // this one is pinned, so node angles are relative to this orientation, so the absolute
        // value is not important so long it's aligned with the plane.
        Quaternion orientation = Quaternion.fromLookDirection(basis.ex, basis.normal);

        return new PinnedParams(circle.r, center, orientation);
    }
}
