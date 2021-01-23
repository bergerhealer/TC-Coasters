package com.bergerkiller.bukkit.coasters.rails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;

/**
 * Stores cached information about a portion of track
 */
public class TrackRailsSection {
    /**
     * Node owner of this rails section
     */
    public final TrackNode node;
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
     * The server tick when this section was last chosen.
     * Is used to make sure junctions within the same block stay functional
     * and don't cause trains to snap to the other junction when crossing it.
     */
    public int tickLastPicked = 0;

    public TrackRailsSection(TrackRailsSection original, RailPath path) {
        this.node = original.node;
        this.rails = original.rails;
        this.path = path;
        this.primary = original.primary;
    }

    public TrackRailsSection(TrackNode node, IntVector3 rails, RailPath path, boolean primary) {
        this.node = node;
        this.rails = rails;
        this.path = path;
        this.primary = primary;
    }

    public BlockFace getMovementDirection() {
        return Util.vecToFace(this.node.getDirection(), false);
    }

    public String getSectionStr() {
        double x1 = rails.x + path.getPoints()[0].x;
        double y1 = rails.y + path.getPoints()[0].y;
        double z1 = rails.z + path.getPoints()[0].z;
        double x2 = rails.x + path.getPoints()[path.getPoints().length - 1].x;
        double y2 = rails.y + path.getPoints()[path.getPoints().length - 1].y;
        double z2 = rails.z + path.getPoints()[path.getPoints().length - 1].z;
        return "[" + x1 + "/" + y1 + "/" + z1 + "      " + x2 + "/" + y2 + "/" + z2 + "]";
    }
    
    public void test(RailState state) {
        CommonUtil.broadcast("ON RAIL " + this.rails);
        
        Vector v = state.railPosition();
        RailPath.Position pos = new RailPath.Position();
        pos.posX = v.getX();
        pos.posY = v.getY();
        pos.posZ = v.getZ();
        this.path.moveRelative(pos, 0.0);

        //System.out.println("RAIL [" + System.identityHashCode(this) + "]   " + pos.posX + "/" + pos.posY + "/" + pos.posZ + "    " +
        //                   pos.motX + "/" + pos.motY + "/" + pos.motZ);
    }

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

    /**
     * Gets a stream of all the track nodes represented in this section of track
     * 
     * @return stream of nodes
     */
    public Stream<TrackNode> getNodes() {
        return Stream.of(this.node);
    }

    /**
     * Gets whether a list of nodes contains a node of this section
     * 
     * @param nodes
     * @return True if a node in the nodes list is part of this section
     */
    public boolean containsNode(Collection<TrackNode> nodes) {
        return nodes.contains(this.node);
    }

    /**
     * Gets whether another section is directly connected with this one
     * 
     * @param section
     * @return True if connected
     */
    public boolean isConnectedWith(TrackRailsSection section) {
        // Disallow non-primary sections as that can break physics
        if (!this.primary) {
            return false;
        }

        // Check a connection exists between this section's node, and the new section's node
        for (TrackConnection connection : this.node.getConnections()) {
            if (connection.getNodeA() == section.node || connection.getNodeB() == section.node) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets all sections represented by this rails section.
     * 
     * @return list of sections
     */
    public List<TrackRailsSection> getAllSections() {
        return Collections.singletonList(this);
    }

    /**
     * Gets the desired spawn location of this track section
     * 
     * @param railBlock
     * @param orientation vector
     * @return spawn location
     */
    public Location getSpawnLocation(Block railBlock, Vector orientation) {
        // Single node, pick node position
        return this.node.getSpawnLocation(orientation);
    }
}
