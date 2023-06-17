package com.bergerkiller.bukkit.coasters.objects;

import com.bergerkiller.bukkit.coasters.particles.TrackParticle;
import com.bergerkiller.bukkit.common.wrappers.BlockData;

/**
 * A type of track object that displays a Block. Exposes methods to get and set the
 * block data of the block displayed.
 *
 * @param <P> Particle type
 */
public interface TrackObjectTypeBlock<P extends TrackParticle> extends TrackObjectType<P> {

    /**
     * Gets the BlockData of the block displayed
     *
     * @return block data
     */
    BlockData getBlockData();

    /**
     * Creates a copy of this type with the block data changed
     *
     * @param blockData The new block data to set
     * @return copy of this type with block data changed
     */
    TrackObjectTypeBlock<P> setBlockData(BlockData blockData);
}
