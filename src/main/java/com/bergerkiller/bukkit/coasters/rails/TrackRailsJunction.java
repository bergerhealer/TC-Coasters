package com.bergerkiller.bukkit.coasters.rails;

import java.util.List;

import com.bergerkiller.bukkit.coasters.rails.multiple.TrackRailsSectionMultipleJunction;
import com.bergerkiller.bukkit.coasters.rails.single.TrackRailsSectionSingleNode;

/**
 * At a rail position a junction is stored
 */
public interface TrackRailsJunction extends TrackRailsSectionsAtRail {

    @Override
    default TrackRailsSectionMultipleJunction merge(TrackRailsSectionsAtRail element) {
        if (element instanceof TrackRailsSection) {
            // Merge the node elements into this junction, populating the branches
            return merge(((TrackRailsSection) element).getSingleNodeSections());
        } else {
            // Not supported, probably is another junction
            return null;
        }
    }

    /**
     * Tries to merge a chain of connected single-node sections into this junction.
     * The chain is connected to existing junction branches unbroken.<br>
     * <br>
     * Can return a new instance to represent both, or the same if this instance
     * can be modified. Returns null if the merge failed, in which case the caller
     * is expected to create a new List to store both separately.<br>
     * <br>
     * If a merge succeeds the original instance and the sections passed to this method
     * must be considered removed afterwards. Both will already be included in the
     * merged result.
     *
     * @param sectionChain Chain of single-node sections
     * @return updated junction information, or null if merging failed
     */
    TrackRailsSectionMultipleJunction merge(List<TrackRailsSectionSingleNode> sectionChain);
}
