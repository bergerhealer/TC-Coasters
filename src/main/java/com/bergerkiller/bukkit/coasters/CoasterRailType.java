package com.bergerkiller.bukkit.coasters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.rails.TrackRailsSection;
import com.bergerkiller.bukkit.coasters.rails.TrackRailsWorld;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.controller.components.RailJunction;
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
        return !getRails(world).findAtRails(x, y, z).isEmpty();
    }

    @Override
    public boolean onBlockCollision(MinecartMember<?> member, Block railsBlock, final Block hitBlock, BlockFace hitFace) {
        return false;
    }

    @Override
    public List<Block> findRails(Block pos) {
        List<TrackRailsSection> rails = getRails(pos.getWorld()).findAtBlock(pos);
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
        List<TrackRailsSection> rails = getRails(trackBlock.getWorld()).findAtRails(trackBlock);
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
        List<TrackRailsSection> rails = getRails(trackBlock.getWorld()).findAtRails(trackBlock);
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
    public List<RailJunction> getJunctions(Block railBlock) {
        List<TrackRailsSection> rails = getRails(railBlock.getWorld()).findAtRails(railBlock);
        if (rails.isEmpty()) {
            return super.getJunctions(railBlock);
        } else {
            return rails.get(0).node.getJunctions();
        }
    }

    @Override
    public void switchJunction(Block railBlock, RailJunction from, RailJunction to) {
        List<TrackRailsSection> rails = getRails(railBlock.getWorld()).findAtRails(railBlock);
        if (rails.isEmpty()) {
            return;
        }
        int fromIdx = (from == null) ? -1 : (ParseUtil.parseInt(from.name(), 0) - 1);
        int toIdx = (to == null) ? -1 : (ParseUtil.parseInt(to.name(), 0) - 1);
        TrackNode node = rails.get(0).node;
        List<TrackConnection> connections = node.getSortedConnections();
        if (fromIdx >= 0 && fromIdx < connections.size()) {
            node.switchJunction(connections.get(fromIdx));
        }
        if (toIdx >= 0 && toIdx < connections.size()) {
            node.switchJunction(connections.get(toIdx));
        }
    }

    @Override
    public BlockFace getDirection(Block railsBlock) {
        List<TrackRailsSection> rails = getRails(railsBlock.getWorld()).findAtRails(railsBlock);
        if (!rails.isEmpty()) {
            return rails.get(0).getMovementDirection();
        }
        return BlockFace.DOWN;
    }

    @Override
    public BlockFace getSignColumnDirection(Block railsBlock) {
        return BlockFace.DOWN;
    }

    /**
     * Gets all the track nodes active at a rail block
     * 
     * @param railBlock
     * @return list of track nodes
     */
    public List<TrackNode> getNodes(Block railBlock) {
        List<TrackRailsSection> sections = getRailSections(railBlock);
        if (sections.isEmpty()) {
            return Collections.emptyList();
        } else {
            return sections.stream().flatMap(section -> section.getNodes()).collect(Collectors.toList());
        }
    }

    /**
     * Gets all the rail sections active at a rail block
     * 
     * @param railBlock
     * @return list of rail sections
     */
    public List<TrackRailsSection> getRailSections(Block railBlock) {
        return getRails(railBlock.getWorld()).findAtRails(railBlock);
    }

    @Override
    public RailLogic getLogic(RailState state) {
        List<TrackRailsSection> rails = getRailSections(state.railBlock());
        if (rails.isEmpty()) {
            return RailLogicAir.INSTANCE;
        }

        int serverTicks = CommonUtil.getServerTicks();
        TrackRailsSection section = rails.get(0);
        if (rails.size() >= 2) {
            final double distSqResolution = (0.4*0.4);
            Vector railPosition = state.railPosition();
            double sectionDistSq = section.distanceSq(railPosition);
            for (int i = 1; i < rails.size(); i++) {
                TrackRailsSection other = rails.get(i);
                double otherDistSq = other.distanceSq(railPosition);

                // When below a movement resolution, check which section we picked previously by tracking server ticks
                // This makes sure a train continues using a junction it was already using
                // If this is above the threshold, instead check which section is closer to the train
                boolean isBelowResolution = (state.member() != null && sectionDistSq < distSqResolution && otherDistSq < distSqResolution);
                if (isBelowResolution ?
                        (other.tickLastPicked > section.tickLastPicked && other.tickLastPicked >= (serverTicks-1)) :
                        (otherDistSq < sectionDistSq))
                {
                    section = other;
                    sectionDistSq = otherDistSq;
                }
            }
        }
        if (state.member() != null) {
            section.tickLastPicked = serverTicks;
        }
        return new CoasterRailLogic(section);
    }

    @Override
    public Location getSpawnLocation(Block railsBlock, BlockFace orientation) {
        List<TrackRailsSection> rails = getRails(railsBlock.getWorld()).findAtRails(railsBlock);
        if (rails.isEmpty()) {
            return super.getSpawnLocation(railsBlock, orientation);
        } else {
            // Compute the spawn location when a single rails section exists
            Vector orientationVec = FaceUtil.faceToVector(orientation);
            Iterator<TrackRailsSection> iter = rails.iterator();
            Location spawnLoc = iter.next().getSpawnLocation(railsBlock, orientationVec);

            // Pick the rails section spawn location that is nearest to the rails block
            // This way it remains possible to dictate where is spawned using locality
            if (iter.hasNext()) {
                Location railsPos = railsBlock.getLocation().add(0.5, 0.5, 0.5);
                double lowestDistanceSq = spawnLoc.distanceSquared(railsPos);
                while (iter.hasNext()) {
                    Location loc = iter.next().getSpawnLocation(railsBlock, orientationVec);
                    double distSq = loc.distance(railsPos);
                    if (distSq < lowestDistanceSq) {
                        lowestDistanceSq = distSq;
                        spawnLoc = loc;
                    }
                }
            }

            return spawnLoc;
        }
    }

    private final TrackRailsWorld getRails(World world) {
        return this.plugin.getCoasterWorld(world).getRails();
    }
}
