package com.bergerkiller.bukkit.coasters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.rails.TrackRailsSection;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailLogicState;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicAir;
import com.bergerkiller.bukkit.tc.rails.type.RailType;

public class CoasterRailType extends RailType {
    private final TCCoasters plugin;

    public CoasterRailType(TCCoasters plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isRail(BlockData blockData) {
        return false;
    }

    @Override
    public boolean isRail(World world, int x, int y, int z) {
        return !this.plugin.getRails(world).findAtRails(x, y, z).isEmpty();
    }

    @Override
    public boolean onBlockCollision(MinecartMember<?> member, Block railsBlock, final Block hitBlock, BlockFace hitFace) {
        return false;
    }

    @Override
    public List<Block> findRails(Block pos) {
        List<TrackRailsSection> rails = this.plugin.getRails(pos.getWorld()).findAtBlock(pos);
        if (rails.isEmpty()) {
            return Collections.emptyList();
        } else {
            ArrayList<Block> railsBlocks = new ArrayList<Block>(rails.size());
            for (TrackRailsSection rail : rails) {
                railsBlocks.add(BlockUtil.getBlock(pos.getWorld(), rail.rails));
            }
            return railsBlocks;
        }
    }

    @Override
    public Block findMinecartPos(Block trackBlock) {
        List<TrackRailsSection> rails = this.plugin.getRails(trackBlock.getWorld()).findAtRails(trackBlock);
        if (!rails.isEmpty()) {
            RailPath.Point[] points = rails.get(0).path.getPoints();
            RailPath.Point mid = null;
            if (points.length == 1) {
                mid = points[0];
            } else if (points.length >= 2) {
                mid = points[points.length / 2];
            }
            if (mid != null) {
                return trackBlock.getWorld().getBlockAt(
                        MathUtil.floor(mid.x), MathUtil.floor(mid.y), MathUtil.floor(mid.z));
            }
        }
        return trackBlock;
    }

    @Override
    public BlockFace[] getPossibleDirections(Block trackBlock) {
        List<TrackRailsSection> rails = this.plugin.getRails(trackBlock.getWorld()).findAtRails(trackBlock);
        if (!rails.isEmpty()) {
            RailPath.Point[] points = rails.get(0).path.getPoints();
            if (points.length >= 2) {
                RailPath.Point first = points[0];
                RailPath.Point mid = points[points.length / 2];
                RailPath.Point last = points[points.length - 1];
                BlockFace[] result = new BlockFace[2];
                result[0] = FaceUtil.getDirection(first.x-mid.x, first.z-mid.z, false);
                result[1] = FaceUtil.getDirection(last.x-mid.x, last.z-mid.z, false);
                return result;
            }
        }
        return new BlockFace[0];
    }

    @Override
    public Block getNextPos(Block currentTrack, BlockFace currentDirection) {
        return null;
    }

    @Override
    public BlockFace getDirection(Block railsBlock) {
        List<TrackRailsSection> rails = this.plugin.getRails(railsBlock.getWorld()).findAtRails(railsBlock);
        if (!rails.isEmpty()) {
            return rails.get(0).getMovementDirection();
        }
        return BlockFace.DOWN;
    }

    @Override
    public BlockFace getSignColumnDirection(Block railsBlock) {
        return BlockFace.DOWN;
    }

    @Override
    public RailLogic getLogic(RailLogicState state) {
        List<TrackRailsSection> rails = this.plugin.getRails(state.getRailsBlock().getWorld()).findAtRails(state.getRailsBlock());
        if (rails.size() >= 1) {
            TrackRailsSection section = rails.get(0);
            if (rails.size() >= 2) {
                double minCost = section.calcCost(state);
                for (int i = 1; i < rails.size(); i++) {
                    TrackRailsSection other = rails.get(i);
                    double cost = other.calcCost(state);
                    if (cost < minCost) {
                        minCost = cost;
                        section = other;
                    }
                }
            }
            //section.test(state);
            return new CoasterRailLogic(section);
        }
        return RailLogicAir.INSTANCE;
    }

    @Override
    public Location getSpawnLocation(Block railsBlock, BlockFace orientation) {
        List<TrackRailsSection> rails = this.plugin.getRails(railsBlock.getWorld()).findAtRails(railsBlock);
        if (rails.isEmpty()) {
            return super.getSpawnLocation(railsBlock, orientation);
        } else {
            return rails.get(0).node.getSpawnLocation(FaceUtil.faceToVector(orientation));
        }
    }
}
