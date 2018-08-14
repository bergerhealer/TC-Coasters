package com.bergerkiller.bukkit.coasters.rails;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

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

    public TrackRailsSection(TrackNode node, RailPath path, boolean primary) {
        this.node = node;
        this.rails = node.getRailBlock(true);
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

    public double calcCost(RailState state) {
        Vector v = state.railPosition();
        RailPath.Position pos = new RailPath.Position();
        pos.posX = v.getX();
        pos.posY = v.getY();
        pos.posZ = v.getZ();
        this.path.moveRelative(pos, 0.0);
        return MathUtil.distanceSquared(pos.posX, pos.posY, pos.posZ, v.getX(), v.getY(), v.getZ());
    }
}
