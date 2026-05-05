package com.bergerkiller.bukkit.coasters.tracks.path;

import com.bergerkiller.bukkit.coasters.util.integration.RangeIntegrator;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.tracks.TrackConnectionPath;

import static com.bergerkiller.bukkit.coasters.TCCoastersUtil.sumComponents;

/**
 * Quadratic bezier curve maths
 * 
 * https://pomax.github.io/bezierinfo/#decasteljau
 */
public class Bezier {
    public final EndPoint endA, endB;
    public double fpA, fpB, fdA, fdB;

    private Bezier(EndPoint endA, EndPoint endB) {
        this.endA = endA;
        this.endB = endB;
    }

    public static Bezier create(TrackConnectionPath path) {
        return new Bezier(path.getEndA(), path.getEndB());
    }

    public static Bezier create(EndPoint endA, EndPoint endB) {
        return new Bezier(endA, endB);
    }

    public Vector getMotionVector(double t, Vector out_vec) {
        return setup_k0(t).compute(out_vec);
    }

    public Vector getPosition(double t, Vector out_vec) {
        return setup_k1(t).compute(out_vec);
    }

    public Vector getAbsition(double t, Vector out_vec) {
        return setup_k2(t).compute(out_vec);
    }

    /**
     * Computes the arc length of this bezier curve between t0 and t1
     *
     * @param t0 Start theta of the curve (0.0)
     * @param t1 End theta of the curve (1.0)
     * @return arc length of the curve between t0 and t1
     */
    public double arcLength(double t0, double t1) {
        final double tol = 1e-6; // absolute tolerance for arc length
        final int maxDepth = 10; // maximum number of recursive subdivisions
        final RangeIntegrator integrator = RangeIntegrator.GLQ_EIGHT; // integrator to tweak performance

        return integrator.integrateAdaptive(
                t -> getMotionVector(t, new Vector()).length(),
                t0, t1, tol, maxDepth
        );
    }

    /**
     * Walks this bezier curve from t0 to t1, calculating the theta value of the point found a distance away.
     * This is the inverse of arcLength(t0, t1)
     *
     * @param tLower Start theta of the curve (0.0)
     * @param tUpper End theta of the curve (1.0)
     * @param targetDistance The distance to walk from the lower bound
     * @return theta value of the point at the distance from tLower, or tUpper if the distance exceeds the total arc length between tLower and tUpper
     */
    public double invThetaFromArcLength(double tLower, double tUpper, double targetDistance) {
        final double tol = 1e-6; // absolute tolerance for arc length
        final int maxDepth = 20; // maximum number of recursive subdivisions
        final RangeIntegrator integrator = RangeIntegrator.GLQ_EIGHT; // integrator to tweak performance

        return integrator.invertIntegrate(
                t -> getMotionVector(t, new Vector()).length(),
                tLower, tUpper, targetDistance,
                tol, maxDepth
        );
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

        Bezier b0 = Bezier.create(endA, endB).setup_k1(t0);
        Bezier b1 = Bezier.create(endA, endB).setup_k1(t1);

        Vector delta = Bezier.compute(endA, endB, b1.fpA-b0.fpA, b1.fpB-b0.fpB, b1.fdA-b0.fdA, b1.fdB-b0.fdB, new Vector());
        double delta_NZ = MathUtil.getNormalizationFactor(delta);
        if (Double.isFinite(delta_NZ)) {
            delta.multiply(delta_NZ);
        } else {
            return 0.0; // Zero length path
        }

        EndPoint endA = this.endA;
        EndPoint endB = this.endB;

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

    private Bezier setup_k0(double t) {
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

    private Bezier setup_k1(double t) {
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

    private Bezier setup_k2(double t) {
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

    private Vector compute(Vector out_vec) {
        return compute(this.endA, this.endB, this.fpA, this.fpB, this.fdA, this.fdB, out_vec);
    }

    public static Vector compute(EndPoint endA, EndPoint endB, double fpA, double fpB, double fdA, double fdB, Vector out_vec) {
        double pfdA = fdA * endA.getStrength();
        double pfdB = fdB * endB.getStrength();
        Vector pA = endA.getPosition();
        Vector pB = endB.getPosition();
        Vector dA = endA.getDirection();
        Vector dB = endB.getDirection();
        out_vec.setX(fpA*pA.getX() + fpB*pB.getX() + pfdA*dA.getX() + pfdB*dB.getX());
        out_vec.setY(fpA*pA.getY() + fpB*pB.getY() + pfdA*dA.getY() + pfdB*dB.getY());
        out_vec.setZ(fpA*pA.getZ() + fpB*pB.getZ() + pfdA*dA.getZ() + pfdB*dB.getZ());
        return out_vec;
    }
}
