package com.bergerkiller.bukkit.coasters.util;

import com.bergerkiller.bukkit.common.math.Quaternion;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Represents the 3D transformation at which a 2D plane circle is positioned
 */
public class PlaneBasis {
    public final Vector centroid;
    public final Vector ex;
    public final Vector ey;
    public final Vector normal;

    public PlaneBasis(Vector centroid, Vector ex, Vector ey, Vector normal) {
        this.centroid = centroid;
        this.ex = ex;
        this.ey = ey;
        this.normal = normal;
    }

    @Override
    public String toString() {
        return "PlaneBasis{\n" +
                "  centroid=" + centroid + ",\n" +
                "  ex=" + ex + ",\n" +
                "  ey=" + ey + ",\n" +
                "  normal=" + normal + "\n" +
                "}";
    }

    /**
     * Estimates a best-fit plane basis from the given points.
     * The basis consists of a centroid point, two orthonormal basis vectors (ex, ey) on the plane,
     * and a normal vector perpendicular to the plane.
     *
     * @param points Points to estimate the plane from
     * @param upOrientation Optional up orientation vector to ensure the normal faces towards (can be null)
     * @return Estimated PlaneBasis
     */
    public static PlaneBasis estimateFromPoints(List<Vector> points, Vector upOrientation) {
        if (points.isEmpty()) {
            Vector centroid = new Vector(0, 0, 0);
            Vector normal = new Vector(0, 1, 0);
            Vector ex = new Vector(1, 0, 0);
            Vector ey = normal.clone().getCrossProduct(ex).normalize();
            return new PlaneBasis(centroid, ex, ey, normal);
        }

        // centroid
        Vector centroid = new Vector(0, 0, 0);
        points.forEach(centroid::add);
        centroid.multiply(1.0 / points.size());

        // First try to fit the points to a single line instead of a plane
        // If we find that this succeeds with a small orthogonal error, we can treat the points as colinear and
        // pick any plane containing that line. Constrain the plane by input upOrientation if so.
        LineFitResult lineFit = fitLine(centroid, points);
        if (lineFit.isColinear(1e-2)) {
            // Assume 0,1,0 if omitted
            if (upOrientation == null) {
                upOrientation = new Vector(0.0, 1.0, 0.0);
            }

            Quaternion q = Quaternion.fromLookDirection(lineFit.direction, upOrientation);
            return new PlaneBasis(lineFit.centroid, q.rightVector(), q.forwardVector(), q.upVector());
        }

        // compute covariance matrix (population covariance)
        CovarianceMatrix M = CovarianceMatrix.computeFromPoints(points, centroid);

        // get largest eigenvector v1
        Vector v1 = M.powerIter(new Vector(1, 0, 0));

        // deflate
        CovarianceMatrix M2 = M.deflate(v1);

        // power iterate on deflated matrix to get second eigenvector v2
        Vector v2 = M2.powerIter(new Vector(0, 1, 0));

        // ensure orthogonality (orthonormalize v2 against v1)
        v2.subtract(v1.clone().multiply(v1.dot(v2)));
        double v2n = v2.length();
        if (v2n < 1e-12) {
            // fallback: pick any vector orthogonal to v1
            Vector vv1 = v1.clone().normalize();
            Vector perp = perpendicular(vv1);
            v2 = perp;
            double vv = v2.length();
            if (vv < 1e-12) {
                // final fallback
                v2 = new Vector(0, 1, 0);
            } else {
                v2.multiply(1.0 / vv);
            }
        } else {
            v2.multiply(1.0 / v2n);
        }

        // normal = v1 x v2
        Vector vv1 = v1.clone().normalize();
        Vector vv2 = v2.clone().normalize();
        Vector normal = vv1.clone().crossProduct(vv2);
        if (normal.lengthSquared() < 1e-12) {
            // fallback
            normal = perpendicular(vv1).normalize();
        } else {
            normal.normalize();
        }

        // ensure normal faces upOrientation
        if (upOrientation != null && normal.dot(upOrientation) < 0) {
            normal.multiply(-1);
            vv2.multiply(-1);
        }

        Vector ex = vv1.clone().normalize();
        Vector ey = normal.clone().getCrossProduct(ex).normalize();

        return new PlaneBasis(centroid, ex, ey, normal);
    }

    /**
     * Transforms a point from world coordinates into the plane's local coordinate space.
     * The returned vector's components are the coordinates along (ex, ey, normal) respectively.
     * Note: this method ignores the centroid (no translation) and only projects into the basis.
     *
     * @param point Point in world coordinates
     * @return Point in plane-local coordinates (x along ex, y along ey, z along normal)
     */
    public Vector toPlane(Vector point) {
        double x = point.dot(ex);
        double y = point.dot(ey);
        double z = point.dot(normal);
        return new Vector(x, y, z);
    }

    /**
     * Transforms a point from plane-local coordinates (components along ex, ey, normal)
     * back into world coordinates. This does not add the centroid; it only composes the
     * basis vectors scaled by the provided components.
     *
     * @param local Point in plane-local coordinates (x along ex, y along ey, z along normal)
     * @return Point in world coordinates
     */
    public Vector fromPlane(Vector local) {
        Vector out = ex.clone().multiply(local.getX());
        out.add(ey.clone().multiply(local.getY()));
        out.add(normal.clone().multiply(local.getZ()));
        return out;
    }

    // Return any vector perpendicular to v (not necessarily normalized)
    private static Vector perpendicular(Vector v) {
        // pick smallest component to avoid degeneracy
        double ax = Math.abs(v.getX()), ay = Math.abs(v.getY()), az = Math.abs(v.getZ());
        if (ax <= ay && ax <= az) {
            return new Vector(0, -v.getZ(), v.getY());
        } else if (ay <= ax && ay <= az) {
            return new Vector(-v.getZ(), 0, v.getX());
        } else {
            return new Vector(-v.getY(), v.getX(), 0);
        }
    }

    /**
     * Fit a line to points. Returns LineFitResult containing centroid, unit direction,
     * min/max projection scalars, max orthogonal distance and RMS orthogonal distance.
     *
     * @param centroid Centroid of the points (precomputed)
     * @param points Points to fit a line to
     */
    private static LineFitResult fitLine(Vector centroid, List<Vector> points) {
        int n = points.size();
        if (n == 0) {
            Vector dir = new Vector(1, 0, 0);
            return new LineFitResult(centroid, dir, 0, 0, 0, 0, 0);
        }

        // Use the existing CovarianceMatrix helper to compute covariance and dominant direction
        CovarianceMatrix M = CovarianceMatrix.computeFromPoints(points, centroid);
        Vector v1 = M.powerIter(new Vector(1, 0, 0));

        // If power iteration failed or returned near-zero, fall back immediately
        if (v1.lengthSquared() < 1e-18) {
            // fallback to farthest-point heuristic (points identical or numerical issue)
            return fallbackFarthestLine(points, centroid, n);
        }

        Vector dir = v1.clone().normalize();

        // project points and compute orthogonal residuals
        double minT = Double.POSITIVE_INFINITY, maxT = Double.NEGATIVE_INFINITY;
        double maxOrth = 0.0;
        double sumSqOrth = 0.0;
        for (Vector p : points) {
            Vector d = p.clone().subtract(centroid);
            double t = d.dot(dir);
            if (t < minT) minT = t;
            if (t > maxT) maxT = t;
            Vector proj = dir.clone().multiply(t);
            Vector orth = d.clone().subtract(proj);
            double orthLen = orth.length();
            if (orthLen > maxOrth) maxOrth = orthLen;
            sumSqOrth += orthLen * orthLen;
        }
        double rms = Math.sqrt(sumSqOrth / n);

        // If the projection spread is effectively zero, PCA likely produced an orthogonal/degenerate vector.
        if ((maxT - minT) <= 1e-12) {
            return fallbackFarthestLine(points, centroid, n);
        }

        return new LineFitResult(centroid, dir, minT, maxT, maxOrth, rms, n);
    }

    // Helper fallback that implements the farthest-point heuristic used previously
    private static LineFitResult fallbackFarthestLine(List<Vector> points, Vector centroid, int n) {
        // pick an arbitrary reference point
        Vector p0 = points.get(0);
        // find point farthest from p0
        Vector p1 = p0;
        double bestDist = -1.0;
        for (Vector p : points) {
            double d2 = p.clone().subtract(p0).lengthSquared();
            if (d2 > bestDist) { bestDist = d2; p1 = p; }
        }
        // find point farthest from p1
        Vector p2 = p1;
        bestDist = -1.0;
        for (Vector p : points) {
            double d2 = p.clone().subtract(p1).lengthSquared();
            if (d2 > bestDist) { bestDist = d2; p2 = p; }
        }
        Vector fallback = p2.clone().subtract(p1);
        double flen = fallback.length();
        if (flen < 1e-12) {
            // all points nearly identical -> zero spread
            return new LineFitResult(centroid, new Vector(1,0,0), 0, 0, 0, 0, n);
        }
        Vector dir = fallback.multiply(1.0 / flen);

        // recompute projections with fallback dir
        double minT = Double.POSITIVE_INFINITY; double maxT = Double.NEGATIVE_INFINITY;
        double maxOrth = 0.0; double sumSqOrth = 0.0;
        for (Vector p : points) {
            Vector d = p.clone().subtract(centroid);
            double t = d.dot(dir);
            if (t < minT) minT = t;
            if (t > maxT) maxT = t;
            Vector proj = dir.clone().multiply(t);
            Vector orth = d.clone().subtract(proj);
            double orthLen = orth.length();
            if (orthLen > maxOrth) maxOrth = orthLen;
            sumSqOrth += orthLen * orthLen;
        }
        double rms = Math.sqrt(sumSqOrth / n);
        return new LineFitResult(centroid, dir, minT, maxT, maxOrth, rms, n);
    }

    /**
     * Result of fitting a line to points: centroid, unit direction, along-range and orthogonal errors.
     */
    private static class LineFitResult {
        public final Vector centroid;
        public final Vector direction; // unit
        public final double minT;
        public final double maxT;
        public final double maxOrthogonalDist;
        public final double rmsOrthogonalDist;
        public final int count;

        public LineFitResult(Vector centroid, Vector direction, double minT, double maxT,
                             double maxOrthogonalDist, double rmsOrthogonalDist, int count) {
            this.centroid = centroid;
            this.direction = direction;
            this.minT = minT;
            this.maxT = maxT;
            this.maxOrthogonalDist = maxOrthogonalDist;
            this.rmsOrthogonalDist = rmsOrthogonalDist;
            this.count = count;
        }

        /**
         * Spread along the fitted line (scalar projection range).
         */
        public double alongSpread() {
            return maxT - minT;
        }

        /**
         * Returns true if the points are sufficiently colinear based on the maximum orthogonal distance
         * and the spread along the line. If the spread is very small as well, returns
         * false so any random arbitrary plane can be created instead.
         *
         * @param epsilonOrthogonal Maximum allowed ratio of max orthogonal distance to along spread for points to be considered colinear
         * @return True if points are sufficiently colinear, false otherwise
         */
        public boolean isColinear(double epsilonOrthogonal) {
            double spread = alongSpread();
            if (spread <= 1e-4) return false; // too small spread, plane is not well-defined

            // Evaluate the max deviation (point outlier) from center compared to line length (spread)
            return (maxOrthogonalDist / spread) <= epsilonOrthogonal;
        }

        @Override
        public String toString() {
            return "LineFitResult{" +
                    "centroid=" + centroid +
                    ", direction=" + direction +
                    ", minT=" + minT +
                    ", maxT=" + maxT +
                    ", maxOrthogonalDist=" + maxOrthogonalDist +
                    ", rmsOrthogonalDist=" + rmsOrthogonalDist +
                    ", count=" + count +
                    '}';
        }
    }

    private static class CovarianceMatrix {
        public final Vector[] M;

        public CovarianceMatrix(Vector... M) {
            this.M = M;
        }

        /**
         * Performs power iteration on this covariance matrix
         *
         * @param normStart Starting normalized vector
         * @return Resulting dominant eigenvector
         */
        public Vector powerIter(Vector normStart) {
            Vector v = normStart.clone();
            for (int it = 0; it < 60; it++) {
                double wx = M[0].dot(v);
                double wy = M[1].dot(v);
                double wz = M[2].dot(v);
                double wlen = Math.sqrt(wx * wx + wy * wy + wz * wz);
                if (wlen < 1e-15) break;
                v.setX(wx / wlen);
                v.setY(wy / wlen);
                v.setZ(wz / wlen);
            }
            return v;
        }

        public double computeRayleighQuotient(Vector v1) {
            return v1.getX() * M[0].dot(v1) + v1.getY() * M[1].dot(v1) + v1.getZ() * M[2].dot(v1);
        }

        public CovarianceMatrix deflate(Vector v1) {
            // compute Rayleigh quotient (approx eigenvalue)
            double lambda1 = computeRayleighQuotient(v1);

            // deflate: M2 = M - lambda1 * (v1 outer v1)
            return new CovarianceMatrix(
                    M[0].clone().subtract(v1.clone().multiply(lambda1 * v1.getX())),
                    M[1].clone().subtract(v1.clone().multiply(lambda1 * v1.getY())),
                    M[2].clone().subtract(v1.clone().multiply(lambda1 * v1.getZ()))
            );
        }

        public static CovarianceMatrix computeFromPoints(List<Vector> points, Vector centroid) {
            int n = points.size();
            double Cxx = 0, Cxy = 0, Cxz = 0;
            double Cyy = 0, Cyz = 0, Czz = 0;
            for (Vector p : points) {
                double dx = p.getX() - centroid.getX();
                double dy = p.getY() - centroid.getY();
                double dz = p.getZ() - centroid.getZ();
                Cxx += dx * dx;
                Cxy += dx * dy;
                Cxz += dx * dz;
                Cyy += dy * dy;
                Cyz += dy * dz;
                Czz += dz * dz;
            }
            Cxx /= n; Cxy /= n; Cxz /= n; Cyy /= n; Cyz /= n; Czz /= n;

            return new CovarianceMatrix(
                    new Vector(Cxx, Cxy, Cxz),
                    new Vector(Cxy, Cyy, Cyz),
                    new Vector(Cxz, Cyz, Czz)
            );
        }
    }
}
