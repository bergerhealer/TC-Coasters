package com.bergerkiller.bukkit.coasters.objects.lod;

import com.bergerkiller.bukkit.common.utils.LogicUtil;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

/**
 * Immutable ItemStack for displaying the item at a particular distance to the player.
 * The {@link List} stores multiple of these and has methods for editing the LODs.
 */
public interface LODItemStack extends Comparable<LODItemStack> {
    int ADD_LOD_STEP = 32; // Blocks increase every time LODs are expanded

    /**
     * Gets the displayed item. Can be null if none is shown.
     *
     * @return Item
     */
    ItemStack getItem();

    /**
     * Gets the distance threshold of this item at which it is shown. For the default
     * item, this is 0.
     *
     * @return Distance threshold
     */
    int getDistanceThreshold();

    /**
     * Gets whether this itemstack is shown for the view distance specified. Checks the
     * {@link #getDistanceThreshold()} of this item as well as of the next LOD that
     * will replace this one.
     *
     * @param viewDistance View distance to the item
     * @return True if this LOD is shown
     */
    boolean isForDistance(int viewDistance);

    @Override
    default int compareTo(LODItemStack other) {
        return Integer.compare(this.getDistanceThreshold(), other.getDistanceThreshold());
    }

    /**
     * Clones this LODItemStack with a new ItemStack item set
     *
     * @param newItem New ItemStack item to set
     * @return New LODItemStack
     * @see #of(int, ItemStack)
     */
    default LODItemStack withItem(ItemStack newItem) {
        return of(getDistanceThreshold(), newItem);
    }

    /**
     * Clones this LODItemStack with a new distance threshold set
     *
     * @param newDistanceThreshold New distance threshold to set
     * @return New LODItemStack
     * @see #of(int, ItemStack)
     */
    default LODItemStack withDistanceThreshold(int newDistanceThreshold) {
        return of(newDistanceThreshold, getItem());
    }

    /**
     * Creates a new LODItemStack snapshot. Its properties are not altered until it
     * is added to a List structure.
     *
     * @param distanceThreshold Distance threshold
     * @param item ItemStack item to display at this distance
     * @return new LODItemStack
     */
    static LODItemStack of(int distanceThreshold, ItemStack item) {
        return new LODItemStackImpl(distanceThreshold, item);
    }

    /**
     * Creates a new List containing a single item. No distance thresholds are set,
     * as the same item is always shown.
     *
     * @param item Item for the list
     * @return Single-item List
     */
    static List createList(ItemStack item) {
        return new SingleItemList(item);
    }

    /**
     * A list of configured LOD's. Can be a single-LOD list that has no
     * thresholds set, which is handled in an optimized fashion.
     */
    interface List {
        /**
         * Gets the number of LOD ItemStacks represented in this List.
         * If the amount is 1, then no distance thresholds are available, as the same
         * item is always displayed.
         *
         * @return Number of LODs
         * @see #getItem(int) 
         */
        int size();

        /**
         * Gets the 'icon' ItemStack. This is the first LOD (nearest) that has a non-null item
         * set. This icon is used for generating the unique name of track objects, and for
         * the icon displayed for those track objects.
         *
         * @return Icon ItemStack
         */
        ItemStack getIcon();

        /**
         * Gets the LODItemStack used close to the player. Same as {@link #getForDistance(int)}
         * with distance 0, but saves a little processing.
         *
         * @return LODItemStack to display nearest
         */
        LODItemStack getNearest();

        /**
         * Gets a LODItemStack item in this list
         *
         * @param lodIndex Index of an LOD item in this list ({@link #getItems()})
         * @return Item at this index
         * @throws IndexOutOfBoundsException If the lodIndex is out of bounds of the list
         */
        LODItemStack getItem(int lodIndex);

        /**
         * Gets the LODItemStack to display for the view distance specified
         *
         * @param viewDistance View distance to the object
         * @return LODItemStack to display
         */
        LODItemStack getForDistance(int viewDistance);

        /**
         * Unmodifiable list of all LODs represented in this List
         *
         * @return List of LODItemStack
         */
        java.util.List<LODItemStack> getItems();

        /**
         * Swaps out the LODItemStack that is in this list at an index
         *
         * @param lodIndex Index of an LOD item in this list ({@link #getItems()})
         * @param newLODItem New LODItemStack to set
         * @return A new List with the lod item updated, and items sorted from near to far.
         *         This means the lod index specified will not be valid in the new list.
         * @throws IndexOutOfBoundsException If the lodIndex is out of bounds of the list
         */
        List update(int lodIndex, LODItemStack newLODItem);

        /**
         * Duplicates the further entry and adds it to the end of this list. The view distance
         * is set to the furthers plus {@link #ADD_LOD_STEP}, and can then be adjusted.
         *
         * @return A new List with the new LOD added
         */
        default List addNewLOD() {
            java.util.List<LODItemStack> items = getItems();
            LODItemStack last = items.get(items.size() - 1);
            return addNewLOD(last.getDistanceThreshold() + ADD_LOD_STEP, last.getItem());
        }

        /**
         * Adds a new entry to this list with the specified distance threshold and item
         *
         * @param distanceThreshold View distance threshold
         * @param item ItemStack
         * @return A new List with the new LOD item added
         */
        default List addNewLOD(int distanceThreshold, ItemStack item) {
            return addNewLOD(of(distanceThreshold, item));
        }

        /**
         * Adds a new entry to this list with the specified LODItemStack configuration.
         *
         * @param lodItemStack LODItemStack with the distance threshold and item to use
         * @return A new List with the new LOD item added
         */
        List addNewLOD(LODItemStack lodItemStack);

        /**
         * Removes an LOD item from this List. The list can never shrink below one item, at
         * that point no removal is done anymore
         *
         * @param lodIndex Index of an LOD item in this list ({@link #getItems()})
         * @return A new List with the LOD item removed, or same List if list size is 1
         * @throws IndexOutOfBoundsException If the lodIndex is out of bounds of the list
         */
        List removeLOD(int lodIndex);
    }

    /**
     * A single-LOD list. The LOD item and the list itself are all implemented
     * by the same class (the LOD is its own list) to minimize memory footprint.
     */
    final class SingleItemList implements List, LODItemStack {
        private final ItemStack item;

        private SingleItemList(ItemStack item) {
            this.item = item;
        }

        @Override
        public ItemStack getItem() {
            return item;
        }

        @Override
        public int getDistanceThreshold() {
            return 0;
        }

        @Override
        public boolean isForDistance(int viewDistance) {
            return true;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public ItemStack getIcon() {
            return item;
        }

        @Override
        public LODItemStack getNearest() {
            return this;
        }

        @Override
        public LODItemStack getItem(int lodIndex) {
            checkIndex(lodIndex);
            return this;
        }

        @Override
        public LODItemStack getForDistance(int viewDistance) {
            return this;
        }

        @Override
        public java.util.List<LODItemStack> getItems() {
            return Collections.singletonList(this);
        }

        @Override
        public List update(int lodIndex, LODItemStack newLODItem) {
            checkIndex(lodIndex);
            return new SingleItemList(newLODItem.getItem());
        }

        @Override
        public List addNewLOD(LODItemStack lodItem) {
            MultiItemList.ListLODItemStack[] items = new MultiItemList.ListLODItemStack[2];
            items[0] = new MultiItemList.ListLODItemStack(this);
            items[1] = new MultiItemList.ListLODItemStack(lodItem);
            return new MultiItemList(items);
        }

        @Override
        public List removeLOD(int lodIndex) {
            checkIndex(lodIndex);
            return this;
        }

        private void checkIndex(int lodIndex) {
            if (lodIndex != 0) {
                throw new IndexOutOfBoundsException("Index of " + lodIndex + " is out of range (has 1 LOD)");
            }
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hashCode(item);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o instanceof SingleItemList) {
                return Objects.equals(((SingleItemList) o).item, this.item);
            } else if (o instanceof LODItemStack) {
                LODItemStack other = (LODItemStack) o;
                return Objects.equals(this.getItem(), other.getItem()) &&
                        other.getDistanceThreshold() == 0;
            } else {
                return false;
            }
        }

        @Override
        public String toString() {
            return "SingleLODItemStack{item=" + this.item + "}";
        }
    }

    /**
     * Stores 2 or more LODs.
     */
    final class MultiItemList implements List {
        private final ListLODItemStack[] items;

        private MultiItemList(ListLODItemStack[] items) {
            // Ensure sorted near-far and maxViewDistance is correct
            Arrays.sort(items);
            if (items[0].getDistanceThreshold() != 0) {
                items[0] = new ListLODItemStack(items[0].withDistanceThreshold(0));
            }
            for (int i = 0; i < items.length - 1; i++) {
                items[i].maxViewDistance = items[i + 1].getDistanceThreshold();
            }
            items[items.length - 1].maxViewDistance = Integer.MAX_VALUE;

            this.items = items;
        }

        @Override
        public int size() {
            return items.length;
        }

        @Override
        public ItemStack getIcon() {
            for (ListLODItemStack item : items) {
                if (item.getItem() != null) {
                    return item.getItem();
                }
            }
            return null;
        }

        @Override
        public LODItemStack getNearest() {
            return this.items[0];
        }

        @Override
        public LODItemStack getItem(int lodIndex) {
            checkIndex(lodIndex);
            return items[lodIndex];
        }

        @Override
        public LODItemStack getForDistance(int viewDistance) {
            // Items are sorted from near to far
            // Iterate the list from end to start (furthest to nearest)
            // We assume when objects spawn in, players are going to be far away from them
            for (int i = items.length - 1; i >= 1; --i) {
                ListLODItemStack item = items[i];
                if (viewDistance >= item.getDistanceThreshold()) {
                    return item;
                }
            }
            return items[0]; // Default / nearest. Has no threshold.
        }

        @Override
        public java.util.List<LODItemStack> getItems() {
            return Collections.unmodifiableList(Arrays.asList(items));
        }

        @Override
        public List update(int lodIndex, LODItemStack newLODItem) {
            checkIndex(lodIndex);
            ListLODItemStack[] newItems = items.clone();
            newItems[lodIndex] = new ListLODItemStack(newLODItem);
            return new MultiItemList(newItems);
        }

        @Override
        public List addNewLOD(LODItemStack lodItem) {
            ListLODItemStack[] items = Arrays.copyOf(this.items, this.items.length + 1);
            items[items.length - 1] = new ListLODItemStack(lodItem);
            return new MultiItemList(items);
        }

        @Override
        public List removeLOD(int lodIndex) {
            checkIndex(lodIndex);

            // 2 -> 1: Turn into a single-item list
            if (this.items.length == 2) {
                ListLODItemStack other = (lodIndex == 0) ? this.items[1] : this.items[0];
                return new SingleItemList(other.getItem());
            }

            // Remove item
            return new MultiItemList(LogicUtil.removeArrayElement(this.items, lodIndex));
        }

        private void checkIndex(int lodIndex) {
            if (lodIndex < 0 || lodIndex >= items.length) {
                throw new IndexOutOfBoundsException("LOD index of " + lodIndex +
                        " is out of range for list of " + items.length + " LODs");
            }
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(items);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o instanceof MultiItemList) {
                return Arrays.equals(items, ((MultiItemList) o).items);
            } else {
                return false;
            }
        }

        @Override
        public String toString() {
            StringBuilder str = new StringBuilder();
            str.append("MultiLODList [");
            for (ListLODItemStack item : items) {
                str.append("\n  - [")
                        .append(item.getDistanceThreshold()).append(" ... ")
                        .append(item.maxViewDistance).append("]: ")
                        .append(item.getItem());
            }
            str.append("\n]");
            return str.toString();
        }

        private static final class ListLODItemStack extends LODItemStackImpl {
            private int maxViewDistance = Integer.MAX_VALUE;

            public ListLODItemStack(LODItemStack lodItem) {
                super(lodItem);
            }

            @Override
            public boolean isForDistance(int viewDistance) {
                return viewDistance >= getDistanceThreshold() && viewDistance < maxViewDistance;
            }

            @Override
            public String toString() {
                if (maxViewDistance == Integer.MAX_VALUE) {
                    return "List.LODItemStack{threshold=" + getDistanceThreshold() +
                            ", item=" + this.getItem() + "}";
                } else {
                    return "List.LODItemStack{threshold=[" +
                            getDistanceThreshold() + " ... " + maxViewDistance +
                            "], item=" + this.getItem() + "}";
                }
            }
        }
    }
}
