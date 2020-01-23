package com.bergerkiller.bukkit.coasters.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;

/**
 * Iterates the full-block positions occupied by a rail section
 */
public class RailSectionBlockIterator {
    private double dx, dy, dz;
    private double px, py, pz;
    private IntVector3 block;
    private BlockFace step;
    private double remaining;

    public RailSectionBlockIterator(RailPath.Segment segment, IntVector3 rails) {
        this.dx = segment.dt_norm.x;
        this.dy = segment.dt_norm.y;
        this.dz = segment.dt_norm.z;
        this.px = rails.x + segment.p0.x;
        this.py = rails.y + segment.p0.y;
        this.pz = rails.z + segment.p0.z;
        this.block = new IntVector3(this.px, this.py, this.pz);
        this.px -= this.block.x;
        this.py -= this.block.y;
        this.pz -= this.block.z;
        this.step = BlockFace.SELF;
        this.remaining = segment.l;
    }

    public IntVector3 block() {
        return this.block;
    }

    /**
     * Obtains the blocks directly around the current position, nearby
     * enough based on distance to center. The step direction is excluded.
     * 
     * @param distance The distance from the current position (max 0.5)
     * @return blocks around the position
     */
    public Collection<IntVector3> around(double distance) {
        switch (this.step) {
        case EAST:
        case WEST:
            return around_calc(this.py, this.pz, BlockFace.UP, BlockFace.SOUTH, distance);
        case UP:
        case DOWN:
            return around_calc(this.px, this.pz, BlockFace.EAST, BlockFace.SOUTH, distance);
        case NORTH:
        case SOUTH:
            return around_calc(this.py, this.pz, BlockFace.UP, BlockFace.EAST, distance);
        case SELF:
            return aroundEnd(distance);
        default:
            return Collections.emptyList();
        }
    }

    /**
     * Obtains the blocks directly around the current position, nearby
     * enough based on distance to center.
     * 
     * @param distance The distance from the current position (max 0.5)
     * @return blocks around the position
     */
    public Collection<IntVector3> aroundEnd(double distance) {
        // All axis together, is only done one time at the first position (the 'head')
        double t0 = distance;
        double t1 = 1.0 - distance;
        ArrayList<IntVector3> around = new ArrayList<IntVector3>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue; // skip middle
                    }

                    // Block edge rules
                    if (dx == -1 && this.px >= t0) continue;
                    if (dx == 1 && this.px <= t1) continue;
                    if (dy == -1 && this.py >= t0) continue;
                    if (dy == 1 && this.py <= t1) continue;
                    if (dz == -1 && this.pz >= t0) continue;
                    if (dz == 1 && this.pz <= t1) continue;

                    // Add
                    around.add(this.block.add(dx, dy, dz));
                }
            }
        }
        return around;
    }

    // Calculates the 1 or 3 neighbours
    // pa is the position of the first axis
    // pb is the position of the second axis
    // fa is the positive axis of the first axis
    // fb is the positive axis of the second axis
    // d is the distance parameter
    private Collection<IntVector3> around_calc(double pa, double pb, BlockFace fa, BlockFace fb, double d) {
        // Compute the position at the first axis
        IntVector3 va;
        if (pa < d) {
            va = this.block.subtract(fa);
        } else if (pa > (1.0 - d)) {
            va = this.block.add(fa);
        } else if (pb < d) {
            // Only second axis, negative
            return Collections.singleton(this.block.subtract(fb));
        } else if (pb > (1.0 - d)) {
            // Only second axis, positive
            return Collections.singleton(this.block.add(fb));
        } else {
            // Not near the edge of the block
            return Collections.emptySet();
        }

        // Compute the permutations with the second axis
        if (pb < d) {
            return Arrays.asList(va, va.subtract(fb), this.block.subtract(fb));
        } else if (pb > (1.0 - d)) {
            return Arrays.asList(va, va.add(fb), this.block.add(fb));
        } else {
            return Collections.singleton(va);
        }
    }

    double min = Double.MAX_VALUE;
 
    public void add(double value, BlockFace step) {
        if (value < this.min) {
            this.min = value;
            this.step = step;
        }
    }

    public boolean next() {
        this.min = Double.MAX_VALUE;

        // Check move distance till x-edge of block
        if (this.dx > 1e-10) {
            add((1.0 - this.px) / this.dx, BlockFace.EAST);
        } else if (this.dx < -1e-10) {
            add(this.px / -this.dx, BlockFace.WEST);
        }

        // Check move distance till y-edge of block
        if (this.dy > 1e-10) {
            add((1.0 - this.py) / this.dy, BlockFace.UP);
        } else if (this.dy < -1e-10) {
            add(this.py / -this.dy, BlockFace.DOWN);
        }

        // Check move distance till z-edge of block
        if (this.dz > 1e-10) {
            add((1.0 - this.pz) / this.dz, BlockFace.SOUTH);
        } else if (this.dz < -1e-10) {
            add(this.pz / -this.dz, BlockFace.NORTH);
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
        this.block = this.block.add(this.step);
        this.px -= this.step.getModX();
        this.py -= this.step.getModY();
        this.pz -= this.step.getModZ();
        return true;
    }
}
