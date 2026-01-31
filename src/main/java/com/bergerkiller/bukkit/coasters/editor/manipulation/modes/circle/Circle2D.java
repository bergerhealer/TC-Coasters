package com.bergerkiller.bukkit.coasters.editor.manipulation.modes.circle;

import com.bergerkiller.bukkit.common.math.Vector2;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a 2D circle
 */
class Circle2D {
    /** Center X coordinate */
    public final double cx;
    /** Center Y coordinate */
    public final double cy;
    /** Radius */
    public final double r;

    public Circle2D(double cx, double cy, double r) {
        this.cx = cx;
        this.cy = cy;
        this.r = r;
    }

    /**
     * A Taubin-style algebraic circle fit:
     * This implementation performs the centroid shift then uses the common algebraic least-squares solve
     * (which is numerically similar to Taubin for typical point sets). It returns center in the shifted
     * coordinates (cx, cy) and radius r.
     *
     * @param points Points to fit the circle to
     * @return Fitted circle as a Circle2D instance
     */
    public static Circle2D fitCircleTaubinLike(List<Vector2> points) {
        if (points.isEmpty())
            return new Circle2D(0, 0, 0);

        // centroid shift
        double mx = 0, my = 0;
        for (Vector2 p : points) {
            mx += p.x;
            my += p.y;
        }
        mx /= points.size();
        my /= points.size();
        List<Vector2> shiftedPoints = new ArrayList<>(points.size());
        for (Vector2 p : points) {
            shiftedPoints.add(new Vector2(p.x - mx, p.y - my));
        }

        // moments
        double Suu = 0, Suv = 0, Svv = 0;
        double Suuu = 0, Svvv = 0, Suvv = 0, Suuv = 0;
        for (Vector2 p : shiftedPoints) {
            double pxSq = p.x * p.x;
            double pySq = p.y * p.y;
            Suu += pxSq;
            Suv += p.x * p.y;
            Svv += pySq;
            Suuu += pxSq * p.x;
            Svvv += pySq * p.y;
            Suvv += p.x * pySq;
            Suuv += pxSq * p.y;
        }

        // solve linear system [Suu Suv; Suv Svv] * [uc; vc] = 0.5 * [Suuu + Suvv; Svvv + Suuv]
        double A = Suu;
        double B = Suv;
        double C = Svv;
        double D = 0.5 * (Suuu + Suvv);
        double E = 0.5 * (Svvv + Suuv);

        double det = A * C - B * B;
        double uc, vc;
        if (Math.abs(det) < 1e-12) {
            // Degenerate: fall back to simpler method (e.g., average radius)
            uc = 0;
            vc = 0;
        } else {
            uc = (D * C - B * E) / det;
            vc = (A * E - D * B) / det;
        }

        Vector2 center = new Vector2(uc + mx, vc + my);

        // compute average radius
        double r = 0;
        for (Vector2 p : points) {
            r += p.distance(center);
        }
        r /= points.size();

        return new Circle2D(center.x, center.y, r);
    }

    /**
     * Attempts to fit a circle in such a way that the circle bisector fits through two points, and then
     * scales the circle radius to best fit the other points.
     *
     * @param p1 First point on the circle (bisector)
     * @param p2 Second point on the circle (bisector)
     * @param otherPoints Other points to fit the circle radius to
     * @return Fitted circle as a Circle2D instance
     */
    public static Circle2DBiSector fitAlongPerpBisector(Vector2 p1, Vector2 p2, List<Vector2> otherPoints) {
        BiSector2D bisector = BiSector2D.fromPoints(p1, p2);

        // search bounds (heuristic): base on chord length
        double step = Math.max(1.0, bisector.length);
        double left = -10.0 * step;
        double right = 10.0 * step;

        // SSE evaluator
        java.util.function.DoubleFunction<Double> sse = (double t) -> {
            Vector2 center = bisector.getPoint(t);
            double rx = center.x - p1.x;
            double ry = center.y - p1.y;
            double r = Math.hypot(rx, ry);
            double s = 0.0;
            for (Vector2 q : otherPoints) {
                double dx = q.x - center.x;
                double dy = q.y - center.y;
                double d = Math.hypot(dx, dy);
                double diff = d - r;
                s += diff * diff;
            }
            return s;
        };

        // ternary search
        for (int iter = 0; iter < 80; iter++) {
            double a = left + (right - left) / 3.0;
            double b = right - (right - left) / 3.0;
            double fa = sse.apply(a);
            double fb = sse.apply(b);
            if (fa > fb) left = a;
            else right = b;
        }
        double tBest = (left + right) * 0.5;
        Vector2 finalCenter = bisector.getPoint(tBest);
        double finalR = Math.hypot(finalCenter.x - p1.x, finalCenter.y - p1.y);

        // detect whether the other points lay inside or outside this arc
        boolean isShortArc = true;
        if (!otherPoints.isEmpty()) {
            double sumProj = 0.0;
            for (Vector2 q : otherPoints) {
                double vx = q.x - bisector.mx;
                double vy = q.y - bisector.my;
                double proj = vx * bisector.perpUnitX + vy * bisector.perpUnitY; // projection onto perp direction
                sumProj += proj;
            }
            if ((sumProj * tBest) > 0.0) {
                isShortArc = false;
            }
        }

        // compute unit vector pointing from bisector midpoint to the final center
        Vector2 centerDir = new Vector2(finalCenter.x - bisector.mx, finalCenter.y - bisector.my);
        double len = Math.hypot(centerDir.x, centerDir.y);
        if (len > 1e-9) {
            centerDir.x /= len;
            centerDir.y /= len;
        } else {
            // fallback: use perp unit direction from bisector with the sign of tBest
            double sign = (tBest >= 0.0) ? 1.0 : -1.0;
            centerDir.x = bisector.perpUnitX * sign;
            centerDir.y = bisector.perpUnitY * sign;
        }

        return new Circle2DBiSector(finalCenter.x, finalCenter.y, finalR, bisector,
                (tBest >= 0.0) ? 1 : -1,
                centerDir.x,
                centerDir.y,
                isShortArc);
    }
}
