package com.bergerkiller.bukkit.coasters.particles;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

/**
 * Stores a type of item displayed in a particle.
 * Different state versions of the item are kept
 */
public class TrackParticleItemType {
    private final ItemStack item_default;
    private final ItemStack item_selected;
    public static TrackParticleItemType DEFAULT = new TrackParticleItemType(Material.GOLD_BLOCK);
    public static TrackParticleItemType LEVER = new TrackParticleItemType(Material.LEVER);

    public TrackParticleItemType(Material itemType) {
        this.item_default = new ItemStack(itemType, 1);
        this.item_selected = this.item_default.clone();
        this.item_selected.addUnsafeEnchantment(Enchantment.DURABILITY, 1);
    }

    public ItemStack getItem(TrackParticleState state) {
        if (state == TrackParticleState.SELECTED) {
            return item_selected;
        }
        return item_default;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof TrackParticleItemType) {
            TrackParticleItemType ot = (TrackParticleItemType) o;
            return ot.item_default.equals(this.item_default);
        } else {
            return false;
        }
    }
}
