package com.bergerkiller.bukkit.coasters.rails.single;

import java.util.function.Consumer;

import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;

/**
 * Same as Line but is not consumed when looking up the single-node sections making up
 * a merged representation
 */
class TrackRailsSectionSingleNodeJunctionLine extends TrackRailsSectionSingleNodeLine {

    public TrackRailsSectionSingleNodeJunctionLine(TrackNode node, TrackConnection conn_a, TrackConnection conn_b, boolean primary) {
        super(node, conn_a, conn_b, primary);
    }

    @Override
    public void forEachNodeElement(Consumer<TrackRailsSingleNodeElement> consumer) {
        // Don't consume this one, it's part of the parent junction
    }

    @Override
    public void writeDebugString(StringBuilder builder, String linePrefix) {
        writeDebugString(builder, "JunctionNode", linePrefix);
    }
}
