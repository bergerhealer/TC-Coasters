package com.bergerkiller.bukkit.coasters.util;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;

/**
 * Iterates the full-block positions occupied by a rail section
 */
public class RailSectionBlockIterator {
    private double dx, dy, dz;
    private double px, py, pz;
    private int bx, by, bz;
    private int bdx, bdy, bdz;
    private double remaining;

    public RailSectionBlockIterator(RailPath.Segment segment, IntVector3 rails) {
        final double padding = 0.1;
        this.dx = segment.dt_norm.x;
        this.dy = segment.dt_norm.y;
        this.dz = segment.dt_norm.z;
        this.px = rails.x + segment.p0.x;
        this.py = rails.y + segment.p0.y;
        this.pz = rails.z + segment.p0.z;
        this.bx = MathUtil.floor(this.px);
        this.by = MathUtil.floor(this.py);
        this.bz = MathUtil.floor(this.pz);
        this.px -= this.bx;
        this.py -= this.by;
        this.pz -= this.bz;
        this.remaining = segment.l;
    }

    public IntVector3 block() {
        return new IntVector3(bx, by, bz);
    }

    double min = Double.MAX_VALUE;
 
    public void add(double value, int bdx, int bdy, int bdz) {
        if (value < this.min) {
            this.min = value;
            this.bdx = bdx;
            this.bdy = bdy;
            this.bdz = bdz;
        }
    }

    public boolean next() {
        this.min = Double.MAX_VALUE;

        // Check move distance till x-edge of block
        if (this.dx > 1e-10) {
            add((1.0 - this.px) / this.dx, 1, 0, 0);
        } else if (this.dx < -1e-10) {
            add(this.px / -this.dx, -1, 0, 0);
        }

        // Check move distance till y-edge of block
        if (this.dy > 1e-10) {
            add((1.0 - this.py) / this.dy, 0, 1, 0);
        } else if (this.dy < -1e-10) {
            add(this.py / -this.dy, 0, -1, 0);
        }

        // Check move distance till z-edge of block
        if (this.dz > 1e-10) {
            add((1.0 - this.pz) / this.dz, 0, 0, 1);
        } else if (this.dz < -1e-10) {
            add(this.pz / -this.dz, 0, 0, -1);
        }

        // If exceeding remaining track, abort
        if (this.min > this.remaining) {
            return false;
        }

        // Move to next block
        this.remaining -= this.min;
        this.px += this.dx * this.min;
        this.py += this.dy * this.min;
        this.pz += this.dz * this.min;
        this.bx += this.bdx;
        this.by += this.bdy;
        this.bz += this.bdz;
        this.px -= this.bdx;
        this.py -= this.bdy;
        this.pz -= this.bdz;
        return true;
    }
}
