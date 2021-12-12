package com.bergerkiller.bukkit.coasters.commands.arguments;

import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.tc.Util;

public enum TrackPositionAxis {
    X("x", 1) {
        public int getBlock(Vector position) { return position.getBlockX(); }
        public double get(Vector position) { return position.getX(); }
        public Vector set(Vector position, double newValue) { return position.setX(newValue); }
        public int get(IntVector3 position) { return position.x; }
        public IntVector3 set(IntVector3 position, int newValue) { return IntVector3.of(newValue, position.y, position.z); }
    },
    Y("y", 1) {
        public int getBlock(Vector position) { return position.getBlockY(); }
        public double get(Vector position) { return position.getY(); }
        public Vector set(Vector position, double newValue) { return position.setY(newValue); }
        public int get(IntVector3 position) { return position.y; }
        public IntVector3 set(IntVector3 position, int newValue) { return IntVector3.of(position.x, newValue, position.z); }
    },
    Z("z", 1) {
        public int getBlock(Vector position) { return position.getBlockZ(); }
        public double get(Vector position) { return position.getZ(); }
        public Vector set(Vector position, double newValue) { return position.setZ(newValue); }
        public int get(IntVector3 position) { return position.z; }
        public IntVector3 set(IntVector3 position, int newValue) { return IntVector3.of(position.x, position.y, newValue); }
    },
    X_INV("x", -1) {
        public int getBlock(Vector position) { return position.getBlockX(); }
        public double get(Vector position) { return position.getX(); }
        public Vector set(Vector position, double newValue) { return position.setX(newValue); }
        public int get(IntVector3 position) { return position.x; }
        public IntVector3 set(IntVector3 position, int newValue) { return IntVector3.of(newValue, position.y, position.z); }
    },
    Y_INV("y", -1) {
        public int getBlock(Vector position) { return position.getBlockY(); }
        public double get(Vector position) { return position.getY(); }
        public Vector set(Vector position, double newValue) { return position.setY(newValue); }
        public int get(IntVector3 position) { return position.y; }
        public IntVector3 set(IntVector3 position, int newValue) { return IntVector3.of(position.x, newValue, position.z); }
    },
    Z_INV("z", -1) {
        public int getBlock(Vector position) { return position.getBlockZ(); }
        public double get(Vector position) { return position.getZ(); }
        public Vector set(Vector position, double newValue) { return position.setZ(newValue); }
        public int get(IntVector3 position) { return position.z; }
        public IntVector3 set(IntVector3 position, int newValue) { return IntVector3.of(position.x, position.y, newValue); }
    };

    private final String name;
    private final int mod;

    private TrackPositionAxis(String name, int mod) {
        this.name = name;
        this.mod = mod;
    }

    public String getName() {
        return this.name;
    }

    public String getCoord() {
        return this.name.toUpperCase() + "-Coordinate";
    }

    public abstract int getBlock(Vector position);

    public abstract double get(Vector position);

    public abstract Vector set(Vector position, double newValue);

    public abstract int get(IntVector3 position);

    public abstract IntVector3 set(IntVector3 position, int newValue);

    public Vector align(Vector position, double blockRelative) {
        if (mod > 0) {
            return set(position, getBlock(position) + blockRelative);
        } else {
            return set(position, getBlock(position) + 1.0 - blockRelative);
        }
    }

    public Vector add(Vector position, double addition) {
        return set(position, get(position) + addition * mod);
    }

    public IntVector3 add(IntVector3 position, int addition) {
        return set(position, get(position) + addition * mod);
    }

    public static TrackPositionAxis eye(Player player) {
        Vector vec = player.getEyeLocation().getDirection();
        BlockFace face = Util.vecToFace(vec, false);
        if (face.getModX() != 0) {
            return vec.getX() > 0.0 ? TrackPositionAxis.X : TrackPositionAxis.X_INV;
        } else if (face.getModZ() != 0) {
            return vec.getZ() > 0.0 ? TrackPositionAxis.Z : TrackPositionAxis.Z_INV;
        } else {
            return vec.getY() > 0.0 ? TrackPositionAxis.Y : TrackPositionAxis.Y_INV;
        }
    }
}
