package com.bergerkiller.bukkit.coasters.objects.lod;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * A snapshot of a LOD ItemStack. Stores all of the state information of
 * a LODItemStack but is not part of a List collection of any sort.
 */
class LODItemStackImpl implements LODItemStack, Comparable<LODItemStack> {
    private final int distanceThreshold;
    private final ItemStack item;

    protected LODItemStackImpl(LODItemStack lodItem) {
        this(lodItem.getDistanceThreshold(), lodItem.getItem());
    }

    public LODItemStackImpl(int distanceThreshold, ItemStack item) {
        this.distanceThreshold = distanceThreshold;
        this.item = item;
    }

    @Override
    public ItemStack getItem() {
        return item;
    }

    @Override
    public int getDistanceThreshold() {
        return distanceThreshold;
    }

    @Override
    public boolean isForDistance(int viewDistance) {
        return viewDistance >= distanceThreshold;
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hashCode(item) + distanceThreshold;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof LODItemStack) {
            LODItemStack other = (LODItemStack) o;
            return Objects.equals(this.item, other.getItem()) &&
                    this.distanceThreshold == other.getDistanceThreshold();
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "LODItemStack{threshold=" + distanceThreshold +
                ", item=" + this.item + "}";
    }
}
