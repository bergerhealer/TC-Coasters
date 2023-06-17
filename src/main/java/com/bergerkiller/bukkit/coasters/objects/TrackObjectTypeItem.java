package com.bergerkiller.bukkit.coasters.objects;

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
     * Gets the ItemStack of the item displayed
     *
     * @return item stack
     */
    ItemStack getItem();

    /**
     * Creates a copy of this type with the item stack changed
     *
     * @param item The new items tack to set
     * @return copy of this type with item stack changed
     */
    TrackObjectTypeItem<P> setItem(ItemStack item);
}
