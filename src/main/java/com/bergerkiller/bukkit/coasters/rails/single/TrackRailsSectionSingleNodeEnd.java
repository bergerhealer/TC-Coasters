package com.bergerkiller.bukkit.coasters.rails.single;

import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.TCCoastersUtil;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.common.bases.IntVector3;

/**
 * Track rails section that connects to one node only. Only for nodes at the end
 * of the track.
 */
class TrackRailsSectionSingleNodeEnd extends TrackRailsSectionSingleNode {
    public final TrackNode neighbour;

    public TrackRailsSectionSingleNodeEnd(TrackNode node) {
        super(node, node.getRailBlock(true), node.buildPath(), true);

        List<TrackConnection> connections = node.getConnections();
        this.neighbour = connections.get(0).getOtherNode(node);
    }

    @Override
    public boolean connectsWithNode(TrackNode node) {
        return this.neighbour == node;
    }

    @Override
    public void forEachBlockPosition(final Consumer<IntVector3> consumer) {
        // For dead-end nodes we must ignore the blocks beyond the node
        // Otherwise the node cannot be 'exited' to other rail types or air

        // Figure out the block occupied by the node itself
        TrackNode node = this.node();
        IntVector3 posBlock = node.getPositionBlock();
        Vector dir = node.getDirection();
        final HashSet<IntVector3> ignoredBlocks = new HashSet<IntVector3>();

        // These are the deltas in the opposite direction
        int[] dx_values = TCCoastersUtil.getBlockDeltas(-dir.getX());
        int[] dy_values = TCCoastersUtil.getBlockDeltas(-dir.getY());
        int[] dz_values = TCCoastersUtil.getBlockDeltas(-dir.getZ());

        // Do not register for all blocks relative to the node in the opposite direction
        for (int dx : dx_values) {
            for (int dy : dy_values) {
                for (int dz : dz_values) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        ignoredBlocks.add(posBlock.add(dx, dy, dz));
                    }
                }
            }
        }

        // Consume all block positions, filter those that should be ignored
        super.forEachBlockPosition(block -> {
            if (!ignoredBlocks.contains(block)) {
                consumer.accept(block);
            }
        });
    }
}
