package com.bergerkiller.bukkit.coasters.objects;

import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.coasters.particles.TrackParticleObject;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.common.utils.ItemUtil;

/**
 * Item stack displayed on an armorstand
 */
public class TrackObjectTypeItemStack implements TrackObjectType<TrackParticleObject> {
    private final ItemStack item;

    private TrackObjectTypeItemStack(ItemStack item) {
        this.item = item;
    }

    public static TrackObjectTypeItemStack create(ItemStack item) {
        return new TrackObjectTypeItemStack(item);
    }

    public static TrackObjectTypeItemStack changeItem(TrackObjectTypeItemStack source, ItemStack item) {
        return new TrackObjectTypeItemStack(item);
    }

    public ItemStack getItem() {
        return this.item;
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
        return point.getWorld().getParticles().addParticleObject(point.position, point.orientation, this.item);
    }

    @Override
    public void updateParticle(TrackParticleObject particle, TrackConnection.PointOnPath point) {
        particle.setPositionOrientation(point.position, point.orientation);
        particle.setItem(this.item);
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
