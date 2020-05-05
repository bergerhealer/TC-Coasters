package com.bergerkiller.bukkit.coasters.objects;

import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.coasters.particles.TrackParticleObject;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;

/**
 * Item stack displayed on an armorstand
 */
public class TrackObjectTypeItemStack implements TrackObjectType<TrackParticleObject> {
    private final double width;
    private final ItemStack item;

    private TrackObjectTypeItemStack(double width, ItemStack item) {
        this.width = width;
        this.item = item;
    }

    public static TrackObjectTypeItemStack create(double width, ItemStack item) {
        return new TrackObjectTypeItemStack(width, item);
    }

    public static TrackObjectTypeItemStack createDefault() {
        return create(0.0, new ItemStack(MaterialUtil.getFirst("RAIL", "LEGACY_RAILS")));
    }

    @Override
    public double getWidth() {
        return this.width;
    }

    @Override
    public TrackObjectTypeItemStack setWidth(double width) {
        return new TrackObjectTypeItemStack(width, this.item);
    }

    /**
     * Gets the item stack displayed
     * 
     * @return item stack
     */
    public ItemStack getItem() {
        return this.item;
    }

    /**
     * Creates a copy of this type with the item stack changed
     * 
     * @param item The new item stack to set
     * @return copy of this type with item stack changed
     */
    public TrackObjectTypeItemStack setItem(ItemStack item) {
        return new TrackObjectTypeItemStack(this.width, item);
    }

    @Override
    @SuppressWarnings("deprecation")
    public String generateName() {
        if (ItemUtil.hasDurability(item)) {
            return "I_" + item.getType() + "_" + item.getDurability();
        } else {
            return "I_" + item.getType();
        }
    }

    @Override
    public TrackParticleObject createParticle(TrackConnection.PointOnPath point) {
        return point.getWorld().getParticles().addParticleObject(point.position, point.orientation, this.item, this.width);
    }

    @Override
    public void updateParticle(TrackParticleObject particle, TrackConnection.PointOnPath point) {
        particle.setPositionOrientation(point.position, point.orientation);
        particle.setItem(this.item);
        particle.setWidth(this.width);
    }

    @Override
    public int hashCode() {
        return this.item.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof TrackObjectTypeItemStack) {
            TrackObjectTypeItemStack other = (TrackObjectTypeItemStack) o;
            return this.item.equals(other.item);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "{TrackObjectType[ItemStack] item=" + this.item + "}";
    }
}
