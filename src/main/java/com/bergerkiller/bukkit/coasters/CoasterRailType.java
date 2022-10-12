package com.bergerkiller.bukkit.coasters;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
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
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeSign;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.controller.global.SignControllerWorld;
import com.bergerkiller.bukkit.tc.controller.components.RailJunction;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.rails.RailLookup.TrackedSign;
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
        Collection<TrackRailsSection> rails = getRails(pos.getWorld()).findAtBlock(pos);
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
        try {
            if (fromIdx >= 0 && fromIdx < connections.size()) {
                node.switchJunction(connections.get(fromIdx));
            }
            if (toIdx >= 0 && toIdx < connections.size()) {
                node.switchJunction(connections.get(toIdx));
            }
        } finally {
            node.getWorld().getTracks().updateAllWithPriority();
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

    @Override
    public void discoverSigns(RailPiece railPiece, SignControllerWorld signController, List<TrackedSign> result) {
        // Vanilla signs at the node's rail block
        super.discoverSigns(railPiece, signController, result);

        // Find all nodes at this rail piece and add all the signs contained in them
        for (TrackNode node : getNodes(railPiece.block())) {
            for (TrackNodeSign sign : node.getSigns()) {
                result.add(sign.getTrackedSign(railPiece));
            }
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
        final List<TrackRailsSection> rails = getRailSections(state.railBlock());

        // This iterator is only used once, eliminating need to use size()
        final Iterator<TrackRailsSection> railsIter = rails.iterator();
        if (!railsIter.hasNext()) {
            return RailLogicAir.INSTANCE;
        }

        TrackRailsSection section;
        TrackRailsSection firstSection = railsIter.next();
        if (!railsIter.hasNext()) {
            // Only one to pick from, so pick it
            section = firstSection;
        } else {
            final int serverTickThreshold = (CommonUtil.getServerTicks() - 1);

            // If any of the rails in this list were picked last time as well,
            // we ignore all other rails sections bound to the same node.
            // This prevents trains teleporting between paths while traveling
            // over a junction.
            TrackRailsSection preferredLast = null;
            TrackRailsSection preferredNew = null;
            {
                final Vector railPosition = state.railPosition();
                TrackRailsSection pick = firstSection;
                while (true) {
                    // Adds information about the distance (squared) to the
                    // position on the rails.
                    pick.lastDistanceSquared = pick.distanceSq(railPosition);

                    // Check picked once before, and if so, consider it for picking
                    // Then, use comparator to decide whether it is a better pick than
                    // our previous pick, if we had one.
                    if (state.member() != null && pick.isPickedBefore(serverTickThreshold)) {
                        if (preferredLast == null || isBetterSection(pick, preferredLast)) {
                            preferredLast = pick;
                        }
                    } else {
                        if (preferredNew == null || isBetterSection(pick, preferredNew)) {
                            preferredNew = pick;
                        }
                    }

                    // Check for next element. This might be expensive, so
                    // do not do this for the first pick.
                    if (pick != firstSection && !railsIter.hasNext()) {
                        break;
                    }

                    pick = railsIter.next();
                }
            }

            if (preferredLast == null) {
                // No previous preferred section, pick whatever is closest
                section = preferredNew;
            } else {
                // Junction logic: eliminate all non-preferred rails sections that
                // are part of the same junction. This is detected by checking whether
                // the section has a node in common.
                final Set<TrackNode> junctionNodes = preferredLast.getNodes()
                        .filter(n -> n.getConnections().size() > 2)
                        .collect(Collectors.toSet());
                if (junctionNodes.isEmpty()) {
                    // None of these are actual junctions, don't do anything special
                    // Compare the last pick with possible new picks
                    section = (preferredNew != null && isBetterSection(preferredNew, preferredLast))
                            ? preferredNew : preferredLast;
                } else {
                    // Check if any of the alternatives are a better fit
                    // Only allow those not part of the original junction
                    // This is for if two separate tracks are close together
                    final double preferredDistSq = preferredLast.lastDistanceSquared;
                    TrackRailsSection altSectionPick = null;
                    for (TrackRailsSection pick : rails) {
                        // Check if picked before, if so, we already filtered this earlier
                        // Check if below distance threshold of the preferred one
                        if ((state.member() != null && pick.isPickedBefore(serverTickThreshold)) || pick.lastDistanceSquared > preferredDistSq) {
                            continue;
                        }

                        // Check no nodes in common with the preferred section
                        if (pick.getNodes().anyMatch(junctionNodes::contains)) {
                            continue;
                        }

                        // Sort
                        if (altSectionPick == null || isBetterSection(pick, altSectionPick)) {
                            altSectionPick = pick;
                        }
                    }
                    section = (altSectionPick != null) ? altSectionPick : preferredLast;
                }
            }
        }

        return new CoasterRailLogic(section);
    }

    @Override
    public Location getSpawnLocation(Block railsBlock, BlockFace orientation) {
        List<TrackRailsSection> rails = getRails(railsBlock.getWorld()).findAtRails(railsBlock);
        if (rails.isEmpty()) {
            return railsBlock.getLocation().add(0.5, 0.5, 0.5);
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

    /**
     * Used to find the pick with the lowest distance squared
     */
    public static final boolean isBetterSection(TrackRailsSection a, TrackRailsSection b) {
        // When similar enough, but one is primary (junction selected), prefer primary
        // This makes sure junction switching works correctly
        if (a.primary != b.primary && Math.abs(a.lastDistanceSquared - b.lastDistanceSquared) < 1e-3) {
            return a.primary;
        }

        return a.lastDistanceSquared < b.lastDistanceSquared;
    }
}
