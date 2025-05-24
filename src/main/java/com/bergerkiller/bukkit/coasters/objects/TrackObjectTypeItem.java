package com.bergerkiller.bukkit.coasters.objects;

import com.bergerkiller.bukkit.coasters.objects.lod.LODItemStack;
import com.bergerkiller.bukkit.coasters.particles.TrackParticle;
import org.bukkit.inventory.ItemStack;

/**
 * A type of track object that displays an Item. Exposes methods to get and set the
 * item stack of the item displayed.
 *
 * @param <P> Particle type
 */
public interface TrackObjectTypeItem<P extends TrackParticle> extends TrackObjectType<P> {

    /**
     * Gets a list of level-of-detail controlled ItemStacks to display
     *
     * @return LODItemStack List
     */
    LODItemStack.List getLODItems();

    /**
     * Creates a copy of this type with the LOD item stack list changed
     *
     * @param lodList The new LOD ItemStack list to set
     * @return copy of this type with item stack changed
     */
    TrackObjectTypeItem<P> setLODItems(LODItemStack.List lodList);
}
