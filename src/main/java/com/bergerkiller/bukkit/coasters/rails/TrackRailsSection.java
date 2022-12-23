package com.bergerkiller.bukkit.coasters.rails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.rails.single.TrackRailsSectionSingleNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.world.CoasterWorld;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;

import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;

/**
 * Stores cached information about a portion of track
 */
public abstract class TrackRailsSection implements TrackRailsSectionsAtRail {
    /**
     * When below this distance, use tickLastPicked to decide whether to select
     * this section over another
     */
    private static final double PICK_MIN_DIST_SQ = (0.4 * 0.4);

    /**
     * The block coordinates of the rails block for this section.
     * This is the block where signs are activated.
     */
    public final IntVector3 rails;
    /**
     * The path along which Minecart move over this section
     */
    public final RailPath path;
    /**
     * The path is a primary path (selected junction)
     */
    public final boolean primary;
    /**
     * List of members that were last driving over this rails section
     */
    private List<LastPickedInfo> lastPicked = Collections.emptyList();
    /**
     * Last-calculated distance between this section and a given point position,
     * during lookup. Has no meaningful use outside of CoasterRailType.getLogic.
     */
    public double lastDistanceSquared = 0.0;

    public TrackRailsSection(TrackRailsSection original, RailPath path) {
        this(original.rails, path, original.primary);
    }

    public TrackRailsSection(IntVector3 rails, RailPath path, boolean primary) {
        this.rails = rails;
        this.path = path;
        this.primary = primary;
    }

    @Override
    public IntVector3 rail() {
        return rails;
    }

    @Override
    public List<TrackRailsSection> options() {
        return path.isEmpty() ? Collections.emptyList() : Collections.singletonList(this);
    }

    /**
     * Obtains a List of single-node rails sections that make up this rails section.
     * If this section is itself a single-node rails section, returns a singleton list
     * of this one element.
     *
     * @return List of single-node rails sections making up this rails section
     */
    public abstract List<TrackRailsSectionSingleNode> getSingleNodeSections();

    public abstract BlockFace getMovementDirection();

    public double distanceSq(Vector railPosition) {
        RailPath.Position pos = new RailPath.Position();
        pos.relative = true;
        pos.posX = railPosition.getX();
        pos.posY = railPosition.getY();
        pos.posZ = railPosition.getZ();
        this.path.moveRelative(pos, 0.0);
        return MathUtil.distanceSquared(pos.posX, pos.posY, pos.posZ,
                                        railPosition.getX(), railPosition.getY(), railPosition.getZ());
    }

    //TODO: Migrate to traincarts RailPath
    protected Vector findAbsoluteSectionPos(Function<RailPath, RailPath.Position> posFunc, Vector fallbackPos) {
        if (path.isEmpty()) {
            return fallbackPos;
        } else {
            RailPath.Position pos = posFunc.apply(path);
            if (pos.relative) {
                return new Vector(rails.x + pos.posX, rails.y + pos.posY, rails.z + pos.posZ);
            } else {
                return new Vector(pos.posX, pos.posY, pos.posZ);
            }
        }
    }

    /**
     * Marks this particular path section as having been used by a particular member.
     * Future logic checks will prefer this section over other ones if they match
     * equally.
     *
     * @param world CoasterWorld this rails section is at
     * @param member
     */
    public void setPickedByMember(CoasterWorld world, MinecartMember<?> member) {
        LastPickedInfo info = findLastPickedInfo(member);
        if (info == null) {
            info = new LastPickedInfo(member);
            if (lastPicked.isEmpty()) {
                lastPicked = new ArrayList<>(4);
                world.getRails().lastPickedSections.add(this); // Background cleanup
            }
            lastPicked.add(info);
        } else {
            info.pick();
        }
    }

    /**
     * Gets whether this section was picked by a member before in the recent past
     *
     * @param member
     * @param serverTickThreshold
     * @return True if picked before by that member
     */
    public boolean isPickedBefore(MinecartMember<?> member, int serverTickThreshold) {
        if (this.lastDistanceSquared < PICK_MIN_DIST_SQ) {
            LastPickedInfo info = findLastPickedInfo(member);
            return info != null && info.isPicked(serverTickThreshold);
        } else {
            return false;
        }
    }

    /**
     * Removes picked-before entries that have timed out
     *
     * @param serverTickThreshold
     * @return True if all picked-before entries have been removed and no more
     *         background cleanup is needed. False if some remain.
     */
    public boolean cleanupPickedBefore(int serverTickThreshold) {
        boolean empty = true;
        for (Iterator<LastPickedInfo> iter = lastPicked.iterator(); iter.hasNext();) {
            LastPickedInfo info = iter.next();
            if (info.isPicked(serverTickThreshold)) {
                empty = false;
            } else {
                iter.remove();
            }
        }
        if (empty) {
            lastPicked = Collections.emptyList(); // Memory cleanup
        }
        return empty;
    }

    /**
     * Sections picked a tick by this value or later are considered picked before
     *
     * @return picked before server tick threshold
     */
    public static int getPickServerTickThreshold() {
        return CommonUtil.getServerTicks() - 1;
    }

    private LastPickedInfo findLastPickedInfo(MinecartMember<?> member) {
        for (LastPickedInfo info : lastPicked) {
            if (info.member == member) {
                return info;
            }
        }
        return null;
    }

    /**
     * Gets a stream of all the track nodes represented in this section of track
     * 
     * @return stream of nodes
     */
    public abstract Stream<TrackNode> getNodes();

    /**
     * Gets whether a list of nodes contains a node of this section
     * 
     * @param nodes
     * @return True if a node in the nodes list is part of this section
     */
    public abstract boolean containsNode(Collection<TrackNode> nodes);

    /**
     * Attempts to append a single-node section to either the start or the end of this section.
     * If successful, returns a new section of the combined path. If not, returns null.
     *
     * @param section
     * @return Combined section, or null if combining was not possible
     */
    public abstract TrackRailsSection appendToChain(TrackRailsSectionSingleNode section);

    /**
     * Attempts to append multiple single-node sections to either the start or the end of this
     * section. If successful, returns a new section of the combined path. If not, returns null.
     *
     * @param sectionChain Chain of single-node sections
     * @return Combined section, or null if combining was not possible
     */
    public abstract TrackRailsSection appendToChain(List<TrackRailsSectionSingleNode> sectionChain);

    /**
     * Gets the desired spawn location of this track section
     * 
     * @param railBlock
     * @param orientation vector
     * @return spawn location
     */
    public abstract Location getSpawnLocation(Block railBlock, Vector orientation);

    /**
     * When a Minecart Member drives over rails, this object is created to track when
     * and for what section this happened. This makes sure that when two ambiguous sections
     * must be picked between, it always picks the one the member was using before.
     * This prevents unwanted teleporting between paths through junctions.
     */
    private static final class LastPickedInfo {
        /** Member that is picked */
        public final MinecartMember<?> member;

        /**
         * The server tick when this section was last chosen.
         * Is used to make sure junctions within the same block stay functional
         * and don't cause trains to snap to the other junction when crossing it.
         */
        public int tickLastPicked;

        public LastPickedInfo(MinecartMember<?> member) {
            this.member = member;
            this.tickLastPicked = CommonUtil.getServerTicks();
        }

        public void pick() {
            tickLastPicked = CommonUtil.getServerTicks();
        }

        public boolean isPicked(int serverTickThreshold) {
            return tickLastPicked >= serverTickThreshold;
        }
    }

}
