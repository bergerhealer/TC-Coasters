package com.bergerkiller.bukkit.coasters.util.integration;

import java.util.function.DoubleUnaryOperator;

/**
 * An implementation of a numerical integration algorithm. Accepts an external integrand function
 * that is called for a range of theta values, and computes the integral over that range. The integrand is
 * typically the speed function |dP/dt| for a bezier curve, and the integral gives the distance traveled along the curve.
 */
@FunctionalInterface
public interface RangeIntegrator {
    /** 5-point Gauss–Legendre Quadrature integration algorithm */
    RangeIntegrator GLQ_FIVE = GaussLegendreQuadratureIntegrator.computeRule(5);

    /** 8-point Gauss–Legendre Quadrature integration algorithm */
    RangeIntegrator GLQ_EIGHT = GaussLegendreQuadratureIntegrator.computeRule(8);

    /**
     * Integrates the integrand function over the interval [t0, t1] using a single-step fast rule.
     * The {@link #integrateAdaptive} function calls this one in an adaptive manner, but this method can be used directly for a quick estimate
     * without error control.
     *
     * @param integrand A function f(t) that returns the integrand value at parameter t (for curves, typically the speed |dP/dt|).
     *                  The integrator will sample this function many times, so it should be continuous and fast to evaluate.
     * @param t0 Start theta
     * @param t1 End theta
     * @return Value computed by integrating the function over [t0, t1], which for a speed function gives the estimated distance traveled.
     */
    double integrate(DoubleUnaryOperator integrand, double t0, double t1);

    /**
     * Integrates the integrand function over the interval [t0, t1] using this range integrator.
     * This method recursively subdivides the interval until the estimated error is within the specified tolerance.
     * The maxDepth parameter prevents infinite recursion in pathological cases.
     *
     * @param integrand A function f(t) that returns the integrand value at parameter t (for curves, typically the speed |dP/dt|).
     *                  The integrator will sample this function many times, so it should be continuous and fast to evaluate.
     * @param t0 Start theta
     * @param t1 End theta
     * @param tol Desired accuracy (tolerance) for the integral estimate. The method will subdivide until the
     *            estimated error is less than or equal to this value.
     * @param maxDepth Maximum recursion depth to prevent infinite subdivision. If this limit is reached, the method will return
     *                 the best estimate it has without further subdivision.
     * @return Value computed by integrating the function over [t0, t1], which for a speed function gives the estimated distance traveled.
     */
    default double integrateAdaptive(DoubleUnaryOperator integrand, double t0, double t1, double tol, int maxDepth) {
        return integrateAdaptiveRec(integrand, t0, t1, tol, maxDepth, integrate(integrand, t0, t1));
    }

    /**
     * Recursive helper method for adaptive integration.
     *
     * @param integrand A function f(t) that returns the integrand value at parameter t (for curves, typically the speed |dP/dt|).
     *                  The integrator will sample this function many times, so it should be continuous and fast to evaluate.
     * @param t0 Start theta
     * @param t1 End theta
     * @param tol Desired accuracy (tolerance) for the integral estimate. The method will subdivide until the
     *            estimated error is less than or equal to this value.
     * @param maxDepth Maximum recursion depth to prevent infinite subdivision. If this limit is reached, the method will return
     *                 the best estimate it has without further subdivision.
     * @param I Initial estimate of the integral over [t0, t1] using the single-step integrate method
     * @return Value computed by integrating the function over [t0, t1], which for a speed function gives the estimated distance traveled.
     */
    default double integrateAdaptiveRec(DoubleUnaryOperator integrand, double t0, double t1, double tol, int maxDepth, double I) {
        double mid = 0.5 * (t0 + t1);
        double Il = integrate(integrand, t0, mid);
        double Ir = integrate(integrand, mid, t1);

        double diff = Math.abs(I - (Il + Ir));
        if (diff > tol && maxDepth > 0) {
            // Recurse, splitting tolerance across children
            tol *= 0.5;
            maxDepth--;
            Il = integrateAdaptiveRec(integrand, t0, mid, tol, maxDepth, Il);
            Ir = integrateAdaptiveRec(integrand, mid, t1, tol, maxDepth, Ir);
        }

        return Il + Ir;
    }

    /**
     * Inverse integration: find theta in [tLower, tUpper] such that the integral of integrand
     * from tLower to theta equals target (within tol). This method performs a two-stage
     * search:
     * <ul>
     *     <li>coarse bisection using the fast non-adaptive {@link #integrate} to bracket the result</li>
     *     <li>fine bisection using the accurate {@link #integrateAdaptive} to reach tolerance</li>
     * </ul>
     *
     * If the coarse bracket (when validated with adaptive integrals) does not actually contain the
     * target (due to coarse rule inaccuracies), we will attempt to expand the bracket by nudging the
     * low/high bounds toward the interval endpoints in a fixed number of back-off steps; each step
     * increases the fraction of the remaining parameter distance moved, starting from a small
     * initial fraction and ending at 1.0 (which reaches the endpoint). If nudging can't produce a
     * valid bracket, we fall back to an adaptive-only bisection. This reduces discarding useful
     * coarse information and avoids costly full adaptive searches when a small correction suffices.
     *
     * @param integrand A function f(t) that returns the integrand value at parameter t (for curves, typically the speed |dP/dt|).
     *                  The integrator will sample this function many times, so it should be continuous and fast to evaluate.
     * @param tLower lower bound theta
     * @param tUpper upper bound theta
     * @param target desired integrated value (>= 0)
     * @param tol target tolerance for the adaptive refinement stage
     * @param maxDepth max recursion depth to pass to adaptive integration
     * @return theta in [tLower,tUpper] approximating the inverse integral
     */
    default double invertIntegrate(DoubleUnaryOperator integrand, double tLower, double tUpper, double target, double tol, int maxDepth) {
        if (target <= 0.0) return tLower;

        // Quick coarse check: if target exceeds coarse integral over entire interval, verify adaptively and clamp
        double coarseTotal = integrate(integrand, tLower, tUpper);
        if (target >= coarseTotal) {
            double adaptiveTotal = integrateAdaptive(integrand, tLower, tUpper, tol, maxDepth);
            if (target >= adaptiveTotal) return tUpper;
        }

        // Coarse bisection stage using non-adaptive integrate to quickly bracket the theta
        double low = tLower;
        double high = tUpper;
        double coarseSwitchTol = Math.max(1e-6, tol * 10.0);
        int maxCoarseIter = 60;
        for (int i = 0; i < maxCoarseIter; i++) {
            double mid = 0.5 * (low + high);
            double val = integrate(integrand, tLower, mid);
            double diff = val - target;
            if (Math.abs(diff) <= coarseSwitchTol) {
                // Good enough to start adaptive validation/refinement
                low = Math.max(low, mid - 1e-16);
                high = Math.min(high, mid + 1e-16);
                break;
            }
            if (val < target) low = mid; else high = mid;
            if (high - low <= 1e-14) break;
        }

        // Validate coarse bracket using adaptive integrals
        final int maxAdjust = 10;              // number of steps when adjusting the search lower/upper bounds
        final double minProbeThetaAdj = 0.001; // minimum amount in theta to step up/down when probing for the new bound
        final double nonLinearityAdj = 1.5;    // multiplier for adjustment so we shoot a little further lower/higher

        // If lower bound integral is already above target, move 'low' toward tLower using back-off steps
        double aLow = integrateAdaptive(integrand, tLower, low, tol, maxDepth);
        if (aLow > target) {
            // Predictive adjustment: assume theta -> integral relation is approximately linear locally.
            // Use small probes to estimate the local rate and predict the delta-theta needed.
            int stepIdx;
            for (stepIdx = 0;; stepIdx++) {
                double remainingParam = low - tLower;
                if (remainingParam <= 0.0) break;

                // probe amount: up to minProbeThetaAdj or 10% of remaining, whichever is smaller
                double dth = Math.min(minProbeThetaAdj, Math.max(remainingParam * 0.1, 1e-12));
                double probe = Math.max(tLower, low - dth);

                double valAtLow = aLow; // integrateAdaptive(integrand, tLower, low, tol, maxDepth);
                double valProbe = integrateAdaptive(integrand, tLower, probe, tol, maxDepth);

                double rate = (valAtLow - valProbe) / (low - probe); // approx dI/dtheta >0
                double move = nonLinearityAdj * (valAtLow - target) / rate;

                // If rate is too small/zero (flat or noisy), fall back to a small fractional step
                if (Double.isFinite(rate) && Math.abs(rate) > 1e-12 && move > 1e-12) {
                    double newLow = Math.max(tLower, low - move);
                    // Don't move more than remainingParam
                    newLow = Math.max(tLower, Math.min(low - 1e-18, newLow));
                    // Apply
                    low = newLow;
                } else {
                    // fallback small fractional step
                    double frac = ((stepIdx + 1) / (double) maxAdjust);
                    double step = Math.max(1e-15, remainingParam * frac);
                    low = Math.max(tLower, low - step);
                }

                aLow = integrateAdaptive(integrand, tLower, low, tol, maxDepth);
                if (aLow <= target || Math.abs(low - tLower) <= 1e-18) {
                    break;
                }

                // Failed to find within the maximum number of steps, reset lower search bound to tLower
                if (stepIdx >= maxAdjust) {
                    low = tLower;
                    break;
                }
            }

            //System.out.println("STOP ADJUST DOWN AFTER " + (stepIdx + 1) + " STEPS: " + aLow + " / " + target);
        }

        // If upper bound integral is below target, move 'high' toward tUpper using back-off steps
        double aHigh = integrateAdaptive(integrand, tLower, high, tol, maxDepth);
        if (aHigh < target) {
            int stepIdx;
            for (stepIdx = 0;; stepIdx++) {
                double remainingParam = tUpper - high;
                if (remainingParam <= 0.0) break;

                double dth = Math.min(minProbeThetaAdj, Math.max(remainingParam * 0.1, 1e-12));
                double probe = Math.min(tUpper, high + dth);

                double valAtHigh = aHigh; // integrateAdaptive(integrand, tLower, high, tol, maxDepth);
                double valProbe = integrateAdaptive(integrand, tLower, probe, tol, maxDepth);

                double rate = (valProbe - valAtHigh) / (probe - high); // approx dI/dtheta
                double move = nonLinearityAdj * (target - valAtHigh) / rate;

                if (Double.isFinite(rate) && Math.abs(rate) > 1e-12 && move > 1e-12) {
                    double newHigh = Math.min(tUpper, high + move);
                    newHigh = Math.min(tUpper, Math.max(high + 1e-18, newHigh));
                    high = newHigh;
                } else {
                    double frac = ((stepIdx + 1) / (double) maxAdjust);
                    double step = Math.max(1e-15, remainingParam * frac);
                    high = Math.min(tUpper, high + step);
                }

                aHigh = integrateAdaptive(integrand, tLower, high, tol, maxDepth);
                if (aHigh >= target || Math.abs(tUpper - high) <= 1e-18) {
                    break;
                }

                // Failed to find within the maximum number of steps, reset upper search bound to tUpper
                if (stepIdx >= maxAdjust) {
                    high = tUpper;
                    break;
                }
            }

            //System.out.println("STOP ADJUST UP AFTER " + (stepIdx + 1) + " STEPS: " + aHigh + " / " + target);
        }

        // Fine refinement stage: use adaptive integrator to bisect to desired tolerance
        double aLowB = low;
        double aHighB = high;
        double aMid = 0.5 * (low + high);
        int adaptiveSteps;
        for (adaptiveSteps = 0; adaptiveSteps < 80; adaptiveSteps++) {
            double val = integrateAdaptive(integrand, tLower, aMid, tol, maxDepth);
            double diff = val - target;
            if (Math.abs(diff) <= tol) break;
            if (val < target) aLowB = aMid; else aHighB = aMid;
            aMid = 0.5 * (aLowB + aHighB);
            if (aHighB - aLowB <= 1e-15) break; // reached theta precision limit
        }

        //System.out.println("REFINED FOR " + adaptiveSteps + " STEPS");

        return aMid;
    }
}
