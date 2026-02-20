package com.bergerkiller.bukkit.coasters.editor.manipulation.modes.circle;

import com.bergerkiller.bukkit.coasters.util.PlaneBasis;
import com.bergerkiller.bukkit.common.math.Quaternion;
import org.bukkit.util.Vector;

/**
 * Computes the angle of a node position around a 2D circle and a
 * circle plane basis to project the circle onto.
 */
class NodeAngleCalculator {
    public final PlaneBasis basis;
    public final Circle2D circle;

    public NodeAngleCalculator(PlaneBasis basis, Circle2D circle) {
        this.basis = basis;
        this.circle = circle;
    }

    /**
     * Computes the angle of the given position around the circle.
     *
     * @param position Position to compute the angle for
     * @return Angle in radians
     */
    public double computeAngle(Vector position) {
        Vector pv = position.clone().subtract(basis.centroid);
        double px = pv.dot(basis.ex), py = pv.dot(basis.ey);
        return Math.atan2(py - circle.cy, px - circle.cx);
    }

    /**
     * Computes the 3D point on the circle at the given angle.
     *
     * @param angle Angle in radians
     * @return 3D point on the circle
     */
    public Vector computePointAtAngle(double angle) {
        double x = circle.cx + circle.r * Math.cos(angle);
        double y = circle.cy + circle.r * Math.sin(angle);
        return basis.centroid.clone()
                .add(basis.ex.clone().multiply(x))
                .add(basis.ey.clone().multiply(y));
    }

    /**
     * Computes the orientation quaternion at the given angle, facing
     * along the tangent of the circle.
     *
     * @param angle Angle in radians
     * @return Orientation quaternion
     */
    public Quaternion computeTangentOrientation(double angle) {
        // Tangent vector in 2D
        double tx = -Math.sin(angle);
        double ty = Math.cos(angle);

        // Convert to 3D
        Vector tangent = basis.ex.clone().multiply(tx).add(basis.ey.clone().multiply(ty)).normalize();

        // Create orientation quaternion from tangent
        return Quaternion.fromLookDirection(tangent, basis.normal);
    }
}
