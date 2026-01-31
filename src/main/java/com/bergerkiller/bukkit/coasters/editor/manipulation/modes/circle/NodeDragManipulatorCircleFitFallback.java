package com.bergerkiller.bukkit.coasters.editor.manipulation.modes.circle;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditNode;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChangeCollection;
import com.bergerkiller.bukkit.coasters.editor.manipulation.NodeDragEvent;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.math.Vector2;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Node drag manipulator that fits a circle going through all selected nodes.
 * The circle is manipulated by dragging a node around the circle, while the opposite
 * side is pinned in place. Middle nodes maintain their angle around the circle.<br>
 * <br>
 * Relative node positions on the circle cannot be changed, but that can be done by
 * creating a sub-arc selection and adjusting the nodes there.
 */
public class NodeDragManipulatorCircleFitFallback extends NodeDragManipulatorCircleFit {
    /** Which of the nodes was clicked at the start (is dragged), or null if none (scale mode) */
    private PlayerEditNode clickedNode = null;
    /** Opposite position on the circle of the clicked node to pin the circle to */
    private Vector pinnedOppositePosition = null;

    /** Parameters at the time dragging was started */
    private PinnedParams startParams = null;
    /** Additional parameters that are used to compute the full circle */
    private PinnedParams pinnedParams = null;
    /** Nodes with their angle values relative to the pinned orientation */
    protected List<CircleNode> circleNodes = null;

    public NodeDragManipulatorCircleFitFallback(PlayerEditState state, Map<TrackNode, PlayerEditNode> editedNodes) {
        super(state, editedNodes);
    }

    @Override
    public void onStarted(NodeDragEvent event) {
        // Compute pinned parameters for the constrained circle fit
        this.startParams = this.pinnedParams = this.computePinnedParams2D();

        // Fit middle nodes and store their thetas
        NodeAngleCalculator calculator = createNodeAngleCalculator();
        this.circleNodes = editedNodes.stream()
                .map(en -> {
                    Vector up = en.node.getOrientation().clone();
                    pinnedParams.orientation.invTransformPoint(up);
                    return new CircleNode(
                            en,
                            calculator.computeAngle(en.node.getPosition()),
                            up);
                })
                .collect(Collectors.toList());

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

        if (clickedNode != null) {
            // Find the clicked circle node angle
            CircleNode cn = this.circleNodes.stream().filter(c -> c.node == clickedNode).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Clicked node not found in circle nodes"));

            // Opposite position of the clicked node
            pinnedOppositePosition = calculator.computePointAtAngle(cn.angle + Math.PI);

            // Adjust angles so that clicked node is at angle 0 to simplify the node-dragging maths
            double angleOffset = cn.angle;
            Quaternion orientationChange = new Quaternion();
            orientationChange.rotateY(Math.toDegrees(angleOffset));
            for (CircleNode cnode : this.circleNodes) {
                cnode.angle -= angleOffset;
                orientationChange.invTransformPoint(cnode.up);
            }
            pinnedParams.orientation.multiply(orientationChange);
        }
    }

    @Override
    public void onUpdate(NodeDragEvent event) {
        if (clickedNode != null) {
            // Drag the node around
            NodeDragPosition dragPos = handleDrag(clickedNode, event, true);

            // Align the circle according to the pinned opposite position
            alignCircle(pinnedOppositePosition, dragPos.position);
        }

        applyCircleNodesToCircle();
    }

    @Override
    public void onFinished(HistoryChangeCollection history, NodeDragEvent event) throws ChangeCancelledException {
        // Merge/record behavior can be copied from other manipulators when implementing finish behavior.
        // For now, do nothing special.
        recordEditedNodesInHistory(history);
    }

    /**
     * Apply stored middle node thetas to position nodes on the provided circle using the given plane basis.
     * Preserves the circle radius and center; reconstructs 3D positions and assigns them to nodes.
     */
    protected void applyCircleNodesToCircle() {
        NodeAngleCalculator calculator = createNodeAngleCalculator();

        for (CircleNode cn : circleNodes) {
            Vector newPos = calculator.computePointAtAngle(cn.angle);
            Vector newUp = cn.up.clone();
            pinnedParams.orientation.transformPoint(newUp);
            cn.node.node.setPosition(newPos);
            cn.node.node.setOrientation(newUp);
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

    /**
     * A middle node that is connected to the first and last nodes of the sequence.
     * Stores the normalized theta value along the arc from first->last.
     */
    protected static class CircleNode {
        public final PlayerEditNode node;
        /**
         * Calculated (new) radian angle applied to the node.
         * Angle is relative to the pinned orientation.
         */
        public double angle;
        /** The up-orientation vector of the node relative to the circle orientation */
        public final Vector up;

        public CircleNode(PlayerEditNode node, double initialAngle, Vector up) {
            this.node = node;
            this.angle = initialAngle;
            this.up = up;
        }
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
        List<Vector2> points = new ArrayList<>(editedNodes.size());
        for (PlayerEditNode en : editedNodes) {
            Vector p = en.node.getPosition().clone().subtract(basis.centroid);
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

    private static double normalizeAngleDiff(double a) {
        while (a <= -Math.PI) a += 2.0 * Math.PI;
        while (a >   Math.PI) a -= 2.0 * Math.PI;
        return a;
    }
}
