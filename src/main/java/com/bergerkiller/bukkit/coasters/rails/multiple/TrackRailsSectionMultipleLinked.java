package com.bergerkiller.bukkit.coasters.rails.multiple;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.rails.TrackRailsJunction;
import com.bergerkiller.bukkit.coasters.rails.TrackRailsSection;
import com.bergerkiller.bukkit.coasters.rails.TrackRailsSectionsAtRail;
import com.bergerkiller.bukkit.coasters.rails.single.TrackRailsSingleNodeElement;
import com.bergerkiller.bukkit.coasters.rails.single.TrackRailsSectionSingleNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.world.CoasterWorld;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;

/**
 * Links multiple single-node sections together with a single path.
 * Always contains at least two single-node sections, otherwise using this
 * makes no sense.
 */
public class TrackRailsSectionMultipleLinked extends TrackRailsSection implements TrackRailsSectionsAtRail {
    public final List<TrackRailsSectionSingleNode> sections;

    public TrackRailsSectionMultipleLinked(IntVector3 rails, List<TrackRailsSectionSingleNode> sections, boolean primary) {
        super(rails, combineRailPaths(sections), primary);
        this.sections = sections;
    }

    @Override
    public List<TrackRailsSectionSingleNode> getSingleNodeSections() {
        return sections;
    }

    @Override
    public Stream<TrackNode> getNodes() {
        return this.sections.stream().map(TrackRailsSectionSingleNode::node);
    }

    @Override
    public CoasterWorld getWorld() {
        return sections.get(0).getWorld();
    }

    @Override
    public boolean containsNode(Collection<TrackNode> nodes) {
        for (TrackRailsSingleNodeElement section : this.sections) {
            if (nodes.contains(section.node())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void forEachNodeElement(Consumer<TrackRailsSingleNodeElement> consumer) {
        for (TrackRailsSectionSingleNode section : sections) {
            section.forEachNodeElement(consumer); // Passes junction element for junction paths
        }
    }

    @Override
    public TrackNode getJunctionNode() {
        return null;
    }

    @Override
    public TrackRailsSectionMultipleLinked appendToChain(TrackRailsSectionSingleNode section) {
        int numSections = sections.size();
        TrackNode node = section.node();
        if (sections.get(0).connectsWithNode(node)) {
            return handleMerge(numSections + 1, list -> {
                list.add(section);
                list.addAll(sections);
            });
        } else if (sections.get(numSections - 1).connectsWithNode(node)) {
            return handleMerge(numSections + 1, list -> {
                list.addAll(sections);
                list.add(section);
            });
        } else {
            return null;
        }
    }

    @Override
    public TrackRailsSectionMultipleLinked appendToChain(List<TrackRailsSectionSingleNode> sectionsToAppend) {
        int numSectionsToMerge = sectionsToAppend.size();
        if (numSectionsToMerge == 1) {
            return appendToChain(sectionsToAppend.get(0)); // Easier
        } else if (numSectionsToMerge == 0) {
            return null;
        }

        TrackNode firstNodeToMerge = sectionsToAppend.get(0).node();
        TrackNode lastNodeToMerge = sectionsToAppend.get(numSectionsToMerge - 1).node();

        int numSections = sections.size();
        TrackRailsSectionSingleNode firstSection = sections.get(0);
        TrackRailsSectionSingleNode lastSection = sections.get(numSections - 1);

        if (firstSection.connectsWithNode(firstNodeToMerge)) {
            // First node to be merged connects with the first node of this linked segment
            // Add the sections to merge in reverse, then add self-sections
            return handleMerge(numSections + numSectionsToMerge, list -> {
                for (int i = sectionsToAppend.size() - 1; i >= 0; --i) {
                    list.add(sectionsToAppend.get(i));
                }
                list.addAll(sections);
            });
        } else if (firstSection.connectsWithNode(lastNodeToMerge)) {
            // Last node to be merged connects with the first node of this linked segment
            // Add them together in the same order, one after the other
            return handleMerge(numSections + numSectionsToMerge, list -> {
                list.addAll(sectionsToAppend);
                list.addAll(sections);
            });
        } else if (lastSection.connectsWithNode(firstNodeToMerge)) {
            // First node to be merged connects with the last node of this linked segment
            // Add them together in the same order, one after the other
            return handleMerge(numSections + numSectionsToMerge, list -> {
                list.addAll(sections);
                list.addAll(sectionsToAppend);
            });
        } else if (lastSection.connectsWithNode(lastNodeToMerge)) {
            // Last node to be merged connects with the last node of this linked segment
            // Add all self-sections, then add the sections to merge in reverse
            return handleMerge(numSections + numSectionsToMerge, list -> {
                list.addAll(sections);
                for (int i = sectionsToAppend.size() - 1; i >= 0; --i) {
                    list.add(sectionsToAppend.get(i));
                }
            });
        } else {
            return null; // Not merged
        }
    }

    private TrackRailsSectionMultipleLinked handleMerge(int capacity, Consumer<ArrayList<TrackRailsSectionSingleNode>> builder) {
        ArrayList<TrackRailsSectionSingleNode> newSections = new ArrayList<>(capacity);
        builder.accept(newSections);
        return new TrackRailsSectionMultipleLinked(this.rails, newSections, this.primary);
    }

    @Override
    public TrackRailsSectionsAtRail merge(TrackRailsSectionsAtRail element) {
        if (element instanceof TrackRailsSectionSingleNode) {
            return appendToChain((TrackRailsSectionSingleNode) element);
        } else if (element instanceof TrackRailsJunction) {
            return ((TrackRailsJunction) element).merge(this.sections);
        } else {
            return null;
        }
    }

    @Override
    public BlockFace getMovementDirection() {
        RailPath.Position p0 = this.path.getStartPosition();
        RailPath.Position p1 = this.path.getEndPosition();
        return Util.vecToFace(p1.posX - p0.posX,
                              p1.posY - p0.posY,
                              p1.posZ - p0.posZ, false);
    }

    @Override
    public Location getSpawnLocation(Block railBlock, Vector orientation) {
        // We want to find the middle between the positions of the nodes of this section
        TrackNode firstNode = this.sections.get(0).node();
        TrackNode lastNode = this.sections.get(this.sections.size()-1).node();
        RailPath.Position startPos = RailPath.Position.fromPosDir(firstNode.getPosition(), firstNode.getDirection());
        RailPath.Position endPos = RailPath.Position.fromPosDir(lastNode.getPosition(), lastNode.getDirection());

        // Snap the start position onto the path
        this.path.move(startPos, railBlock, 0.0);

        // Compute the total distance to reach endPos
        double totalDistance = 0.0;
        {
            RailPath.Position pos = startPos.clone();
            boolean triedInverted = false;
            for (int n = 0; n < 10000; n++) {
                double remaining = pos.distance(endPos);
                if (remaining < 1e-10) {
                    break;
                }
                double moved = this.path.move(pos, railBlock, remaining);
                if (moved >= (remaining - 1e-10)) {
                    totalDistance += moved;
                    continue;
                }

                // Could not move the full distance. Something is wrong.
                if (triedInverted) {
                    // It's just not possible. What.
                    break;
                } else {
                    // Try inverted
                    triedInverted = true;
                    startPos.invertMotion();
                    pos = startPos.clone();
                    totalDistance = 0.0;
                }
            }
        }

        // Now move half this total distance to find the middle
        this.path.move(startPos, railBlock, 0.5 * totalDistance);

        // Convert position on path to a spawn Location
        if (startPos.motDot(orientation) < 0.0) {
            startPos.invertMotion();
        }
        return startPos.toLocation(railBlock).setDirection(startPos.getMotion());
    }

    private static RailPath combineRailPaths(List<? extends TrackRailsSection> sections) {
        RailPath.Builder builder = new RailPath.Builder();
        NonEmptyPathIterator sections_it = new NonEmptyPathIterator(sections);

        // We need at least two non-empty path sections to do any combining
        // It might be some are empty because of straightened nodes
        TrackRailsSection first = sections_it.next();
        TrackRailsSection second = sections_it.next();
        if (first == null) {
            return (second == null) ? RailPath.EMPTY : second.path;
        } else if (second == null) {
            return first.path;
        }

        // For first section, compare with the sections that follow to find curr_point
        RailPath.Point[] first_points = first.path.getPoints();
        RailPath.Point[] second_points = second.path.getPoints();

        boolean first_reversed, second_reversed;

        // Use these to find the rail path point order
        double dist_sq_first_first = getDistSq(first_points[0], second_points[0]);
        double dist_sq_first_last = getDistSq(first_points[0], second_points[second_points.length-1]);
        double dist_sq_last_first = getDistSq(first_points[first_points.length-1], second_points[0]);
        double dist_sq_last_last = getDistSq(first_points[first_points.length-1], second_points[second_points.length-1]);

        // Check in what order the first and second section should be stored in the builder
        first_reversed = Math.min(dist_sq_first_first, dist_sq_first_last) < Math.min(dist_sq_last_first, dist_sq_last_last);
        if (first_reversed) {
            second_reversed = (dist_sq_first_last < dist_sq_first_first);
        } else {
            second_reversed = (dist_sq_last_last < dist_sq_last_first);
        }

        // Add points of first section to the builder
        if (first_reversed) {
            for (int i = first_points.length-1; i >= 0; i--) {
                builder.add(first_points[i]);
            }
        } else {
            for (int i = 0; i < first_points.length; i++) {
                builder.add(first_points[i]);
            }
        }

        // Add points of second section to the builder. Ignore first point.
        RailPath.Point curr_point;
        if (second_reversed) {
            curr_point = second_points[0];
            for (int i = second_points.length-2; i >= 0; i--) {
                builder.add(second_points[i]);
            }
        } else {
            curr_point = second_points[second_points.length-1];
            for (int i = 1; i < second_points.length; i++) {
                builder.add(second_points[i]);
            }
        }

        // Add points of all remaining sections
        TrackRailsSection nextSection;
        while ((nextSection = sections_it.next()) != null) {
            RailPath.Point[] next_points = nextSection.path.getPoints();

            if (getDistSq(curr_point, next_points[0]) < getDistSq(curr_point, next_points[next_points.length-1])) {
                // Natural order, skip first point
                curr_point = next_points[next_points.length-1];
                for (int i = 1; i < next_points.length; i++) {
                    builder.add(next_points[i]);
                }
            } else {
                // Reversed, skip first point
                curr_point = next_points[0];
                for (int i = next_points.length-2; i >= 0; i--) {
                    builder.add(next_points[i]);
                }
            }
        }

        return builder.build();
    }

    private static double getDistSq(RailPath.Point a, RailPath.Point b) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double dz = b.z - a.z;
        return dx*dx + dy*dy + dz*dz;
    }

    private static class NonEmptyPathIterator {
        private final List<? extends TrackRailsSection> sections;
        private int index;

        public NonEmptyPathIterator(List<? extends TrackRailsSection> sections) {
            this.sections = sections;
            this.index = -1;
        }

        public TrackRailsSection next() {
            // Don't care about ++ overflow
            while (++index < sections.size()) {
                TrackRailsSection section = sections.get(index);
                if (!section.path.isEmpty()) {
                    return section;
                }
            }

            return null;
        }
    }

    @Override
    public void writeDebugString(StringBuilder builder, String linePrefix) {
        Vector p0 = path.getStartPosition().toLocation(rails.toBlock(sections.get(0).node().getBukkitWorld())).toVector();
        Vector p1 = path.getEndPosition().toLocation(rails.toBlock(sections.get(0).node().getBukkitWorld())).toVector();

        builder.append(linePrefix);
        if (!primary) {
            builder.append("Secondary");
        }
        builder.append("Linked {\n")
               .append(linePrefix).append("    len: ").append(path.getTotalDistance()).append('\n')
               .append(linePrefix).append("    p0: ").append(MathUtil.round(p0.getX(), 3)).append(' ')
                                                     .append(MathUtil.round(p0.getY(), 3)).append(' ')
                                                     .append(MathUtil.round(p0.getZ(), 3)).append("\n")
               .append(linePrefix).append("    p1: ").append(MathUtil.round(p1.getX(), 3)).append(' ')
                                                     .append(MathUtil.round(p1.getY(), 3)).append(' ')
                                                     .append(MathUtil.round(p1.getZ(), 3)).append("\n")
               .append(linePrefix).append("} SECTIONS [\n");
        for (TrackRailsSectionSingleNode section : sections) {
            section.writeDebugString(builder, linePrefix + "  ");
            builder.append('\n');
        }
        builder.append(linePrefix).append("]");
    }
}
