package com.bergerkiller.bukkit.coasters.rails;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.block.Block;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.TCCoastersUtil;
import com.bergerkiller.bukkit.coasters.tracks.TrackCoaster;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.util.RailSectionBlockIterator;
import com.bergerkiller.bukkit.coasters.world.CoasterWorld;
import com.bergerkiller.bukkit.coasters.world.CoasterWorldComponent;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.collections.ObjectCache;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.SetMultimap;

/**
 * Tracks the lookup of rails information from block positions on a single world
 */
public class TrackRailsWorld implements CoasterWorldComponent {
    private final CoasterWorld _world;
    private final ListMultimap<IntVector3, TrackRailsSection> sectionsByRails = LinkedListMultimap.create(10000);
    private final SetMultimap<IntVector3, TrackRailsSection> sectionsByBlock = HashMultimap.create(10000, 2);
    private final Map<TrackNode, TrackNodeMeta> trackNodeMeta = new IdentityHashMap<>();
    private final Map<TrackNode, ObjectCache.Entry<Set<IntVector3>>> tmpNodeBlocks = new IdentityHashMap<>();

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

    public Collection<TrackRailsSection> findAtBlock(Block block) {
        return sectionsByBlock.get(new IntVector3(block));
    }

    public List<TrackRailsSection> findAtRails(Block railsBlock) {
        return sectionsByRails.get(new IntVector3(railsBlock));
    }

    public List<TrackRailsSection> findAtRails(int x, int y, int z) {
        return sectionsByRails.get(new IntVector3(x, y, z));
    }

    /**
     * Rebuilds all the rail information
     */
    public void rebuild() {
        clear();
        for (TrackCoaster coaster : getWorld().getTracks().getCoasters()) {
            for (TrackNode node : coaster.getNodes()) {
                store(node);
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
        try (ObjectCache.Entry<Set<TrackRailsSection>> sectionsToReAdd = ObjectCache.newHashSet();
             ObjectCache.Entry<Set<IntVector3>> addedTrackRails = ObjectCache.newHashSet();
             ObjectCache.Entry<Set<IntVector3>> addedTrackBlocks = ObjectCache.newHashSet())
        {
            // Collect all block and rail coordinates affected
            for (TrackNode node : nodes) {
                TrackNodeMeta meta = trackNodeMeta.remove(node);
                if (meta != null) {
                    addedTrackRails.get().add(meta.rails);
                    addedTrackBlocks.get().addAll(Arrays.asList(meta.blocks));
                }
            }

            // Remove from the found coordinates
            for (IntVector3 railBlock : addedTrackRails.get()) {
                removeFromMap(sectionsByRails.get(railBlock), nodes, sectionsToReAdd.get());
            }
            for (IntVector3 posBlock : addedTrackBlocks.get()) {
                removeFromMap(sectionsByBlock.get(posBlock), nodes, sectionsToReAdd.get());
            }

            // Re-add sections that were merged
            for (TrackRailsSection reAdd : sectionsToReAdd.get()) {
                addSectionToMap(reAdd);
            }
        } finally {
            finishAddingSectionsToMap();
        }
    }

    public void store(TrackNode node) {
        try {
            // If no connections, don't map it in the world at all - it does nothing
            List<TrackConnection> connections = node.getConnections();
            if (connections.isEmpty()) {
                return;
            }

            // Rails
            IntVector3 rails = node.getRailBlock(true);

            // First 1 or 2 connections, which connect to each other and are selected
            addSectionToMapIfPathNotEmpty(node, rails, node.buildPath(), true);

            // All other kinds of connections lead to their best fit
            if (connections.size() > 2) {
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
                    addSectionToMapIfPathNotEmpty(node, rails, node.buildPath(conn, other), false);
                }
            }
        } finally {
            finishAddingSectionsToMap();
        }
    }

    private final void addSectionToMapIfPathNotEmpty(TrackNode node, IntVector3 rails, RailPath path, boolean primary) {
        if (!path.isEmpty()) {
            addSectionToMap(new TrackRailsSection(node, rails, path, primary));
        }
    }

    private final void addSectionToMap(TrackRailsSection section) {
        // Add to sections by rails mapping. If not empty, try to merge the sections.
        // We then proceed to store the merged-together sections in the map, instead.
        // Only do this for primary sections, never for non-primary (junctions) to prevent issues.
        {
            List<TrackRailsSection> sectionsAtRails = sectionsByRails.get(section.rails);
            if (sectionsAtRails.isEmpty()) {
                // No sections here, store it and nothing special
                sectionsAtRails.add(section);
            } else {
                //TODO: Debug validation, can be removed when no longer an issue
                if (sectionsAtRails.contains(section)) {
                    throw new IllegalArgumentException("Rails section is already contained");
                }
                if (section instanceof TrackRailsSectionLinked) {
                    throw new IllegalArgumentException("Linked rails sections cannot be added this way");
                }

                // Try to merge it with existing track sections at these rails
                if (section.primary) {
                    List<TrackRailsSection> sectionsToMerge = new ArrayList<TrackRailsSection>(2);
                    for (TrackRailsSection other : sectionsAtRails) {
                        if (other.isConnectedWith(section)) {
                            sectionsToMerge.add(other);
                        }
                    }

                    int numSectionsToMerge = sectionsToMerge.size();
                    if (numSectionsToMerge == 1 || numSectionsToMerge == 2) {
                        List<TrackRailsSection> allSections = new ArrayList<TrackRailsSection>();
                        allSections.addAll(sectionsToMerge.get(0).getAllSections());
                        if (allSections.get(0).isConnectedWith(section)) {
                            allSections.add(0, section);
                            if (numSectionsToMerge == 2) {
                                List<TrackRailsSection> secondSections = sectionsToMerge.get(1).getAllSections();
                                if (secondSections.get(0).isConnectedWith(section)) {
                                    // First section is connected, which means the list is the wrong way around
                                    // Reverse it
                                    secondSections = new ArrayList<TrackRailsSection>(secondSections);
                                    Collections.reverse(secondSections);
                                }
                                allSections.addAll(0, secondSections);
                            }
                        } else {
                            allSections.add(section);
                            if (numSectionsToMerge == 2) {
                                List<TrackRailsSection> secondSections = sectionsToMerge.get(1).getAllSections();
                                if (secondSections.get(secondSections.size()-1).isConnectedWith(section)) {
                                    // Last section is connected, which means the list is the wrong way around
                                    // Reverse it
                                    secondSections = new ArrayList<TrackRailsSection>(secondSections);
                                    Collections.reverse(secondSections);
                                }
                                allSections.addAll(secondSections);
                            }
                        }

                        // Unregister sections we are replacing from the by-rails list
                        sectionsAtRails.removeAll(sectionsToMerge);

                        // Remove sections we are replacing from the by-block-position mapping
                        for (TrackRailsSection mergedSection : sectionsToMerge) {
                            TrackNodeMeta meta = trackNodeMeta.remove(mergedSection.node);
                            if (meta != null) {
                                for (IntVector3 posBlock : meta.blocks) {
                                    sectionsByBlock.get(posBlock).remove(mergedSection);
                                }
                            }
                        }

                        // Now create a single linked section from all the sections we've gathered
                        section = new TrackRailsSectionLinked(allSections);
                    }
                }

                // Add section to list
                sectionsAtRails.add(section);
            }
        }

        // For dead-end nodes we must ignore the blocks beyond the node
        // Otherwise the node cannot be 'exited' to other rail types
        Collection<IntVector3> ignoredBlocks = Collections.emptyList();
        if (section.node.getConnections().size() == 1) {
            // Figure out the block occupied by the node itself
            IntVector3 posBlock = section.node.getPositionBlock();
            Vector dir = section.node.getDirection();
            ignoredBlocks = new HashSet<IntVector3>();

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
        }

        // For all segments of the path, store the block positions being covered in the lookup table
        for (RailPath.Segment segment : section.path.getSegments()) {
            RailSectionBlockIterator iter = new RailSectionBlockIterator(segment, section.rails);
            do {
                mapSectionToBlock(iter.block(), ignoredBlocks, section);
                for (IntVector3 around : iter.around(0.4)) {
                    mapSectionToBlock(around, ignoredBlocks, section);
                }
            } while (iter.next());
            for (IntVector3 around : iter.aroundEnd(0.4)) {
                mapSectionToBlock(around, ignoredBlocks, section);
            }
        }
    }

    private void finishAddingSectionsToMap() {
        // Map all added nodes to the track node
        // We may be adding more than one node, because of merging tracks at the same rails
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
        tmpNodeBlocks.clear();
    }

    private void mapSectionToBlock(IntVector3 key, Collection<IntVector3> suppressed, TrackRailsSection section) {
        if (!suppressed.contains(key) && sectionsByBlock.get(key).add(section)) {
            section.getNodes().forEach(node -> {
                tmpNodeBlocks.computeIfAbsent(node, unused -> {
                    ObjectCache.Entry<Set<IntVector3>> e = ObjectCache.newHashSet();
                    e.get().add(key);
                    return e;
                });
            });
        }
    }

    private static void removeFromMap(Collection<TrackRailsSection> sections, Collection<TrackNode> nodes, Set<TrackRailsSection> sectionsToReAdd) {
        Iterator<TrackRailsSection> sections_iter = sections.iterator();
        if (sections.size() == 1) {
            // Single section (or linked section) is stored
            TrackRailsSection section = sections_iter.next();
            if (!section.containsNode(nodes)) {
                return;
            }

            // Sub-sections that should not be removed, should be re-added later
            if (section instanceof TrackRailsSectionLinked) { // optimization. Can remove.
                for (TrackRailsSection part : section.getAllSections()) {
                    if (!part.containsNode(nodes)) {
                        sectionsToReAdd.add(part);
                    }
                }
            }

            // Remove entry entirely
            sections_iter.remove();
        } else {
            // Multiple sections are stored in an ArrayList
            while (sections_iter.hasNext()) {
                TrackRailsSection section = sections_iter.next();
                if (section.containsNode(nodes)) {
                    // Sub-sections that should not be removed, should be re-added later
                    if (section instanceof TrackRailsSectionLinked) { // optimization. Can remove.
                        for (TrackRailsSection part : section.getAllSections()) {
                            if (!part.containsNode(nodes)) {
                                sectionsToReAdd.add(part);
                            }
                        }
                    }

                    sections_iter.remove();
                }
            }
        }
    }

    /*
    private void addToSectionsByBlock(BlockRelativePosition position, TrackRailsSection section) {
        int min_x = position.block_x;
        int max_x = position.block_x;
        int min_y = position.block_y;
        int max_y = position.block_y;
        int min_z = position.block_z;
        int max_z = position.block_z;
        final double c = 1e-8;
        if (position.x < c) {
            min_x--;
        } else if (position.x > (1.0 - c)) {
            max_x++;
        }
        if (position.y < c) {
            min_y--;
        } else if (position.y > (1.0 - c)) {
            max_y++;
        }
        if (position.z < c) {
            min_z--;
        } else if (position.z > (1.0 - c)) {
            max_z++;
        }
        for (int bx = min_x; bx <= max_x; bx++) {
            for (int by = min_y; by <= max_y; by++) {
                for (int bz = min_z; bz <= max_z; bz++) {
                    addToMap(sectionsByBlock, new IntVector3(bx, by, bz), section);
                }
            }
        }
    }
    */

    /*
    public void store(TrackRailsSection section) {
        this.sectionsByRails.put(section.rails, section);

        // For all segments of the path, store the block positions being covered in the lookup table
        for (RailPath.Segment segment : section.path.getSegments()) {
            double x = section.rails.x + segment.p0.x;
            double y = section.rails.y + segment.p0.y;
            double z = section.rails.z + segment.p0.z;
            int numSteps = MathUtil.ceil(segment.l / 0.1);
            if (numSteps <= 0) {
                continue;
            }

            if (numSteps == 1) {
                sectionsByBlock.put(new IntVector3(x, y, z), section);
            } else {
                for (int i = 0; i < numSteps; i++) {
                    double m = (double) i / (double) (numSteps - 1);
                    IntVector3 pos = new IntVector3(x + m * segment.dt.x,
                                                    y + m * segment.dt.y,
                                                    z + m * segment.dt.z);
                    sectionsByBlock.put(pos, section);
                }
            }
        }
    }
    */

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
