package com.bergerkiller.bukkit.coasters;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicHorizontal;
import com.bergerkiller.generated.net.minecraft.server.AxisAlignedBBHandle;

/**
 * Random stuff used internally that just needs a place
 */
public class TCCoastersUtil {

    public static Vector snapToBlock(World world, Vector eyePos, Vector position) {
        // Direction vector to move into to find a free air block
        double wX = eyePos.getX() - position.getX();
        double wY = eyePos.getY() - position.getY();
        double wZ = eyePos.getZ() - position.getZ();
        double wN = MathUtil.getNormalizationFactor(wX, wY, wZ);
        if (Double.isInfinite(wN)) {
            wX = 0.0; wY = 1.0; wZ = 0.0;
        } else {
            wX *= wN; wY *= wN; wZ *= wN;
        }

        final double OFFSET_TO_SIDE = RailLogicHorizontal.Y_POS_OFFSET;
        double totalDistance = 0.0;
        Block curBlock = world.getBlockAt(position.getBlockX(), position.getBlockY(), position.getBlockZ());
        Vector curPos = new Vector(position.getX() - curBlock.getX(),
                                   position.getY() - curBlock.getY(),
                                   position.getZ() - curBlock.getZ());
        boolean foundFreeSpace = true;
        while (true) {
            AxisAlignedBBHandle bounds = BlockUtil.getBoundingBox(curBlock);
            if (bounds == null) {
                break; // Found nonblocking; stop
            }
            if (curPos.getX() < bounds.getMinX() || curPos.getY() < bounds.getMinY() || curPos.getZ() < bounds.getMinZ()) {
                break; // Found nonblocking; stop
            }
            if (curPos.getX() > bounds.getMaxX() || curPos.getY() > bounds.getMaxY() || curPos.getZ() > bounds.getMaxZ()) {
                break; // Found nonblocking; stop
            }

            if (totalDistance > 5.0) {
                foundFreeSpace = false;
                break; // Abort
            }
            
            // Snap position onto the bounds of this bounding box + a small offset off block face
            final double MIN_DELTA = 1e-4;
            double minDist = Double.MAX_VALUE;
            if (wX > MIN_DELTA) {
                double d = (bounds.getMaxX() - curPos.getX() + OFFSET_TO_SIDE) / wX;
                if (d > 0.0 && d < minDist) {
                    minDist = d;
                }
            } else if (wX < -MIN_DELTA) {
                double d = (bounds.getMinX() - curPos.getX() - OFFSET_TO_SIDE) / wX;
                if (d > 0.0 && d < minDist) {
                    minDist = d;
                }
            }
            if (wY > MIN_DELTA) {
                double d = (bounds.getMaxY() - curPos.getY() + OFFSET_TO_SIDE) / wY;
                if (d > 0.0 && d < minDist) {
                    minDist = d;
                }
            } else if (wY < -MIN_DELTA) {
                double d = (bounds.getMinY() - curPos.getY() - OFFSET_TO_SIDE) / wY;
                if (d > 0.0 && d < minDist) {
                    minDist = d;
                }
            }
            if (wZ > MIN_DELTA) {
                double d = (bounds.getMaxZ() - curPos.getZ() + OFFSET_TO_SIDE) / wZ;
                if (d > 0.0 && d < minDist) {
                    minDist = d;
                }
            } else if (wZ < -MIN_DELTA) {
                double d = (bounds.getMinZ() - curPos.getZ() - OFFSET_TO_SIDE) / wZ;
                if (d > 0.0 && d < minDist) {
                    minDist = d;
                }
            }

            if (minDist == Double.MAX_VALUE) {
                break;
            }

            curPos.setX(curPos.getX() + wX * minDist);
            curPos.setY(curPos.getY() + wY * minDist);
            curPos.setZ(curPos.getZ() + wZ * minDist);

            int dx = curPos.getBlockX();
            int dy = curPos.getBlockY();
            int dz = curPos.getBlockZ();
            curPos.setX(curPos.getX() - dx);
            curPos.setY(curPos.getY() - dy);
            curPos.setZ(curPos.getZ() - dz);
            curBlock = curBlock.getRelative(dx, dy, dz);
            totalDistance += minDist;
        }
        if (foundFreeSpace) {
            position = curPos;
            position.setX(position.getX() + curBlock.getX());
            position.setY(position.getY() + curBlock.getY());
            position.setZ(position.getZ() + curBlock.getZ());
        }
        return position;
    }
}
