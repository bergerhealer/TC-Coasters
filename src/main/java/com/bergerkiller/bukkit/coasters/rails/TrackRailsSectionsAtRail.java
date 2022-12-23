package com.bergerkiller.bukkit.coasters.rails;

import java.util.List;
import java.util.function.Consumer;

import com.bergerkiller.bukkit.coasters.rails.single.TrackRailsSingleNodeElement;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.common.bases.IntVector3;

/**
 * Accesses all the rail section information for a particular Rail Block.
 * Includes logic for merging multiple connected sections into one,
 * also handling the more complex logic at switched nodes.
 */
public interface TrackRailsSectionsAtRail {
    /**
     * Obtains the rail block coordinates
     *
     * @return rail
     */
    IntVector3 rail();

    /**
     * Obtains all unique rail section options at this rail block
     *
     * @return rail sections at this rail block
     */
    List<? extends TrackRailsSection> options();

    /**
     * Gets the Track Node that represents a junction at the rail block.
     * Returns null if there is no junction.
     */
    TrackNode getJunctionNode();

    /**
     * Passes all single-node track rail elements to a consumer that were assigned
     * to this rail block. {@link #options()} contains merged representations
     * of these.
     *
     * @param consumer The consumer to accept all the single-node elements
     */
    void forEachNodeElement(Consumer<TrackRailsSingleNodeElement> consumer);

    /**
     * Outputs a debugging string representation of all the information stored at this
     * rail coordinate.
     *
     * @return debug string
     */
    default String debugString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Rail").append(rail()).append(" ");
        writeDebugString(builder, "");
        return builder.toString();
    }

    /**
     * Outputs a debugging string representation of all the information stored at this
     * rail coordinate.
     *
     * @param builder String Builder to fill with debugging text
     * @param linePrefix Prefix (of spaces) to put at the start of every line outputted
     * @return debug string
     */
    void writeDebugString(StringBuilder builder, String linePrefix);

    /**
     * Tries to include a different element into this by-rail information.
     * Can return a new instance to represent both, or the same if this instance
     * can be modified. Returns null if the merge failed, in which case the caller
     * is expected to create a new List to store both separately.<br>
     * <br>
     * If a merge succeeds the original instance and the element passed to this method
     * must be considered removed afterwards. Both will already be included in the
     * merged result.
     *
     * @param element Single-node element to merge into this one
     * @return updated information, or null if merging failed
     */
    TrackRailsSectionsAtRail merge(TrackRailsSectionsAtRail element);
}
