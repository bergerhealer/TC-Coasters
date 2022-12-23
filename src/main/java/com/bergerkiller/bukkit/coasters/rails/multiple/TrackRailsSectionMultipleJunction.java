package com.bergerkiller.bukkit.coasters.rails.multiple;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import com.bergerkiller.bukkit.coasters.rails.TrackRailsJunction;
import com.bergerkiller.bukkit.coasters.rails.TrackRailsSection;
import com.bergerkiller.bukkit.coasters.rails.single.TrackRailsSingleNodeElement;
import com.bergerkiller.bukkit.coasters.rails.single.TrackRailsSectionSingleNode;
import com.bergerkiller.bukkit.coasters.rails.single.TrackRailsSingleJunctionNodeElement;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.common.bases.IntVector3;

/**
 * Modifiable junction node that stores all options through the junction as
 * modifiable chains of single-node sections. A single-node junction is upgraded
 * to this one if one or more other sections are merged into it.
 */
public final class TrackRailsSectionMultipleJunction implements TrackRailsJunction {
    /** Junction track node and related metadata that sits at the center */
    public final TrackRailsSingleJunctionNodeElement junction;
    /** List of paths of this junction. First element is the primary (selected) one */
    public final TrackRailsSection[] options;

    public TrackRailsSectionMultipleJunction(TrackRailsSingleJunctionNodeElement junction) {
        this.junction = junction;

        List<? extends TrackRailsSection> junctionOptions = junction.options();
        this.options = junctionOptions.toArray(new TrackRailsSection[junctionOptions.size()]);
    }

    @Override
    public IntVector3 rail() {
        return junction.rail();
    }

    @Override
    public TrackNode getJunctionNode() {
        return junction.node();
    }

    @Override
    public List<TrackRailsSection> options() {
        return Arrays.asList(options);
    }

    @Override
    public void forEachNodeElement(Consumer<TrackRailsSingleNodeElement> consumer) {
        consumer.accept(junction);
        for (TrackRailsSection section : options) {
            section.forEachNodeElement(consumer);
        }
    }

    @Override
    public TrackRailsSectionMultipleJunction merge(List<TrackRailsSectionSingleNode> sectionChain) {
        int sectionChainSize = sectionChain.size();
        if (sectionChainSize == 0) {
            return null; // Can't merge nothing
        }

        int numOptions = options.length;
        boolean success = false;
        if (sectionChainSize == 1) {
            // Optimized method for a single element
            TrackRailsSectionSingleNode section = sectionChain.get(0);
            for (int i = 0; i < numOptions; i++) {
                TrackRailsSection merged = options[i].appendToChain(section);
                if (merged != null) {
                    options[i] = merged;
                    success = true;
                }
            }
        } else {
            // Try to add this chain of sections to the sections of this junction
            // If it succeeds for even just one, return this to indicate it is updated
            for (int i = 0; i < numOptions; i++) {
                TrackRailsSection merged = options[i].appendToChain(sectionChain);
                if (merged != null) {
                    options[i] = merged;
                    success = true;
                }
            }
        }

        return success ? this : null;
    }

    @Override
    public void writeDebugString(StringBuilder builder, String linePrefix) {
        builder.append(linePrefix).append("MultipleJunction [\n");
        for (TrackRailsSection option : options) {
            option.writeDebugString(builder, linePrefix + "  ");
            builder.append('\n');
        }
        builder.append(linePrefix).append("]");
    }
}
