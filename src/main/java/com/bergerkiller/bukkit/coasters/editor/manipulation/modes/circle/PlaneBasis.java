package com.bergerkiller.bukkit.coasters.editor.manipulation.modes.circle;

import org.bukkit.util.Vector;

import java.util.List;

/**
 * Represents the 3D transformation at which a 2D plane circle is positioned
 */
class PlaneBasis {
    public final Vector centroid;
    public final Vector ex;
    public final Vector ey;
    public final Vector normal;

    PlaneBasis(Vector centroid, Vector ex, Vector ey, Vector normal) {
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
