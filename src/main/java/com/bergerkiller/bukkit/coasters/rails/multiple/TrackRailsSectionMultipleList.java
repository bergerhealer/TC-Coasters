package com.bergerkiller.bukkit.coasters.rails.multiple;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.bergerkiller.bukkit.coasters.rails.TrackRailsSection;
import com.bergerkiller.bukkit.coasters.rails.TrackRailsSectionsAtRail;
import com.bergerkiller.bukkit.coasters.rails.single.TrackRailsSingleNodeElement;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.common.bases.IntVector3;

/**
 * Used if two or more islands of rail information exist that aren't connected.
 * Might be resolved and merged back together as one again with future
 * merges providing missing linkages.
 */
public class TrackRailsSectionMultipleList implements TrackRailsSectionsAtRail {
    public final IntVector3 rails;
    public final List<TrackRailsSectionsAtRail> islands;
    private List<TrackRailsSection> optionsCached;

    public TrackRailsSectionMultipleList(IntVector3 rails, TrackRailsSectionsAtRail first, TrackRailsSectionsAtRail second) {
        this.rails = rails;
        this.islands = new ArrayList<>(3);
        this.islands.add(first);
        this.islands.add(second);
        this.optionsCached = null;
    }

    @Override
    public IntVector3 rail() {
        return rails;
    }

    @Override
    public List<TrackRailsSection> options() {
        List<TrackRailsSection> options = optionsCached;
        if (options == null) {
            options = new ArrayList<>(5);
            for (TrackRailsSectionsAtRail island : islands) {
                options.addAll(island.options());
            }
            optionsCached = options;
        }
        return options;
    }

    @Override
    public void forEachNodeElement(Consumer<TrackRailsSingleNodeElement> consumer) {
        for (TrackRailsSectionsAtRail island : islands) {
            island.forEachNodeElement(consumer);
        }
    }

    @Override
    public TrackNode getJunctionNode() {
        for (TrackRailsSectionsAtRail island : islands) {
            TrackNode junctionNode = island.getJunctionNode();
            if (junctionNode != null) {
                return junctionNode;
            }
        }
        return null;
    }

    @Override
    public TrackRailsSectionsAtRail merge(TrackRailsSectionsAtRail element) {
        // Try to merge this section into one of the islands
        int numIslands = islands.size();
        boolean mergeSuccess = false;
        for (int i = 0; i < numIslands; i++) {
            TrackRailsSectionsAtRail merged = islands.get(i).merge(element);
            if (merged == null) {
                continue;
            }

            // If this succeeds, try to merge the other islands into the updated island,
            // if such a thing is possible.
            // We know islands with indices < i didn't merge with the element, so no need
            // to check those.
            for (int j = i + 1; j < numIslands; j++) {
                TrackRailsSectionsAtRail islandMerged = merged.merge(islands.get(j));
                if (islandMerged != null) {
                    merged = islandMerged;
                    islands.remove(j); // Merged into it, so remove it
                    numIslands--;
                    j--;
                }
            }

            islands.set(i, merged);
            mergeSuccess = true;
        }

        if (!mergeSuccess) {
            // Failure. Add as new island.
            islands.add(element);
        } else if (islands.size() == 1) {
            // If list has only one element, we can get rid of the list
            return islands.get(0);
        }

        return this;
    }

    @Override
    public void writeDebugString(StringBuilder builder, String linePrefix) {
        builder.append(linePrefix).append("List [\n");
        for (TrackRailsSectionsAtRail island : islands) {
            island.writeDebugString(builder, linePrefix + "  ");
            builder.append('\n');
        }
        builder.append(linePrefix).append("]");
    }
}
