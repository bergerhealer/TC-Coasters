package com.bergerkiller.bukkit.coasters.rails.single;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.rails.TrackRailsJunction;
import com.bergerkiller.bukkit.coasters.rails.TrackRailsSection;
import com.bergerkiller.bukkit.coasters.rails.multiple.TrackRailsSectionMultipleJunction;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.common.bases.IntVector3;

/**
 * A rails section which represents a switched node. A junction has
 * one primary route and one or more alternative routes for moving
 * towards the junction.<br>
 * <br>
 * This is a root primitive shape. This means it can only represent the paths of
 * a single junction node. It cannot represent the routes along multiple nodes.
 */
public class TrackRailsSingleJunctionNodeElement implements TrackRailsSingleNodeElement, TrackRailsJunction {
    private final TrackNode node;
    private final IntVector3 rails;
    private final TrackRailsSectionSingleNodeJunctionLine[] options;

    public TrackRailsSingleJunctionNodeElement(TrackNode node) {
        this.node = node;
        this.rails = node.getRailBlock(true);

        List<TrackConnection> connections = node.getConnections();

        // Initialize all the options, which are possible routes taken through the junction
        // The first option is the primary one, which connects the selected junctions together
        // All other ones are remaining junctions leading to the most-straight selected end
        this.options = new TrackRailsSectionSingleNodeJunctionLine[connections.size() - 1];
        this.options[0] = new TrackRailsSectionSingleNodeJunctionLine(
                node, connections.get(0), connections.get(1), true);

        // Pick most-straight paths through the junctions
        Vector dir0 = connections.get(0).getDirection(node);
        Vector dir1 = connections.get(1).getDirection(node);
        for (int i = 2; i < connections.size(); i++) {
            TrackConnection conn = connections.get(i);
            TrackConnection other;
            Vector dir = conn.getDirection(node);
            if (dir0.dot(dir) > dir1.dot(dir)) {
                other = connections.get(1);
            } else {
                other = connections.get(0);
            }
            this.options[i - 1] = new TrackRailsSectionSingleNodeJunctionLine(
                    node, conn, other, false);
        }
    }

    @Override
    public IntVector3 rail() {
        return rails;
    }

    @Override
    public TrackNode node() {
        return node;
    }

    @Override
    public TrackNode getJunctionNode() {
        return node;
    }

    @Override
    public List<? extends TrackRailsSection> options() {
        return Arrays.asList(options);
    }

    @Override
    public void forEachNodeElement(Consumer<TrackRailsSingleNodeElement> consumer) {
        consumer.accept(this);
    }

    @Override
    public void forEachBlockPosition(Consumer<IntVector3> consumer) {
        for (TrackRailsSectionSingleNodeLine line : options) {
            line.forEachBlockPosition(consumer);
        }
    }

    @Override
    public TrackRailsSectionMultipleJunction merge(List<TrackRailsSectionSingleNode> sectionChain) {
        int sectionChainSize = sectionChain.size();
        if (sectionChainSize == 0) {
            return null; // Can't merge nothing
        }

        if (sectionChainSize == 1) {
            // Optimized method for a single element
            TrackRailsSectionSingleNode section = sectionChain.get(0);
            for (TrackNode neighbour : node.getNeighbours()) {
                if (section.node() == neighbour) {
                    TrackRailsSectionMultipleJunction multipleJunc = new TrackRailsSectionMultipleJunction(this);
                    return multipleJunc.merge(Collections.singletonList(section));
                }
            }
        } else {
            TrackNode firstNode = sectionChain.get(0).node();
            TrackNode lastNode = sectionChain.get(sectionChainSize - 1).node();

            // Try to see if the section can be appended to one of the junction neighbours
            // If it can be, produce a new multiple-junction which allows for the junction paths to
            // be modified/extended.
            for (TrackNode neighbour : node.getNeighbours()) {
                if (firstNode == neighbour || lastNode == neighbour) {
                    TrackRailsSectionMultipleJunction multipleJunc = new TrackRailsSectionMultipleJunction(this);
                    return multipleJunc.merge(sectionChain);
                }
            }
        }

        return null;
    }

    @Override
    public void writeDebugString(StringBuilder builder, String linePrefix) {
        builder.append(linePrefix).append("Junction [\n");
        for (TrackRailsSectionSingleNodeJunctionLine option : options) {
            option.writeDebugString(builder, linePrefix + "  ");
            builder.append('\n');
        }
        builder.append(linePrefix).append("]");
    }
}
