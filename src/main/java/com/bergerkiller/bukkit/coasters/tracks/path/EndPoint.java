package com.bergerkiller.bukkit.coasters.tracks.path;

import org.bukkit.util.Vector;

/**
 * An endpoint of a bezier curve, which computes and stores the
 * direction of the curve and the distance (weight) of the end.
 * Right now the weight for both ends are always equal.
 */
public abstract class EndPoint {
    private Vector direction = new Vector();
    private double strength = 0.0;

    // These must be implemented as input for the algorithm
    public abstract Vector getNodePosition();
    public abstract Vector getNodeDirection();
    public abstract Vector getOtherNodePosition();
    public abstract Vector getOtherNodeDirection();

    public void initAuto() {
        this.direction = getOtherNodePosition().clone().subtract(getNodePosition()).normalize();
        this.updateDistance();
    }

    public void initNormal() {
        this.direction = getNodeDirection();
        this.updateDistance();
    }

    public void initInverted() {
        this.direction = getNodeDirection().clone().multiply(-1.0);
        this.updateDistance();
    }

    private final void updateDistance() {
        this.strength = 0.5 * getNodePosition().distance(getOtherNodePosition());
        if (Double.isNaN(this.direction.getX())) {
            this.direction = new Vector(1.0, 0.0, 0.0);
        }
    }

    public final Vector getPosition() {
        return this.getNodePosition();
    }

    public final Vector getDirection() {
        return this.direction;
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

    public static EndPoint create(Vector nodePosition, Vector nodeDirection, Vector otherPosition, Vector otherDirection) {
        return new EndPointImpl(nodePosition, nodeDirection, otherPosition, otherDirection);
    }

    private static class EndPointImpl extends EndPoint {
        private final Vector _nodePosition;
        private final Vector _nodeDirection;
        private final Vector _otherPosition;
        private final Vector _otherDirection;

        public EndPointImpl(Vector nodePosition, Vector nodeDirection, Vector otherPosition, Vector otherDirection) {
            this._nodePosition = nodePosition;
            this._nodeDirection = nodeDirection.clone().normalize();
            this._otherPosition = otherPosition;
            this._otherDirection = otherDirection.clone().normalize();
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
        public Vector getOtherNodePosition() {
            return _otherPosition;
        }

        @Override
        public Vector getOtherNodeDirection() {
            return _otherDirection;
        }
    }
}
