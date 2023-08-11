package com.bergerkiller.bukkit.coasters.tracks.path;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Quaternion;

/**
 * An endpoint of a bezier curve, which computes and stores the
 * direction of the curve and the distance (weight) of the end.
 * Right now the weight for both ends are always equal.
 */
public abstract class EndPoint {
    private Vector direction = new Vector();
    private Quaternion orientation = new Quaternion();
    private double strength = 0.0;

    // These must be implemented as input for the algorithm
    public abstract Vector getNodePosition();
    public abstract Vector getNodeDirection();
    public abstract Vector getNodeUp();
    public abstract Vector getOtherNodePosition();
    public abstract Vector getOtherNodeDirection();

    public void initAuto() {
        this.direction = getOtherNodePosition().clone().subtract(getNodePosition());
        double distance = this.direction.length();
        if (distance > 1e-20) {
            // Non-zero distance
            this.direction.multiply(1.0 / distance);
        } else {
            // Zero distance, take average of both node directions
            this.direction = Quaternion.slerp(Quaternion.fromLookDirection(getNodeDirection()),
                                              Quaternion.fromLookDirection(getOtherNodeDirection()),
                                              0.5).forwardVector();
        }
        computeStrength(distance);
    }

    public void initNormal() {
        this.direction = getNodeDirection();
        computeStrengthUsingPositions();
    }

    public void initInverted() {
        this.direction = getNodeDirection().clone().multiply(-1.0);
        computeStrengthUsingPositions();
    }

    private final void computeStrength(double distance) {
        this.strength = 0.5 * distance;
        this.orientation = Quaternion.fromLookDirection(this.direction, this.getNodeUp());
    }

    protected final void computeStrengthUsingPositions() {
        computeStrength(getNodePosition().distance(getOtherNodePosition()));
    }

    public final Vector getPosition() {
        return this.getNodePosition();
    }

    public final Vector getDirection() {
        return this.direction;
    }

    public Quaternion getOrientation() {
        return this.orientation;
    }

    public void alignOrientationForward(Quaternion otherOrientation) {
        if (this.orientation.forwardVector().dot(otherOrientation.forwardVector()) < 0.0) {
            this.orientation.rotateYFlip();
        }
    }

    /**
     * Gets the distance between the two nodes
     * 
     * @return distance
     */
    public final double getDistance() {
        return 2.0 * this.strength;
    }

    /**
     * Gets the strength of the curve, based on distance between the nodes
     * 
     * @return curve strength
     */
    public final double getStrength() {
        return this.strength;
    }

    /**
     * Gets whether the connection between this end-point and the other one is zero-length.
     *
     * @return True if zero length
     */
    public final boolean isZeroLength() {
        return this.strength < 1e-20;
    }

    public static EndPoint create(Vector nodePosition, Vector nodeDirection, Vector otherPosition, Vector otherDirection) {
        return new EndPointImpl(nodePosition, nodeDirection, new Vector(0.0, 1.0, 0.0), otherPosition, otherDirection);
    }

    public static EndPoint create(Vector nodePosition, Vector nodeDirection, Vector nodeUp, Vector otherPosition, Vector otherDirection) {
        return new EndPointImpl(nodePosition, nodeDirection, nodeUp, otherPosition, otherDirection);
    }

    private static class EndPointImpl extends EndPoint {
        private final Vector _nodePosition;
        private final Vector _nodeDirection;
        private final Vector _nodeUp;
        private final Vector _otherPosition;
        private final Vector _otherDirection;

        public EndPointImpl(Vector nodePosition, Vector nodeDirection, Vector nodeUp, Vector otherPosition, Vector otherDirection) {
            this._nodePosition = nodePosition;
            this._nodeDirection = nodeDirection.clone().normalize();
            this._nodeUp = nodeUp;
            this._otherPosition = otherPosition;
            this._otherDirection = otherDirection.clone().normalize();
            this.computeStrengthUsingPositions(); // Positions known, can now be called
        }

        @Override
        public Vector getNodePosition() {
            return _nodePosition;
        }

        @Override
        public Vector getNodeDirection() {
            return _nodeDirection;
        }

        @Override
        public Vector getNodeUp() {
            return _nodeUp;
        }

        @Override
        public Vector getOtherNodePosition() {
            return _otherPosition;
        }

        @Override
        public Vector getOtherNodeDirection() {
            return _otherDirection;
        }
    }
}
