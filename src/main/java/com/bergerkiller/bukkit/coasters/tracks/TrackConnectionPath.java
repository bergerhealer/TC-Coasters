package com.bergerkiller.bukkit.coasters.tracks;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.TCCoastersUtil;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.MathUtil;

/**
 * Helper object for computing track path information
 */
public class TrackConnectionPath {
    public final EndPoint endA;
    public final EndPoint endB;

    public TrackConnectionPath(EndPoint endA, EndPoint endB) {
        this.endA = endA;
        this.endB = endB;
    }

    // Currently only used under test
    public static TrackConnectionPath create(Vector endA_position, Vector endA_direction, Vector endB_position, Vector endB_direction) {
        return new TrackConnectionPath(
                EndPoint.create(endA_position, endA_direction, endB_position, endB_direction),
                EndPoint.create(endB_position, endB_direction, endA_position, endA_direction));
    }

    private static double sumComponents(Vector v) {
        return v.getX() + v.getY() + v.getZ();
    }

    /**
     * Walks this path from the start a distance towards the end, calculating the theta
     * value of the point found a distance away.
     * 
     * @param distance The distance to walk from the start
     * @return theta value of the point at the distance
     */
    public double findPointThetaAtDistance(double distance) {
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
        while (true) {
            if (current.distanceToNext >= remaining) {
                // End reached. Interpolate positions using remaining distance
                double s = remaining / current.distanceToNext;
                return (1.0 - s) * current.theta + s * current.next.theta;
            }
            remaining -= current.distanceToNext;
            current = current.next;
        }
    }

    /**
     * Estimates the distance between two points to a high enough precision
     * 
     * @param t0 Start theta
     * @param t1 End theta
     * @return Estimated distance
     */
    public double computeDistance(double t0, double t1) {
        return Node.init(this, t0, t1).define(this, 1e-4, Double.MAX_VALUE);
    }

    /**
     * A single node on a discrete version of this connection path.
     * Only one discrete path can exist at one time, this memory is cached and reused.
     */
    private static class Node {
        private static Node[] node_cache = new Node[] { new Node(), new Node() };
        private static final AtomicInteger node_index = new AtomicInteger(0);
        public Node next;
        public final Vector position;
        public double theta;
        public double distanceToNext;

        private Node() {
            this.position = new Vector();
        }

        /**
         * Inserts nodes between this node and this node's end of the chain until the
         * difference falls below the precision specified. The path is defined from start to finish,
         * with max_distance used to abort path construction early on.
         * 
         * @param path The path used to calculate the node positions
         * @param precision The precision at which to subdivide the path
         * @param max_distance The maximum distance above which to stop defining further
         * @return total distance of the computed path
         */
        public double define(TrackConnectionPath path, double precision, double max_distance) {
            // Process in a loop
            double totalDistance = 0.0;
            Node current = this;
            while (true) {
                // Insert two nodes between current and current's next node
                // Keep the original distance to next, we need it for termination
                double currentNextDistance = current.distanceToNext;
                double t0 = current.theta;
                double t1 = current.next.theta;
                double ht = 0.5 * (t0 + t1);

                // Insert three nodes between current and next and compute their distances
                // We insert three so we can avoid the S-curve trap that can occur with a quadratic bezier curve
                // We don't insert two because the theta values are slow in binary (0.333...) (0.5* is fast!)
                current.insertThree();

                // Compute positions and distances between the creates nodes
                Node m0 = current.next;
                Node m1 = m0.next;
                Node m2 = m1.next;
                m0.computePosition(path, 0.5 * (t0 + ht));
                m1.computePosition(path, ht);
                m2.computePosition(path, 0.5 * (t1 + ht));
                double summed = current.computeDistanceToNext() +
                                m0.computeDistanceToNext() +
                                m1.computeDistanceToNext() +
                                m2.computeDistanceToNext();
     
                // If distance difference is small enough, then this section is complete
                if ((summed - currentNextDistance) < precision) {
                    totalDistance += summed;
                    current = m2.next;
                    if (current.next == null || totalDistance > max_distance) {
                        break;
                    }
                }
            }
            return totalDistance;
        }

        public void computePosition(TrackConnectionPath path, double theta) {
            this.theta = theta;
            path.getPosition(theta, this.position);
        }

        public double computeDistanceToNext() {
            return this.distanceToNext = this.position.distance(this.next.position);
        }

        /**
         * Inserts tree more nodes between this node and this node's next node.
         */
        public void insertThree() {
            int index = node_index.getAndAdd(3);
            Node last;

            // Get the last node. (Ab)use out of bounds exception to resize the cache if needed
            try {
                last = node_cache[index + 2];
            } catch (ArrayIndexOutOfBoundsException ex) {
                reserve();
                last = node_cache[index + 2];
            }

            last.next = this.next;
            this.next = node_cache[index];
            this.next.next = node_cache[index + 1];
            this.next.next.next = last;
        }

        /**
         * Resets the node cache and initializes it with the start and end nodes of the path
         * to compute.
         * 
         * @param path
         * @param t0
         * @param t1
         * @return root node
         */
        public static Node init(TrackConnectionPath path, double t0, double t1) {
            node_index.set(2);
            Node root = node_cache[0];
            root.next = node_cache[1];
            root.next.next = null;
            root.computePosition(path, t0);
            root.next.computePosition(path, t1);
            root.computeDistanceToNext();
            return root;
        }

        // Doubles the capacity of the cache, with a size of at least 512
        private static void reserve() {
            int old_size = node_cache.length;
            int new_size = Math.max(512, old_size * 2);
            node_cache = Arrays.copyOf(node_cache, new_size);
            for (int i = old_size; i < new_size; i++) {
                node_cache[i] = new Node();
            }
        }
    }

    /**
     * Searches points on the path of this connection for the point closest to a player's view point.
     * 
     * @param playerViewMatrixInverted The view matrix, inverted, for computing with
     * @param t0 Start theta
     * @param t1 End theta
     * @return theta value that lies closest
     */
    public double findClosestPointInView(Matrix4x4 playerViewMatrixInverted, final double t0, final double t1) {
        // Create 5 view point options which are linked together
        ViewPointOption head = new ViewPointOption(null, 5);
        ViewPointOption m0 = head.next;
        ViewPointOption m1 = m0.next;
        ViewPointOption m2 = m1.next;
        ViewPointOption tail = m2.next;

        // Set head and tail to t0 and t1 respectively
        head.update(this, playerViewMatrixInverted, t0);
        tail.update(this, playerViewMatrixInverted, t1);

        while (true) {
            // Calculate middle 3 points using head and tail theta values
            double ht = 0.5 * (head.theta + tail.theta);
            m0.update(this, playerViewMatrixInverted, 0.5 * (head.theta + ht));
            m1.update(this, playerViewMatrixInverted, ht);
            m2.update(this, playerViewMatrixInverted, 0.5 * (tail.theta + ht));

            // Find best view point option with lowest distance from where the player is looking
            ViewPointOption best = head;
            for (ViewPointOption opt = m0; opt != null; opt = opt.next) {
                if (opt.distance < best.distance) {
                    best = opt;
                }
            }

            // Get the view point option directly neighbouring the selected one
            // At head/tail there is only one option, otherwise lowest distance counts
            ViewPointOption other;
            if (best.prev == null || (best.next != null && best.next.distance < best.prev.distance)) {
                other = best.next;
            } else {
                other = best.prev;
            }

            // If difference in distance is negligible, return the best result
            if ((other.distance - best.distance) < 1e-5) {
                return best.theta;
            }

            // Copy best and other to head/tail in whatever way works best and continue looking
            if (best == head) {
                tail.assign(other);
            } else if (best == tail) {
                head.assign(other);
            } else if (other == head) {
                tail.assign(best);
            } else if (other == tail) {
                head.assign(best);
            } else {
                head.assign(best);
                tail.assign(other);
            }
        }
    }

    // Helper class for sorting the different points on the path
    private static final class ViewPointOption {
        public final ViewPointOption prev, next;
        private final Vector position = new Vector();
        public double theta = 0.0;
        public double distance;

        public ViewPointOption(ViewPointOption prev, int len) {
            this.prev = prev;
            this.next = (len <= 1) ? null : new ViewPointOption(this, len-1);
        }

        public void update(TrackConnectionPath path, Matrix4x4 playerViewMatrixInverted, double theta) {
            this.theta = theta;
            path.getPosition(theta, this.position);
            playerViewMatrixInverted.transformPoint(this.position);
            this.distance = TCCoastersUtil.distanceSquaredXY(this.position);
        }

        public void assign(ViewPointOption opt) {
            this.theta = opt.theta;
            this.distance = opt.distance;
        }
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
    public double getLinearError(double t0, double t1) {
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

        BezierInput b0 = new BezierInput().setup_k1(t0);
        BezierInput b1 = new BezierInput().setup_k1(t1);

        Vector delta = bezier(b1.fpA-b0.fpA, b1.fpB-b0.fpB, b1.fdA-b0.fdA, b1.fdB-b0.fdB);
        double delta_NZ = MathUtil.getNormalizationFactor(delta);
        if (Double.isFinite(delta_NZ)) {
            delta.multiply(delta_NZ);
        } else {
            return 0.0; // Zero length path
        }

        Vector pA = this.endA.getPosition();
        Vector pB = this.endB.getPosition();
        Vector dA = this.endA.getDirection();
        Vector dB = this.endB.getDirection();

        Vector pA_f = pA.clone().subtract(delta.clone().multiply(pA.dot(delta)));
        Vector pB_f = pB.clone().subtract(delta.clone().multiply(pB.dot(delta)));
        Vector dA_f = dA.clone().subtract(delta.clone().multiply(dA.dot(delta))).multiply(this.endA.getDistance());
        Vector dB_f = dB.clone().subtract(delta.clone().multiply(dB.dot(delta))).multiply(this.endB.getDistance());
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
    public Vector getAbsition(double t) {
        // Primitive of getPosition(t)
        return bezier(new BezierInput().setup_k2(t));
    }

    /**
     * Calculates the position along this track at a particular t
     * 
     * @param t [0 ... 1]
     * @return position at t
     */    
    public Vector getPosition(double t) {
        return bezier(new BezierInput().setup_k1(t));
    }

    /**
     * Calculates the position along this track at a particular t
     * 
     * @param t [0 ... 1]
     * @param out_pos Vector value which will be set to the position at t
     * @return out_pos
     */
    public Vector getPosition(double t, Vector out_pos) {
        return bezier(new BezierInput().setup_k1(t), out_pos);
    }

    /**
     * Calculates the motion vector along this track at a particular t
     * 
     * @param t [0 ... 1]
     * @return motion vector at t
     */
    public Vector getMotionVector(double t) {
        // Derivative of getPosition(t)
        Vector motion = bezier(new BezierInput().setup_k0(t));
        double motion_NZ = MathUtil.getNormalizationFactor(motion);
        if (Double.isFinite(motion_NZ)) {
            motion.multiply(motion_NZ);
        }
        return motion;
    }

    private Vector bezier(BezierInput bezier) {
        return bezier(bezier, new Vector());
    }

    private Vector bezier(BezierInput bezier, Vector out_vec) {
        return bezier(bezier.fpA, bezier.fpB, bezier.fdA, bezier.fdB, out_vec);
    }

    private Vector bezier(double fpA, double fpB, double fdA, double fdB) {
        return bezier(fpA, fpB, fdA, fdB, new Vector());
    }

    private Vector bezier(double fpA, double fpB, double fdA, double fdB, Vector out_vec) {
        double pfdA = fdA * this.endA.getDistance();
        double pfdB = fdB * this.endB.getDistance();
        Vector pA = this.endA.getPosition();
        Vector pB = this.endB.getPosition();
        Vector dA = this.endA.getDirection();
        Vector dB = this.endB.getDirection();
        out_vec.setX(fpA*pA.getX() + fpB*pB.getX() + pfdA*dA.getX() + pfdB*dB.getX());
        out_vec.setY(fpA*pA.getY() + fpB*pB.getY() + pfdA*dA.getY() + pfdB*dB.getY());
        out_vec.setZ(fpA*pA.getZ() + fpB*pB.getZ() + pfdA*dA.getZ() + pfdB*dB.getZ());
        return out_vec;
    }

    /**
     * An endpoint of a bezier curve, which computes and stores the
     * direction of the curve and the distance (weight) of the end.
     * Right now the weight for both ends are always equal.
     */
    public static abstract class EndPoint {
        private Vector direction = new Vector();
        private double distance = 0.0;

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
            this.distance = 0.5 * getNodePosition().distance(getOtherNodePosition());
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
         * Gets half the distance between the node of this end point and the other node
         * 
         * @return half distance
         */
        public final double getDistance() {
            return this.distance;
        }

        public static EndPoint create(Vector nodePosition, Vector nodeDirection, Vector otherPosition, Vector otherDirection) {
            return new EndPointImpl(nodePosition, nodeDirection, otherPosition, otherDirection);
        }
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

    // https://pomax.github.io/bezierinfo/#decasteljau
    private final class BezierInput {
        public double fpA, fpB, fdA, fdB;

        public BezierInput setup_k0(double t) {
            double t2 = t*t;

            // fpB = -6t^2 + 6t
            // fpA = 6t^2 - 6t
            // fdB = -9t^2 + 6t
            // fdA = 9t^2 - 12t + 3

            fpB = 6.0 * (t - t2);
            fpA = -fpB;
            fdB = fpB - 3.0 * t2;
            fdA = -fdB - 6.0 * t + 3.0;
            return this;
        }

        public BezierInput setup_k1(double t) {
            double t2 = t*t;
            double t3 = t2*t;

            // fpA = 2t^3 - 3t^2 + 1.0
            // fpB = -2t^3 + 3t^2
            // fdA = 3t^3 - 6t^2 + 3t
            // fdB = -3t^3 + 3t^2

            fpB = -2.0 * t3 + 3.0 * t2;
            fpA = -fpB + 1.0;
            fdB = fpB - t3;
            fdA = -fpB + t3 + 3.0 * (t - t2);
            return this;
        }

        public BezierInput setup_k2(double t) {
            double t2 = t*t;
            double t3 = t2*t;
            double t4 = t2*t2;

            // fpB = -0.5t^4 + t^3
            // fpA = 0.5t^4 - t^3 + t
            // fdB = -0.75t^4 + t^3
            // fdA = 0.75t^4 - 2t^3 + 1.5t^2

            fpB = -0.5 * t4 + t3;
            fpA = -fpB + t;
            fdB = -0.75 * t4 + t3;
            fdA = -fdB - t3 + 1.5*t2;
            return this;
        }
    }
}
