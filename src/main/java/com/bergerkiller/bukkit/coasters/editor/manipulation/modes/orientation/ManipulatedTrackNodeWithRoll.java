package com.bergerkiller.bukkit.coasters.editor.manipulation.modes.orientation;

import com.bergerkiller.bukkit.coasters.editor.manipulation.ManipulatedTrackNode;
import com.bergerkiller.bukkit.coasters.util.PlaneBasis;
import com.bergerkiller.bukkit.common.math.Quaternion;
import org.bukkit.util.Vector;

/**
 * A manipulated track node on a circle, tracking angle values
 * around the circle for position updates. This node can be on any of the
 * 360 degrees of the circle and is not constrained by some bi-sector.
 */
class ManipulatedTrackNodeWithRoll extends ManipulatedTrackNode {
    /** Plane basis puts the "interesting" 2d bits at x/y. So we want a forward vector along +X. */
    private static final Vector BASE_FORWARD = new Vector(1.0, 0.0, 0.0);

    /** Theta position of the node along the chain (0 = first, 1 = last node) */
    public double theta;
    /** Forward direction vector from this node to the next one. Roll is applied around this direction. */
    public Vector direction;
    /** Computed roll value of the node around the direction. Relative to the base orientation passed in. */
    public double roll;

    public ManipulatedTrackNodeWithRoll(ManipulatedTrackNode draggedNode) {
        super(draggedNode);
    }

    public void setDirection(Vector direction) {
        this.direction = direction;
    }

    public void computeAndSetRoll(PlaneBasis planeBasis) {
        // Build an orientation that faces along `direction`, using the node's orientation
        Quaternion orientation = Quaternion.fromLookDirection(this.direction, planeBasis.toPlane(node.getOrientation()));

        // The Z component of the up vector is now 0.0. Compute the roll angle from the X/Y components of the up vector.
        Vector up = rotateForwardInto(orientation, BASE_FORWARD).upVector();
        this.roll = Math.toDegrees(Math.atan2(up.getY(), up.getZ()));
    }

    public void applyRoll(PlaneBasis planeBasis) {
        // Compute the relative orientation along the forward direction, using the roll value.
        // This is the orientation that would be applied if the forward direction was (0,0,1).
        Vector relativeUp = new Vector(0.0, Math.sin(Math.toRadians(roll)), Math.cos(Math.toRadians(roll)));
        Quaternion relativeOrientation = Quaternion.fromLookDirection(BASE_FORWARD, relativeUp);

        Quaternion absoluteOrientation = rotateForwardInto(relativeOrientation, this.direction);
        this.setOrientation(planeBasis.fromPlane(absoluteOrientation.upVector()));
    }

    /**
     * Rotates the given orientation so that its forward vector points into the target direction.
     * This is different from Quaternion fromToRotation, which does not guarantee the forward/right vectors
     * stay consistent.
     *
     * @param q Quaternion
     * @param forwardVector Normalized target forward direction to rotate into
     * @return Rotated quaternion
     */
    public static Quaternion rotateForwardInto(Quaternion q, Vector forwardVector) {
        Vector fwd = q.forwardVector();

        // If vectors are already aligned, return original
        double dot = fwd.dot(forwardVector);
        if (dot > 0.999999) {
            return q;
        }

        // If vectors are opposite, rotate 180° around any perpendicular axis
        if (dot < -0.999999) {
            // Pick an axis perpendicular to forward
            Vector axis = fwd.getCrossProduct(new Vector(1, 0, 0));
            if (axis.lengthSquared() < 1e-6f) {
                axis = fwd.getCrossProduct(new Vector(0, 1, 0));
            }
            axis.normalize();
            Quaternion halfTurn = Quaternion.fromAxisAngles(axis, 180.0);
            halfTurn.multiply(q);
            return halfTurn;
        }

        // Minimal rotation axis
        Vector axis = fwd.getCrossProduct(forwardVector);
        axis.normalize();

        // Build quaternion that rotates forward into target
        Quaternion rot = Quaternion.fromAxisAngles(axis, Math.toDegrees(Math.acos(dot)));

        // Apply relative to original orientation
        rot.multiply(q);
        return rot;
    }
}
