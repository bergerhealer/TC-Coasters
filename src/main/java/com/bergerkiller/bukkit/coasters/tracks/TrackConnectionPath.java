package com.bergerkiller.bukkit.coasters.tracks;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.tracks.path.Bezier;
import com.bergerkiller.bukkit.coasters.tracks.path.Discrete;
import com.bergerkiller.bukkit.coasters.tracks.path.EndPoint;
import com.bergerkiller.bukkit.coasters.tracks.path.TrackConnectionPathImpl;
import com.bergerkiller.bukkit.coasters.tracks.path.ViewPointOption;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.MathUtil;

/**
 * Helper object for computing track path information
 */
public interface TrackConnectionPath {

    /**
     * Gets the bezier path start endpoint
     * 
     * @return end point A
     */
    EndPoint getEndA();

    /**
     * Gets the bezier path end endpoint
     * 
     * @return end point B
     */
    EndPoint getEndB();

    /**
     * Walks this path from the start a distance towards the end, calculating the theta
     * value of the point found a distance away.
     * 
     * @param distance The distance to walk from the start
     * @return theta value of the point at the distance
     */
    default double findPointThetaAtDistance(double distance) {
        return Bezier.create(this).invThetaFromArcLength(0.0, 1.0, distance);
    }

    /**
     * Walks this path from the start a distance towards the end, calculating the theta
     * value of the point found a distance away.
     * This is less efficient than the bezier invThetaFromArcLength method, and is here for
     * testing and comparison purposes.
     *
     * @param distance The distance to walk from the start
     * @return theta value of the point at the distance
     */
    default double findPointThetaAtDistanceDiscrete(double distance) {
        return distance <= 0.0 ? 0.0 : Discrete.INSTANCE.init(this).findTheta(distance);
    }

    /**
     * Estimates the distance between two points to a high enough precision
     * 
     * @param t0 Start theta
     * @param t1 End theta
     * @return Estimated distance
     */
    default double computeDistance(double t0, double t1) {
        return Bezier.create(this).arcLength(t0, t1);
    }

    /**
     * Computes the distance between two points by discretizing the path into small segments and summing their lengths.
     * This is less efficient than the bezier arc length method, and is here for testing and comparison purposes.
     *
     * @param t0 Start theta
     * @param t1 End theta
     * @return Estimated distance
     */
    default double computeDistanceDiscrete(double t0, double t1) {
        return Discrete.INSTANCE.init(this, t0, t1).getTotalDistance();
    }

    /**
     * Searches points on the path of this connection for the point closest to a player's view point.
     * 
     * @param playerViewMatrixInverted The view matrix, inverted, for computing with
     * @param t0 Start theta
     * @param t1 End theta
     * @return theta value that lies closest
     */
    default double findClosestPointInView(Matrix4x4 playerViewMatrixInverted, double t0, double t1) {
        // Create 5 view point options which are linked together
        ViewPointOption head = new ViewPointOption(null, 5);
        ViewPointOption tail = head.next.next.next.next;

        // Initialize head and tail
        head.update(this, playerViewMatrixInverted, t0);
        tail.update(this, playerViewMatrixInverted, t1);

        return ViewPointOption.findClosestPointOptionInView(this, playerViewMatrixInverted, head, ViewPointOption.NONE).theta;
    }

    /**
     * Gets the error between comparing a linear line from t=t0 to t=t1, and the same
     * continuous line of the track as computed here. The error is computed by taking
     * the average distance between the two lines squared.
     * 
     * @param t0
     * @param t1
     * @return linear error
     */
    default double getLinearError(double t0, double t1) {
        return Bezier.create(this).getLinearError(t0, t1);
    }

    /**
     * Calculates the absition (area below the position curve from t=0 to t).
     * This property is used to compute the area difference between two 3D curves.
     * 
     * @param t [0 ... 1]
     * @return absition at t
     */
    default Vector getAbsition(double t) {
        // Primitive of getPosition(t)
        return Bezier.create(this).getAbsition(t, new Vector());
    }

    /**
     * Calculates the position along this track at a particular t
     * 
     * @param t [0 ... 1]
     * @return position at t
     */    
    default Vector getPosition(double t) {
        return Bezier.create(this).getPosition(t, new Vector());
    }

    /**
     * Calculates the position along this track at a particular t
     * 
     * @param t [0 ... 1]
     * @param out_pos Vector value which will be set to the position at t
     * @return out_pos
     */
    default Vector getPosition(double t, Vector out_pos) {
        return Bezier.create(this).getPosition(t, out_pos);
    }

    /**
     * Calculates the motion vector along this track at a particular t
     * 
     * @param t [0 ... 1]
     * @return motion vector at t
     */
    default Vector getMotionVector(double t) {
        // Derivative of getPosition(t)
        Vector motion = Bezier.create(this).getMotionVector(t, new Vector());
        double motion_NZ = MathUtil.getNormalizationFactor(motion);
        if (Double.isFinite(motion_NZ)) {
            motion.multiply(motion_NZ);
        } else {
            return Quaternion.slerp(
                    Quaternion.fromLookDirection(this.getEndA().getDirection()),
                    Quaternion.fromLookDirection(this.getEndB().getDirection()),
                    t).forwardVector();
        }
        return motion;
    }

    /**
     * Creates a track connection path with the given bezier curve end point positions and directions
     * 
     * @param endA_position
     * @param endA_direction
     * @param endB_position
     * @param endB_direction
     * @return path
     */
    public static TrackConnectionPath create(Vector endA_position, Vector endA_direction, Vector endB_position, Vector endB_direction) {
        return new TrackConnectionPathImpl(
                EndPoint.create(endA_position, endA_direction, endB_position, endB_direction),
                EndPoint.create(endB_position, endB_direction, endA_position, endA_direction));
    }
}
