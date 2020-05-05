package com.bergerkiller.bukkit.coasters;

import java.util.ArrayList;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicHorizontal;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.generated.net.minecraft.server.AxisAlignedBBHandle;
import com.bergerkiller.generated.net.minecraft.server.MovingObjectPositionHandle;
import com.bergerkiller.generated.net.minecraft.server.WorldHandle;

/**
 * Random stuff used internally that just needs a place
 */
public class TCCoastersUtil {
    private static final int[] BLOCK_DELTAS_NEG = new int[] {0, -1};
    private static final int[] BLOCK_DELTAS_POS = new int[] {0, 1};
    private static final int[] BLOCK_DELTAS_ZER = new int[] {0};
    public static final double OFFSET_TO_SIDE = RailLogicHorizontal.Y_POS_OFFSET;

    public static int[] getBlockDeltas(double value) {
        if (value > 1e-10) {
            return BLOCK_DELTAS_POS;
        } else if (value < -1e-10) {
            return BLOCK_DELTAS_NEG;
        } else {
            return BLOCK_DELTAS_ZER;
        }
    }

    public static double distanceSquaredXY(Vector p) {
        return p.getX()*p.getX()+p.getY()*p.getY();
    }

    public static double sumComponents(Vector v) {
        return v.getX() + v.getY() + v.getZ();
    }

    public static void snapToBlock(World world, Vector eyePos, Vector position, Vector orientation) {
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

        double totalDistance = 0.0;
        BlockFace curFace = BlockFace.SELF;
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
                    curFace = BlockFace.EAST;
                }
            } else if (wX < -MIN_DELTA) {
                double d = (bounds.getMinX() - curPos.getX() - OFFSET_TO_SIDE) / wX;
                if (d > 0.0 && d < minDist) {
                    minDist = d;
                    curFace = BlockFace.WEST;
                }
            }
            if (wY > MIN_DELTA) {
                double d = (bounds.getMaxY() - curPos.getY() + OFFSET_TO_SIDE) / wY;
                if (d > 0.0 && d < minDist) {
                    minDist = d;
                    curFace = BlockFace.UP;
                }
            } else if (wY < -MIN_DELTA) {
                double d = (bounds.getMinY() - curPos.getY() - OFFSET_TO_SIDE) / wY;
                if (d > 0.0 && d < minDist) {
                    minDist = d;
                    curFace = BlockFace.DOWN;
                }
            }
            if (wZ > MIN_DELTA) {
                double d = (bounds.getMaxZ() - curPos.getZ() + OFFSET_TO_SIDE) / wZ;
                if (d > 0.0 && d < minDist) {
                    minDist = d;
                    curFace = BlockFace.SOUTH;
                }
            } else if (wZ < -MIN_DELTA) {
                double d = (bounds.getMinZ() - curPos.getZ() - OFFSET_TO_SIDE) / wZ;
                if (d > 0.0 && d < minDist) {
                    minDist = d;
                    curFace = BlockFace.NORTH;
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
            position.setX(curPos.getX() + curBlock.getX());
            position.setY(curPos.getY() + curBlock.getY());
            position.setZ(curPos.getZ() + curBlock.getZ());
            orientation.setX(curFace.getModX());
            orientation.setY(curFace.getModY());
            orientation.setZ(curFace.getModZ());
        }
    }

    public static boolean snapToCoasterRails(TrackNode selfNode, Vector position, Vector orientation) {
        for (TrackNode nearby : selfNode.getWorld().getTracks().findNodesNear(new ArrayList<TrackNode>(0), position, 0.25)) {
            if (nearby == selfNode) {
                continue;
            }

            Vector nearbyPos = nearby.getPosition();
            Vector nearbyOri = nearby.getOrientation();
            position.setX(nearbyPos.getX());
            position.setY(nearbyPos.getY());
            position.setZ(nearbyPos.getZ());
            orientation.setX(nearbyOri.getX());
            orientation.setY(nearbyOri.getY());
            orientation.setZ(nearbyOri.getZ());
            return true;
        }
        return false;
    }

    public static boolean snapToRails(World world, IntVector3 ignoreNodeBlock, Vector position, Vector direction, Vector orientation) {
        // Snap to normal/other types of rails
        Block positionBlock = world.getBlockAt(position.getBlockX(), position.getBlockY(), position.getBlockZ());
        RailState state = new RailState();
        state.position().posX = position.getX();
        state.position().posY = position.getY();
        state.position().posZ = position.getZ();
        state.position().setMotion(direction);
        state.setRailPiece(RailPiece.create(RailType.NONE, positionBlock));
        if (RailType.loadRailInformation(state)) {
            if (state.railType() instanceof CoasterRailType) {
                return false;
            }

            RailPath path = state.loadRailLogic().getPath();
            RailPath.Position p1 = path.getStartPosition();
            RailPath.Position p2 = path.getEndPosition();
            RailPath.Position inPos = RailPath.Position.fromPosDir(position, orientation);
            p1.makeAbsolute(state.railBlock());
            p2.makeAbsolute(state.railBlock());
            double dsq1 = p1.distanceSquared(inPos);
            double dsq2 = p2.distanceSquared(inPos);
            if (dsq2 < dsq1) {
                dsq1 = dsq2;
                p1 = p2;
            }
            final double SNAP_RADIUS = 0.25;
            if (dsq1 < (SNAP_RADIUS*SNAP_RADIUS)) {
                position.setX(p1.posX);
                position.setY(p1.posY);
                position.setZ(p1.posZ);
                Util.setVector(orientation, p1.orientation.upVector());
                return true;
            }
        }
        return false;
    }

    /**
     * Performs raytracing from the player's field of view, providing information
     * about the block clicked and where on the block was clicked
     * 
     * @param player
     * @return raytrace results, null if not looking at a block
     */
    public static TargetedBlockInfo rayTrace(Player player) {
        Location loc = player.getEyeLocation();
        Vector dir = loc.getDirection();
        Vector start = loc.toVector();
        Vector end = dir.clone().multiply(5.0).add(start);
        MovingObjectPositionHandle mop = WorldHandle.fromBukkit(loc.getWorld()).rayTrace(start, end);
        if (mop == null) {
            return null;
        }

        TargetedBlockInfo info = new TargetedBlockInfo();
        info.position = mop.getPos();
        info.face = mop.getDirection();
        Vector blockCoordPos = info.position.clone().add(dir.clone().multiply(1e-5));
        int x = blockCoordPos.getBlockX();
        int y = blockCoordPos.getBlockY();
        int z = blockCoordPos.getBlockZ();
        info.block = loc.getWorld().getBlockAt(x, y, z);
        info.position.setX(info.position.getX() - x);
        info.position.setY(info.position.getY() - y);
        info.position.setZ(info.position.getZ() - z);
        return info;
    }

    public static class TargetedBlockInfo {
        public Block block;
        public Vector position;
        public BlockFace face;
    }
}
