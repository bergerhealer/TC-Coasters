package com.bergerkiller.bukkit.coasters.rails.single;

import java.util.function.Consumer;

import com.bergerkiller.bukkit.coasters.rails.TrackRailsSectionsAtRail;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.common.bases.IntVector3;

/**
 * Represents a single node element, which can be a line segment, end segment
 * or a junction. Always represents the details of a single node.
 */
public interface TrackRailsSingleNodeElement extends TrackRailsSectionsAtRail {

    /**
     * Gets the node for which this single node element is
     *
     * @return node
     */
    TrackNode node();

    /**
     * Iterates all the block positions that this single node element becomes
     * active inside of. Some block positions might be consumed more than
     * once.<br>
     * <br>
     * Is used to map single-node sections to block positions in the world.
     *
     * @param consumer Consumer accepting the block positions
     */
    void forEachBlockPosition(Consumer<IntVector3> consumer);

    /**
     * Creates the single-node rail element for a node
     *
     * @param node Node
     * @return rails node elemnt for the node. Returns null if the node is
     *         free-standing and has no effect on trains/the world.
     */
    public static TrackRailsSingleNodeElement create(TrackNode node) {
        // Handle the basic line/end segments
        int numConnections = node.getConnections().size();
        if (numConnections == 0) {
            return null; // Should not be added at all
        } else if (numConnections == 1) {
            return new TrackRailsSectionSingleNodeEnd(node);
        } else if (numConnections == 2) {
            return new TrackRailsSectionSingleNodeLine(node);
        } else {
            return new TrackRailsSingleJunctionNodeElement(node);
        }
    }
}
