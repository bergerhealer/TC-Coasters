package com.bergerkiller.bukkit.coasters.util.integration;

import java.util.function.DoubleUnaryOperator;

/**
 * Precomputed nodes and weights for Gauss–Legendre quadrature rules.
 * <a href="https://en.wikipedia.org/wiki/Gauss%E2%80%93Legendre_quadrature">Wiki page</a><br>
 * <br>
 * It's a little overkill to compute the nodes and weights at runtime, but it allows us to have a single source of truth
 * for the quadrature rules and to assert correctness. It also allows trying out different values of n to test
 * performance.
 */
class GaussLegendreQuadratureIntegrator implements RangeIntegrator {
    public final int n;
    public final double[] x;
    public final double[] w;

    private GaussLegendreQuadratureIntegrator(int n, double[] x, double[] w) {
        this.n = n;
        this.x = x;
        this.w = w;
    }

    @Override
    public double integrate(DoubleUnaryOperator speedFunc, double t0, double t1) {
        double sum = 0.0;
        double half = 0.5 * (t1 - t0);
        double mid = 0.5 * (t1 + t0);
        for (int i = 0; i < n; i++) {
            double t = mid + half * x[i];
            sum += w[i] * speedFunc.applyAsDouble(t);
        }
        return half * sum;
    }

    public static GaussLegendreQuadratureIntegrator computeRule(int n) {
        double[] x = new double[n];
        double[] w = new double[n];

        int m = (n + 1) / 2; // roots come in symmetric pairs
        for (int i = 1; i <= m; i++) {
            // Initial guess (good empirical formula)
            double z = Math.cos(Math.PI * (i - 0.25) / (n + 0.5));

            // Replace the old do/while Newton-Raphson with a more robust iteration.
            final double eps = 1e-16;      // tighter tolerance close to machine precision
            final int maxIter = 10000;     // allow many iterations for high-precision roots
            int iter = 0;
            double p = 0.0, dp = 0.0;

            while (iter < maxIter) {
                double[] pd = legendreWithDerivative(n, z);
                p = pd[0];
                dp = pd[1];

                if (Double.isNaN(p) || Double.isNaN(dp)) {
                    // give up and keep current z
                    break;
                }

                if (dp == 0.0) {
                    // Derivative is zero (flat) — perturb by a few ulps to escape
                    double reference = (z == 0.0) ? 1.0 : z;
                    double perturb = Math.copySign(10.0 * Math.ulp(reference), (i % 2 == 0) ? 1.0 : -1.0);
                    z += perturb;
                    iter++;
                    continue;
                }

                double delta = p / dp;
                double zNew = z - delta;

                // Stopping criteria: prefer small residual and small relative update (close to machine precision)
                double relUpdate = Math.abs(delta) / (1.0 + Math.abs(z));
                double tolUlp = 10.0 * Math.ulp((z == 0.0) ? 1.0 : z);
                if (Math.abs(p) <= eps || relUpdate <= eps || Math.abs(delta) <= tolUlp) {
                    z = zNew;
                    break;
                }

                z = zNew;
                iter++;
            }

            // Recompute final derivative and fall back to finite-difference if needed
            double[] pdFinal = legendreWithDerivative(n, z);
            double dpFinal = pdFinal[1];
            if (dpFinal == 0.0) {
                // central difference fallback — use a small delta around z
                double delta = Math.sqrt(Math.ulp(1.0)); // ~1e-8, a reasonable finite-diff step
                double pPlus = legendreWithDerivative(n, z + delta)[0];
                double pMinus = legendreWithDerivative(n, z - delta)[0];
                dpFinal = (pPlus - pMinus) / (2.0 * delta);
                if (dpFinal == 0.0) {
                    // last resort: avoid division by zero by using a tiny derivative
                    dpFinal = 1e-300;
                }
            }

            double weight = 2.0 / ((1.0 - z * z) * dpFinal * dpFinal);

            int j = i - 1;
            int k = n - i;
            x[j] = -Math.abs(z);
            x[k] = Math.abs(z);
            w[j] = weight;
            w[k] = weight;
        }

        // For odd n, ensure the center root is exactly 0 (numerical may be tiny)
        if ((n & 1) == 1) {
            int center = n / 2;
            x[center] = 0.0;
            double[] pd = legendreWithDerivative(n, 0.0);
            double dp = pd[1];
            w[center] = 2.0 / ((1.0 - 0.0) * dp * dp);
        }

        return new GaussLegendreQuadratureIntegrator(n, x, w);
    }

    // Returns {P_n(x), P'_n(x)}
    private static double[] legendreWithDerivative(int n, double x) {
        if (n == 0) {
            return new double[] {1.0, 0.0};
        } else if (n == 1) {
            return new double[] {x, 1.0};
        }

        double pnm2 = 1.0; // P_0
        double pnm1 = x;   // P_1
        double pn = 0.0;

        for (int k = 2; k <= n; k++) {
            pn = ((2.0 * k - 1.0) * x * pnm1 - (k - 1.0) * pnm2) / k;
            pnm2 = pnm1;
            pnm1 = pn;
        }

        // Derivative using relation: P'_n(x) = n*(x*P_n(x) - P_{n-1}(x)) / (x^2 - 1)
        double dp;
        if (Math.abs(x) == 1.0) {
            // Handle endpoint to avoid division by zero: use limit value
            dp = n * (n + 1) / 2.0 * Math.copySign(1.0, x);
        } else {
            dp = n * (x * pn - pnm2) / (x * x - 1.0);
        }

        return new double[] {pn, dp};
    }
}
