package com.bergerkiller.bukkit.coasters.editor.manipulation.modes.circle;

import com.bergerkiller.bukkit.coasters.util.PlaneBasis;
import org.bukkit.util.Vector;

/**
 * Computes the theta value (0.0 to 1.0) of a node position around a bi-sector constrained circle.
 */
class NodeBiSectorThetaCalculator extends NodeAngleCalculator {
    public final double angleFirst;
    public final double arcAngle;
    public final double angleSide;

    public NodeBiSectorThetaCalculator(PlaneBasis basis, Circle2D circle, double angleFirst, double arcAngle, double angleSide) {
        super(basis, circle);
        this.angleFirst = angleFirst;
        this.arcAngle = arcAngle;
        this.angleSide = angleSide;
    }

    /**
     * Computes the theta value (0.0 to 1.0) of the given position around the bi-sector constrained circle.
     * This is a theta value along the arc of the existing circle.
     *
     * @param position Position to compute the theta for
     * @return Theta value (0.0 to 1.0)
     */
    public double computeTheta(Vector position) {
        double ang = computeAngle(position);
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

    /**
     * Computes the radian angle from the theta value (0.0 to 1.0) around the bi-sector constrained circle.
     *
     * @param theta Theta value (0.0 to 1.0)
     * @return Radian angle
     */
    public double computeAngleFromTheta(double theta) {
        return angleFirst + (angleSide * theta * arcAngle);
    }
}
