package com.bergerkiller.bukkit.coasters.editor.manipulation.modes.circle;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditNode;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChangeCollection;
import com.bergerkiller.bukkit.coasters.editor.manipulation.NodeDragEvent;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.common.math.Quaternion;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NodeDragManipulatorCircleFitFallback extends NodeDragManipulatorCircleFit {
    // Drag position changes radius
    private Vector dragStartPos;
    // Result of the fit
    private Vector circleCenter3D;
    private Vector circleNormal;
    private double circleRadius;

    public NodeDragManipulatorCircleFitFallback(PlayerEditState state, Map<TrackNode, PlayerEditNode> editedNodes) {
        super(state, editedNodes);
    }

    @Override
    public void onStarted(NodeDragEvent event) {
        /*
        // Evaluate the edited nodes to see how the circle is defined
        // Try to find nodes which act as an anchor (not moved) because they have connections to non-selected nodes
        // These nodes are only moved when the player specifically clicks and drags these nodes.
        Set<TrackNode> nodes = editedNodes.stream()
                .map(n -> n.node)
                .collect(Collectors.toSet());
        Set<TrackNode> anchorNodes = nodes.stream()
                .filter(n -> n.getNeighbours().stream().anyMatch(c -> !nodes.contains(c)))
                .collect(Collectors.toSet());

        System.out.println(anchorNodes.stream().map(n -> n.getPosition().toString()).collect(Collectors.joining(", ")));

         */


        dragStartPos = event.current().toVector();

        if (editedNodes.isEmpty()) return;

        // Compute average up-vector of all edited nodes
        // This is used to flip the circle normal vector so that "up" is correct
        Vector upOrientation = Quaternion.average(
                () -> editedNodes.stream()
                        .map(n -> Quaternion.fromLookDirection(n.node.getDirection(), n.node.getOrientation()))
                        .iterator()
        ).upVector();

        // Collect points
        List<Vector> points = new ArrayList<>(editedNodes.size());
        for (PlayerEditNode n : editedNodes) {
            points.add(n.node.getPosition().clone());
        }

        // Estimate plane basis (centroid, ex, ey, normal)
        PlaneBasis basis = PlaneBasis.estimateFromPoints(points, upOrientation);

        // Project points to 2D coordinates in the plane
        int m = points.size();
        double[] xs = new double[m];
        double[] ys = new double[m];
        for (int i = 0; i < m; i++) {
            Vector d = points.get(i).clone().subtract(basis.centroid);
            xs[i] = d.dot(basis.ex);
            ys[i] = d.dot(basis.ey);
        }

        // Fit circle in 2D (Taubin-style algebraic fit with centroid shift)
        Circle2D circle2D = fitCircleTaubinLike(xs, ys);

        // Convert center back to 3D and store results
        this.circleCenter3D = basis.centroid.clone()
                .add(basis.ex.clone().multiply(circle2D.cx))
                .add(basis.ey.clone().multiply(circle2D.cy));
        this.circleNormal = basis.normal.clone();
        this.circleRadius = circle2D.r;

        // Compute and store a single angle per node representing its position on the 2D circle
        for (PlayerEditNode editNode : editedNodes) {
            Vector dir = editNode.node.getPosition().clone().subtract(this.circleCenter3D);
            if (dir.lengthSquared() < 1e-12) {
                // fallback: pick any tangent direction perpendicular to the normal
                dir = perpendicular(this.circleNormal != null ? this.circleNormal : new Vector(0, 1, 0));
            }

            // remove any component along the circle normal so dir lies in the circle plane
            double along = dir.dot(this.circleNormal);
            dir = dir.clone().subtract(this.circleNormal.clone().multiply(along));

            dir.normalize();

            editNode.dragCircleFitDirection = Quaternion.fromLookDirection(dir, this.circleNormal);
            Vector up = this.circleNormal.clone().normalize();
        }

        System.out.println("Circle center: " + this.circleCenter3D);
        System.out.println("Circle normal" + this.circleNormal);
        System.out.println("Circle radius: " + this.circleRadius);

        //TODO: Implement angle calculations here
    }

    @Override
    public void onUpdate(NodeDragEvent event) {
        /*
        Vector tmp = circleNormal.clone().multiply(circleRadius);
        event.change().transformPoint(tmp);
        circleRadius = tmp.length();

        for (PlayerEditNode editNode : editedNodes) {
            Vector p = circlePlaneTo3D(editNode.dragCircleFitDirection);
            Util.spawnDustParticle(p.toLocation(state.getBukkitWorld()), Color.RED);
            editNode.node.setPosition(p);
        }

         */

        // Intentionally not implemented yet
    }

    @Override
    public void onFinished(HistoryChangeCollection history, NodeDragEvent event) throws ChangeCancelledException {
        // Merge/record behavior can be copied from other manipulators when implementing finish behavior.
        // For now, do nothing special.
        recordEditedNodesInHistory(history);
    }

    private Vector circlePlaneTo3D(Quaternion dragCircleFitDirection) {
        return dragCircleFitDirection.forwardVector().multiply(circleRadius).add(circleCenter3D);
    }
}
