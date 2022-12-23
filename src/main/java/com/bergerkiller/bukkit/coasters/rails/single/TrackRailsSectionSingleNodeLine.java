package com.bergerkiller.bukkit.coasters.rails.single;

import java.util.List;

import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;

/**
 * Track rails section that connects to two other nodes. Only for nodes with exactly
 * two connections.
 */
class TrackRailsSectionSingleNodeLine extends TrackRailsSectionSingleNode {
    public final TrackNode neighbour_a, neighbour_b;

    public TrackRailsSectionSingleNodeLine(TrackNode node) {
        super(node, node.getRailBlock(true), node.buildPath(), true);

        List<TrackConnection> connections = node.getConnections();
        this.neighbour_a = connections.get(0).getOtherNode(node);
        this.neighbour_b = connections.get(1).getOtherNode(node);
    }

    public TrackRailsSectionSingleNodeLine(TrackNode node, TrackConnection conn_a, TrackConnection conn_b, boolean primary) {
        super(node, node.getRailBlock(true), node.buildPath(conn_a, conn_b), primary);

        this.neighbour_a = conn_a.getOtherNode(node);
        this.neighbour_b = conn_b.getOtherNode(node);
    }

    @Override
    public boolean connectsWithNode(TrackNode node) {
        return this.neighbour_a == node || this.neighbour_b == node;
    }
}
