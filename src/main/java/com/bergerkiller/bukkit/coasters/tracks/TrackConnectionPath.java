package com.bergerkiller.bukkit.coasters.tracks;

import org.bukkit.util.Vector;

import static com.bergerkiller.bukkit.coasters.TCCoastersUtil.sumComponents;

import com.bergerkiller.bukkit.coasters.tracks.path.Bezier;
import com.bergerkiller.bukkit.coasters.tracks.path.EndPoint;
import com.bergerkiller.bukkit.coasters.tracks.path.Node;
import com.bergerkiller.bukkit.coasters.tracks.path.TrackConnectionPathImpl;
import com.bergerkiller.bukkit.coasters.tracks.path.ViewPointOption;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
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
        if (distance <= 0.0) {
            // Past start of path
            return 0.0;
        }

        Node root = Node.init(this, 0.0, 1.0);
        if (root.define(this, 1e-4, distance) < distance) {
            // Past end of path
            return 1.0;
        }

        // Walk down the produced node linked list and find the exact theta at distance
        Node current = root;
        double remaining = distance;
        while (current != null) {
            if (current.distanceToNext >= remaining) {
                // End reached. Interpolate positions using remaining distance
                double s = remaining / current.distanceToNext;
                return (1.0 - s) * current.theta + s * current.next.theta;
            }
            remaining -= current.distanceToNext;
            current = current.next;
        }

        // Past end of path
        return 1.0;
    }

    /**
     * Estimates the distance between two points to a high enough precision
     * 
     * @param t0 Start theta
     * @param t1 End theta
     * @return Estimated distance
     */
    default double computeDistance(double t0, double t1) {
        return Node.init(this, t0, t1).define(this, 1e-4, Double.MAX_VALUE);
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
        // Swap t0 and t1 to make sure t1 > t0
        // When this is not done the error could be negative if t0 > t1.
        // TODO: Make an ABS of the error at the end is easier.
        if (t0 > t1) {
            double t = t1;
            t1 = t0;
            t0 = t;
        }

        /*
         * ============================================
         * Some history of how this function came to be
         * ============================================
         * 
         * We started with a simple integrator of error compared to the linear line using getPosition(t).
         * This uses the 'distance of point to linear line' formula, hence the dot product.
         * {
         *     Vector p0 = this.getPosition(t0);
         *     Vector p1 = this.getPosition(t1);
         *     Vector delta = p1.clone().subtract(p0).normalize();
         *
         *     int n = 100;
         *     double error = 0.0;
         *     double incr = (t1-t0) / (double) n;
         *     double t = t0;
         *     for (int i = 0; i < n; i++, t += incr) {
         *         Vector p = this.getPosition(t).subtract(p0);
         *         double dot = p.dot(delta);
         *         double diffx = p.getX() - dot*delta.getX();
         *         double diffy = p.getY() - dot*delta.getY();
         *         double diffz = p.getZ() - dot*delta.getZ();
         *         error += (diffx*diffx)+(diffy*diffy)+(diffz*diffz);
         *     }
         *     error /= n;
         *     return error;
         * }
         * 
         * Note the equations:
         *   fpA =  2t^3 - 3t^2 + 1
         *   fpB = -2t^3 + 3t^2
         *   fdA =  3t^3 - 6t^2 + 3t
         *   fdB = -3t^3 + 3t^2
         * 
         * Next getPosition(t) was taken apart, since the modifiers in bezier() don't change.
         * The inputs for the bezier() are fully expanded.
         * The subtraction of p0 is replaced with the subtraction of the inputs of p0's bezier().
         * {
         *     double t02 = t0*t0;
         *     double t03 = t02*t0;
         * 
         *     double t0_fpB = -2.0 * t03 + 3.0 * t02;
         *     double t0_fpA = -t0_fpB + 1.0;
         *     double t0_fdB = t0_fpB - t03;
         *     double t0_fdA = -t0_fpB + t03 + 3.0 * (t0 - t02);
         * 
         *     Vector p0 = bezier(t0_fpA, t0_fpB, t0_fdA, t0_fdB);
         *     Vector p1 = this.getPosition(t1);
         *     Vector delta = p1.clone().subtract(p0).normalize();
         *     
         *     int n = 100;
         *     double error = 0.0;
         *     double incr = (t1-t0) / (double) n;
         *     double t = t0;
         *     for (int i = 0; i < n; i++, t += incr) {
         *         double t2 = t*t;
         *         double t3 = t2*t;
         * 
         *         double fpA = 2.0 * t3 - 3.0 * t2 + 1.0;
         *         double fpB = -2.0 * t3 + 3.0 * t2;
         *         double fdA = 3.0 * t3 - 6.0 * t2 + 3.0 * t;
         *         double fdB = -3.0 * t3 + 3.0 * t2;
         * 
         *         Vector p = bezier(fpA-t0_fpA, fpB-t0_fpB, fdA-t0_fdA, fdB-t0_fdB);
         *         double dot = p.dot(delta);
         *         double diffx = p.getX() - dot*delta.getX();
         *         double diffy = p.getY() - dot*delta.getY();
         *         double diffz = p.getZ() - dot*delta.getZ();
         *         error += (diffx*diffx)+(diffy*diffy)+(diffz*diffz);
         *     }
         *     error /= n;
         *     return error;
         * }
         * 
         * Next we could clean it up by precomputing the bezier constants and using those instead:
         * {
         *     (...)
         * 
         *     Vector pA = this.endA.getPosition();
         *     Vector pB = this.endB.getPosition();
         *     Vector dA = this.endA.getDirection();
         *     Vector dB = this.endB.getDirection();
         *     Vector pA_f = pA.clone().subtract(delta.clone().multiply(pA.dot(delta)));
         *     Vector pB_f = pB.clone().subtract(delta.clone().multiply(pB.dot(delta)));
         *     Vector dA_f = dA.clone().subtract(delta.clone().multiply(dA.dot(delta))).multiply(this.endA.getDistance());
         *     Vector dB_f = dB.clone().subtract(delta.clone().multiply(dB.dot(delta))).multiply(this.endB.getDistance());
         * 
         *     (...)
         * 
         *     for (...) {
         *         (...)
         * 
         *         double fdB = -3.0 * t3 + 3.0 * t2;
         * 
         *         double diffx = (fpA*pA_f.getX() + fpB*pB_f.getX() + fdA*dA_f.getX() + fdB*dB_f.getX());
         *         double diffy = (fpA*pA_f.getY() + fpB*pB_f.getY() + fdA*dA_f.getY() + fdB*dB_f.getY());
         *         double diffz = (fpA*pA_f.getZ() + fpB*pB_f.getZ() + fdA*dA_f.getZ() + fdB*dB_f.getZ());
         *         error += (diffx*diffx)+(diffy*diffy)+(diffz*diffz);
         *     }
         *     (...)
         * }
         * 
         * Then came the tedious task of fully expanding the squaring of diffx/diffy/diffz.
         * Many additional constants (e.g. pA_f*pA_f) were added to simplify it.
         * Eventually you end with a sum of a whole lot of equations.
         * We sum the input multiplier of t/t^2/t^3/etc. and multiply it with t/t^2/t^3/etc. at the end.
         * 
         * You can then find that the same multiplier groups are used for X/Y/Z coordinates.
         * This means you can sum the X/Y/Z multipliers together and only use a single group.
         */

        Bezier b0 = Bezier.create(this).setup_k1(t0);
        Bezier b1 = Bezier.create(this).setup_k1(t1);

        Vector delta = Bezier.compute(this.getEndA(), this.getEndB(), b1.fpA-b0.fpA, b1.fpB-b0.fpB, b1.fdA-b0.fdA, b1.fdB-b0.fdB, new Vector());
        double delta_NZ = MathUtil.getNormalizationFactor(delta);
        if (Double.isFinite(delta_NZ)) {
            delta.multiply(delta_NZ);
        } else {
            return 0.0; // Zero length path
        }

        EndPoint endA = this.getEndA();
        EndPoint endB = this.getEndB();

        Vector pA = endA.getPosition();
        Vector pB = endB.getPosition();
        Vector dA = endA.getDirection();
        Vector dB = endB.getDirection();

        Vector pA_f = pA.clone().subtract(delta.clone().multiply(pA.dot(delta)));
        Vector pB_f = pB.clone().subtract(delta.clone().multiply(pB.dot(delta)));
        Vector dA_f = dA.clone().subtract(delta.clone().multiply(dA.dot(delta))).multiply(endA.getStrength());
        Vector dB_f = dB.clone().subtract(delta.clone().multiply(dB.dot(delta))).multiply(endB.getStrength());
        Vector t0_pA_f = pA_f.clone().multiply(b0.fpA);
        Vector t0_pB_f = pB_f.clone().multiply(b0.fpB);
        Vector t0_dA_f = dA_f.clone().multiply(b0.fdA);
        Vector t0_dB_f = dB_f.clone().multiply(b0.fdB);
        Vector t0_f = t0_pA_f.clone().add(t0_pB_f).add(t0_dA_f).add(t0_dB_f);

        double pA_f2 = sumComponents(pA_f.clone().multiply(pA_f));
        double pB_f2 = sumComponents(pB_f.clone().multiply(pB_f));
        double dA_f2 = sumComponents(dA_f.clone().multiply(dA_f));
        double dB_f2 = sumComponents(dB_f.clone().multiply(dB_f));
        double pB_pA_f2_m2 = sumComponents(pB_f.clone().multiply(pA_f));
        double dA_pA_f2_m2 = sumComponents(dA_f.clone().multiply(pA_f));
        double dB_pA_f2_m2 = sumComponents(dB_f.clone().multiply(pA_f));
        double dA_pB_f2_m2 = sumComponents(dA_f.clone().multiply(pB_f));
        double dB_pB_f2_m2 = sumComponents(dB_f.clone().multiply(pB_f));
        double dB_dA_f2_m2 = sumComponents(dB_f.clone().multiply(dA_f));
        double pA_t0_f = sumComponents(pA_f.clone().multiply(t0_f));
        double pB_t0_f = sumComponents(pB_f.clone().multiply(t0_f));
        double dA_t0_f = sumComponents(dA_f.clone().multiply(t0_f));
        double dB_t0_f = sumComponents(dB_f.clone().multiply(t0_f));

        double mx6 = 4.0 * (pA_f2 + pB_f2) +
                    -8.0 * (pB_pA_f2_m2) +
                     9.0 * (dA_f2 + dB_f2) +
                     12.0 * (dA_pA_f2_m2 - dA_pB_f2_m2 - dB_pA_f2_m2 + dB_pB_f2_m2) +
                     -18.0 * (dB_dA_f2_m2);

        double mx5 = -12.0 * (pA_f2 + pB_f2) +
                     -18.0 * dB_f2 +
                     24.0 * pB_pA_f2_m2 +
                     30.0 * (dB_pA_f2_m2 - dB_pB_f2_m2) +
                     -36.0 * dA_f2 +
                     42.0 * (dA_pB_f2_m2 - dA_pA_f2_m2) +
                     54.0 * dB_dA_f2_m2;

        double mx4 = 9.0 * (pA_f2 + pB_f2 + dB_f2) +
                     18.0 * (dB_pB_f2_m2 - dB_pA_f2_m2 - pB_pA_f2_m2) +
                     48.0 * (dA_pA_f2_m2 - dA_pB_f2_m2) +
                     54.0 * (dA_f2 - dB_dA_f2_m2);

        double mx3 = 4.0 * (pA_f2 - pB_pA_f2_m2 - pA_t0_f + pB_t0_f) +
                     6.0 * (dB_t0_f - dA_t0_f - dB_pA_f2_m2) +
                     -12.0 * dA_pA_f2_m2 +
                     18.0 * (dA_pB_f2_m2 + dB_dA_f2_m2)
                     -36.0 * dA_f2;

        double mx2 = 6.0 * (pB_pA_f2_m2 - pA_f2 + dB_pA_f2_m2 + pA_t0_f - pB_t0_f - dB_t0_f) +
                     9.0 * dA_f2 +
                     12.0 * (dA_t0_f - dA_pA_f2_m2);

        double mx1 = 6.0 * (dA_pA_f2_m2 - dA_t0_f);            
        double mx0 = t0_f.dot(t0_f) + pA_f2 - 2.0 * pA_t0_f;

        double error = 0.0;
        double t0_k = t0;
        double t1_k = t1;

        error += mx0 * (t1_k - t0_k);
        t0_k *= t0; t1_k *= t1;
        error += mx1 * (t1_k - t0_k) / 2.0;
        t0_k *= t0; t1_k *= t1;
        error += mx2 * (t1_k - t0_k) / 3.0;
        t0_k *= t0; t1_k *= t1;
        error += mx3 * (t1_k - t0_k) / 4.0;
        t0_k *= t0; t1_k *= t1;
        error += mx4 * (t1_k - t0_k) / 5.0;
        t0_k *= t0; t1_k *= t1;
        error += mx5 * (t1_k - t0_k) / 6.0;
        t0_k *= t0; t1_k *= t1;
        error += mx6 * (t1_k - t0_k) / 7.0;

        return error / ((t1 - t0) * (t1 - t0));
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
        return Bezier.create(this).setup_k2(t).compute();
    }

    /**
     * Calculates the position along this track at a particular t
     * 
     * @param t [0 ... 1]
     * @return position at t
     */    
    default Vector getPosition(double t) {
        return Bezier.create(this).setup_k1(t).compute();
    }

    /**
     * Calculates the position along this track at a particular t
     * 
     * @param t [0 ... 1]
     * @param out_pos Vector value which will be set to the position at t
     * @return out_pos
     */
    default Vector getPosition(double t, Vector out_pos) {
        return Bezier.create(this).setup_k1(t).compute(out_pos);
    }

    /**
     * Calculates the motion vector along this track at a particular t
     * 
     * @param t [0 ... 1]
     * @return motion vector at t
     */
    default Vector getMotionVector(double t) {
        // Derivative of getPosition(t)
        Vector motion = Bezier.create(this).setup_k0(t).compute();
        double motion_NZ = MathUtil.getNormalizationFactor(motion);
        if (Double.isFinite(motion_NZ)) {
            motion.multiply(motion_NZ);
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
