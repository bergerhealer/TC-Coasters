package com.bergerkiller.bukkit.coasters.rails;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.bergerkiller.bukkit.coasters.tracks.TrackWorld;
import org.bukkit.block.Block;

import com.bergerkiller.bukkit.coasters.rails.multiple.TrackRailsSectionMultipleList;
import com.bergerkiller.bukkit.coasters.rails.single.TrackRailsSingleNodeElement;
import com.bergerkiller.bukkit.coasters.tracks.TrackCoaster;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.world.CoasterWorld;
import com.bergerkiller.bukkit.coasters.world.CoasterWorldComponent;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.collections.ObjectCache;
import com.bergerkiller.bukkit.common.utils.MathUtil;

/**
 * Tracks the lookup of rails information from block positions on a single world
 */
public class TrackRailsWorld implements CoasterWorldComponent {
    private final CoasterWorld _world;
    private final Map<IntVector3, TrackRailsSectionsAtRail> sectionsByRails = new HashMap<>();
    private final TrackRailsSectionsAtPosition.Map sectionsByBlock = new TrackRailsSectionsAtPosition.Map();
    private final Map<TrackNode, TrackNodeMeta> trackNodeMeta = new IdentityHashMap<>();
    private final Map<TrackNode, ObjectCache.Entry<Set<IntVector3>>> tmpNodeBlocks = new IdentityHashMap<>();
    final Set<TrackRailsSection> lastPickedSections = new HashSet<>(); // For background cleanup

    public TrackRailsWorld(CoasterWorld world) {
        this._world = world;
    }

    @Override
    public final CoasterWorld getWorld() {
        return this._world;
    }

    public void clear() {
        this.sectionsByBlock.clear();
        this.sectionsByRails.clear();
        this.trackNodeMeta.clear();
    }

    public TrackRailsSectionsAtPosition findAtBlock(Block block) {
        return sectionsByBlock.getOrDefault(new IntVector3(block), TrackRailsSectionsAtPosition.NONE);
    }

    public TrackNode findJunctionNode(Block railsBlock) {
        TrackRailsSectionsAtRail atRail = sectionsByRails.get(new IntVector3(railsBlock));
        return (atRail == null) ? null : atRail.getJunctionNode();
    }

    public TrackRailsSectionsAtRail findAtRailsInformation(int x, int y, int z) {
        return sectionsByRails.get(new IntVector3(x, y, z));
    }

    public List<? extends TrackRailsSection> findAtRails(Block railsBlock) {
        TrackRailsSectionsAtRail atRail = sectionsByRails.get(new IntVector3(railsBlock));
        return (atRail == null) ? Collections.emptyList() : atRail.options();
    }

    public List<? extends TrackRailsSection> findAtRails(int x, int y, int z) {
        TrackRailsSectionsAtRail atRail = sectionsByRails.get(new IntVector3(x, y, z));
        return (atRail == null) ? Collections.emptyList() : atRail.options();
    }

    /**
     * Cleans up picked-before information tracks for rail sections in the world
     */
    @Override
    public void updateAll() {
        if (!lastPickedSections.isEmpty()) {
            int serverTickThreshold = TrackRailsSection.getPickServerTickThreshold();
            for (Iterator<TrackRailsSection> iter = lastPickedSections.iterator(); iter.hasNext();) {
                if (iter.next().cleanupPickedBefore(serverTickThreshold)) {
                    iter.remove();
                }
            }
        }
    }

    /**
     * Removes all track information for a particular node
     * 
     * @param node
     */
    public void purge(TrackNode node) {
        purge(Collections.singletonList(node));
    }

    /**
     * Removes all track information for all nodes specified
     * 
     * @param nodes
     */
    public void purge(Collection<TrackNode> nodes) {
        try (ObjectCache.Entry<Set<TrackRailsSingleNodeElement>> nodeElementsToReAdd = ObjectCache.newHashSet();
             ObjectCache.Entry<Set<IntVector3>> addedTrackBlocks = ObjectCache.newHashSet();
             ObjectCache.Entry<Set<IntVector3>> addedTrackRails = ObjectCache.newHashSet())
        {
            // Collect all block and rail coordinates affected
            for (TrackNode node : nodes) {
                TrackNodeMeta meta = trackNodeMeta.remove(node);
                if (meta != null) {
                    addedTrackBlocks.get().addAll(Arrays.asList(meta.blocks));
                    addedTrackRails.get().add(meta.rails);
                }
            }

            // Remove all sections from the by-block-position mapping
            // These sections are the un-merged single-node originals
            for (IntVector3 posBlock : addedTrackBlocks.get()) {
                Iterator<TrackRailsSingleNodeElement> sections_iter = sectionsByBlock.getSections(posBlock).iterator();
                while (sections_iter.hasNext()) {
                    TrackRailsSingleNodeElement section = sections_iter.next();
                    if (nodes.contains(section.node())) {
                        sections_iter.remove();
                    }
                }
            }

            final Set<TrackRailsSingleNodeElement> nodeElementsToReAddSet = nodeElementsToReAdd.get();

            // Look up all rail blocks affected and remove the single-node sections affected
            // by this removal. Sections that match nodes that weren't removed are re-added
            // later.
            for (IntVector3 railBlock : addedTrackRails.get()) {
                TrackRailsSectionsAtRail atRail = sectionsByRails.remove(railBlock);
                if (atRail != null) {
                    atRail.forEachNodeElement(element -> {
                        if (!nodes.contains(element.node())) {
                            nodeElementsToReAddSet.add(element);
                        }
                    });
                }
            }

            // Re-add sections that were merged and are now detached. This might result in
            // a new way of merging these together.
            nodeElementsToReAddSet.forEach(this::addSectionToByRailMap);
        } finally {
            finishAddingSectionsToMap();
        }
    }

    public void store(TrackNode node) {
        try {
            TrackRailsSingleNodeElement nodeSection = TrackRailsSingleNodeElement.create(node);
            if (nodeSection == null) {
                return;
            }

            // Map this section to all block position blocks where it is active
            nodeSection.forEachBlockPosition(block -> {
                if (sectionsByBlock.addSection(block, nodeSection)) {
                    tmpNodeBlocks.computeIfAbsent(node, u -> ObjectCache.newHashSet()).get().add(block);
                }
            });

            // Map this section to the rail block. This might result in it being merged
            // with other sections.
            addSectionToByRailMap(nodeSection);
        } finally {
            finishAddingSectionsToMap();
        }
    }

    /**
     * Adds the section of rails for a single node to the by-rails mapping. This might
     * merge the section with pre-existing sections of other nodes.
     *
     * @param section
     */
    private final void addSectionToByRailMap(final TrackRailsSingleNodeElement section) {
        sectionsByRails.compute(section.rail(), (rail, atRail) -> {
            if (atRail == null) {
                return section;
            } else {
                TrackRailsSectionsAtRail merged = atRail.merge(section);
                if (merged != null) {
                    return merged;
                } else {
                    // Can't merge, make it into a List
                    return new TrackRailsSectionMultipleList(rail, atRail, section);
                }
            }
        });
    }

    private void finishAddingSectionsToMap() {
        // Map all added nodes to the track node
        // We may be adding more than one node, because of merging tracks at the same rails
        try {
            for (Map.Entry<TrackNode, ObjectCache.Entry<Set<IntVector3>>> entry : tmpNodeBlocks.entrySet()) {
                final Set<IntVector3> blocks = entry.getValue().get();
                trackNodeMeta.compute(entry.getKey(), (node, prevValue) -> {
                    if (prevValue == null) {
                        return new TrackNodeMeta(node.getRailBlock(true), blocks);
                    } else {
                        // Merge old and new sets
                        // The set is writable because it is not used elsewhere, and will be closed
                        blocks.addAll(Arrays.asList(prevValue.blocks));
                        // Preserve original rail coordinates
                        return new TrackNodeMeta(prevValue.rails, blocks);
                    }
                });

                // Done using this set, close it so its put back in cache
                entry.getValue().close();
            }
        } finally {
            tmpNodeBlocks.clear();
        }
    }

    /**
     * Stores metadata for a track node, used when purging data for nodes
     */
    private static final class TrackNodeMeta {
        public final IntVector3[] blocks;
        public final IntVector3 rails;

        public TrackNodeMeta(IntVector3 rails, Set<IntVector3> blocks) {
            this.rails = rails;
            this.blocks = blocks.toArray(new IntVector3[blocks.size()]);
        }
    }

    /**
     * Stores an x/y/z vector by splitting it into a block part (int) and
     * the small position relative to the block (double). The x/y/z will
     * always fall between 0.0 and 1.0 after each update().
     */
    @SuppressWarnings("unused")
    private static class BlockRelativePosition {
        public double x, y, z;
        public int block_x = 0;
        public int block_y = 0;
        public int block_z = 0;

        public boolean update() {
            int block_dx = MathUtil.floor(x);
            int block_dy = MathUtil.floor(y);
            int block_dz = MathUtil.floor(z);
            if ((block_dx | block_dy | block_dz) == 0) {
                return false;
            }

            block_x += block_dx;
            block_y += block_dy;
            block_z += block_dz;
            x -= block_dx;
            y -= block_dy;
            z -= block_dz;
            return true;
        }
    }
}
