package com.bergerkiller.bukkit.coasters.rails;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;

/**
 * If more than one connected piece of track section exist for a single rails block,
 * this type is used to link them together. It stores a single RailPath for
 * all linked sections combined.
 */
public class TrackRailsSectionLinked extends TrackRailsSection {
    /**
     * All the sections that are linked together
     */
    public final List<TrackRailsSection> sections;

    public TrackRailsSectionLinked(List<TrackRailsSection> sections) {
        super(findBestSection(sections), combineRailPaths(sections));
        this.sections = sections;
    }

    @Override
    public Stream<TrackNode> getNodes() {
        return sections.stream().flatMap(section -> section.getNodes());
    }

    @Override
    public boolean containsNode(Collection<TrackNode> nodes) {
        for (TrackRailsSection section : sections) {
            if (nodes.contains(section.node)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<TrackRailsSection> getAllSections() {
        return this.sections;
    }

    @Override
    public boolean isConnectedWith(TrackRailsSection section) {
        return this.sections.get(0).isConnectedWith(section) ||
               this.sections.get(this.sections.size()-1).isConnectedWith(section);
    }

    @Override
    public Location getSpawnLocation(Block railBlock, Vector orientation) {
        // We want to find the middle between the positions of the nodes of this section
        TrackNode firstNode = this.sections.get(0).node;
        TrackNode lastNode = this.sections.get(this.sections.size()-1).node;
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

    private static TrackRailsSection findBestSection(List<TrackRailsSection> sections) {
        // Sections that are part of nodes that are junctions are most important
        for (TrackRailsSection section : sections) {
            if (section.node.getConnections().size() > 2) {
                return section;
            }
        }

        // Middle as fallback. TODO: other rules?
        return sections.get(sections.size()/2);
    }

    private static RailPath combineRailPaths(List<TrackRailsSection> sections) {
        RailPath.Builder builder = new RailPath.Builder();

        Iterator<TrackRailsSection> sections_it = sections.iterator();

        // For first section, compare with the sections that follow to find curr_point
        TrackRailsSection first = sections_it.next();
        TrackRailsSection second = sections_it.next();
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
        while (sections_it.hasNext()) {
            RailPath.Point[] next_points = sections_it.next().path.getPoints();

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
}
