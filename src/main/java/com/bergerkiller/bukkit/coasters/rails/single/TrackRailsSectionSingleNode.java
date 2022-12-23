package com.bergerkiller.bukkit.coasters.rails.single;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.rails.TrackRailsSection;
import com.bergerkiller.bukkit.coasters.rails.TrackRailsSectionsAtRail;
import com.bergerkiller.bukkit.coasters.rails.multiple.TrackRailsSectionMultipleLinked;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.util.RailSectionBlockIterator;
import com.bergerkiller.bukkit.coasters.world.CoasterWorld;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.Util;

/**
 * Path information tied to a single node only. Can be either a section of track connecting
 * to one other node (end) or two nodes (line). Allows merging with other sections of track.
 * Also used for the different option paths of a junction node.
 */
public abstract class TrackRailsSectionSingleNode extends TrackRailsSection implements TrackRailsSingleNodeElement {
    private final TrackNode node;

    protected TrackRailsSectionSingleNode(TrackNode node, IntVector3 rails, RailPath path, boolean primary) {
        super(rails, path, primary);
        this.node = node;
    }

    @Override
    public TrackNode node() {
        return node;
    }

    @Override
    public CoasterWorld getWorld() {
        return node.getWorld();
    }

    @Override
    public TrackNode getJunctionNode() {
        return null;
    }

    @Override
    public Stream<TrackNode> getNodes() {
        return Stream.of(node);
    }

    @Override
    public List<TrackRailsSectionSingleNode> getSingleNodeSections() {
        return Collections.singletonList(this);
    }

    @Override
    public boolean containsNode(Collection<TrackNode> nodes) {
        return nodes.contains(node);
    }

    @Override
    public void forEachNodeElement(Consumer<TrackRailsSingleNodeElement> consumer) {
        consumer.accept(this);
    }

    /**
     * Gets whether this single-node rails section can connect with a node. If the node
     * is part of this section itself, it returns false.
     *
     * @param node
     * @return True if this section connects with the node specified
     */
    public abstract boolean connectsWithNode(TrackNode node);

    @Override
    public TrackRailsSectionMultipleLinked appendToChain(TrackRailsSectionSingleNode section) {
        // If a neighbour connects with this section, create a new linked section
        if (this.connectsWithNode(section.node())) {
            ArrayList<TrackRailsSectionSingleNode> sections = new ArrayList<>(2);
            sections.add(this);
            sections.add(section);
            return new TrackRailsSectionMultipleLinked(rails, sections, primary);
        }

        // Merge failed
        return null;
    }

    @Override
    public TrackRailsSectionMultipleLinked appendToChain(List<TrackRailsSectionSingleNode> sectionChain) {
        int numSections = sectionChain.size();
        if (numSections == 1) {
            return appendToChain(sectionChain.get(0)); // Easier...
        }

        if (numSections > 0) { // Probably not needed
            if (this.connectsWithNode(sectionChain.get(0).node)) {
                // Append to start
                ArrayList<TrackRailsSingleNodeElement> newSections = new ArrayList<>(numSections + 1);
                newSections.add(this);
                newSections.addAll(sectionChain);
                return new TrackRailsSectionMultipleLinked(rails, sectionChain, primary);
            } else if (this.connectsWithNode(sectionChain.get(numSections - 1).node)) {
                // Append to end
                ArrayList<TrackRailsSingleNodeElement> newSections = new ArrayList<>(numSections + 1);
                newSections.addAll(sectionChain);
                newSections.add(this);
                return new TrackRailsSectionMultipleLinked(rails, sectionChain, primary);
            }   
        }

        return null;
    }

    @Override
    public TrackRailsSectionsAtRail merge(TrackRailsSectionsAtRail element) {
        // If element is also a single node element, we might be able to combine it into this one
        if (element instanceof TrackRailsSectionSingleNode) {
            return appendToChain((TrackRailsSectionSingleNode) element);
        }

        // Probably a junction element, let that one handle it
        return element.merge(this);
    }

    @Override
    public BlockFace getMovementDirection() {
        return Util.vecToFace(node.getDirection(), false);
    }

    @Override
    public Location getSpawnLocation(Block railBlock, Vector orientation) {
        return node.getSpawnLocation(orientation);
    }

    @Override
    public void forEachBlockPosition(Consumer<IntVector3> consumer) {
        // For all segments of the path, store the block positions being covered in the lookup table
        for (RailPath.Segment segment : path.getSegments()) {
            RailSectionBlockIterator iter = new RailSectionBlockIterator(segment, rails);
            do {
                consumer.accept(iter.block());
                iter.around(0.4).forEach(consumer);
            } while (iter.next());
            iter.aroundEnd(0.4).forEach(consumer);
        }
    }

    @Override
    public void writeDebugString(StringBuilder builder, String linePrefix) {
        writeDebugString(builder, "Node", linePrefix);
    }

    protected void writeDebugString(StringBuilder builder, String name, String linePrefix) {
        Vector pos = node.getPosition();
        Vector p0 = path.getStartPosition().toLocation(rails.toBlock(node.getBukkitWorld())).toVector();
        Vector p1 = path.getEndPosition().toLocation(rails.toBlock(node.getBukkitWorld())).toVector();

        builder.append(linePrefix);
        if (!primary) {
            builder.append("Secondary");
        }
        builder.append(name).append("{\n")
               .append(linePrefix).append("    nodePos: ").append(MathUtil.round(pos.getX(), 3)).append(' ')
                                                          .append(MathUtil.round(pos.getY(), 3)).append(' ')
                                                          .append(MathUtil.round(pos.getZ(), 3)).append("\n")
               .append(linePrefix).append("    len: ").append(path.getTotalDistance()).append('\n')
               .append(linePrefix).append("    p0: ").append(MathUtil.round(p0.getX(), 3)).append(' ')
                                                     .append(MathUtil.round(p0.getY(), 3)).append(' ')
                                                     .append(MathUtil.round(p0.getZ(), 3)).append("\n")
               .append(linePrefix).append("    p1: ").append(MathUtil.round(p1.getX(), 3)).append(' ')
                                                     .append(MathUtil.round(p1.getY(), 3)).append(' ')
                                                     .append(MathUtil.round(p1.getZ(), 3)).append("\n")
               .append(linePrefix).append("}");
    }
}
