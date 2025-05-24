package com.bergerkiller.bukkit.coasters.objects.lod;

import com.bergerkiller.bukkit.common.utils.LogicUtil;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Function;

/**
 * Immutable ItemStack for displaying the item at a particular distance to the player.
 * The {@link List} stores multiple of these and has methods for editing the LODs.
 */
public interface LODItemStack {
    int EXPAND_LOD_STEP = 32; // Blocks increase every time LODs are expanded

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
         * Updates the {@link LODItemStack#getDistanceThreshold()} of a LOD in this list
         *
         * @param lodIndex Index of an LOD item in this list ({@link #getItems()})
         * @param distanceThreshold New distance threshold to set
         * @return A new List with the lod threshold updated, and items sorted from near to far.
         *         This means the lod index specified will not be valid in the new list.
         * @throws IndexOutOfBoundsException If the lodIndex is out of bounds of the list
         */
        List updateDistanceThreshold(int lodIndex, int distanceThreshold);

        /**
         * Updates the {@link LODItemStack#getItem()} of a LOD in this list
         *
         * @param lodIndex Index of an LOD item in this list ({@link #getItems()})
         * @param item New ItemStack item
         * @return A new List with the item updated
         * @throws IndexOutOfBoundsException If the lodIndex is out of bounds of the list
         */
        List updateItem(int lodIndex, ItemStack item);

        /**
         * Duplicates the further entry and adds it to the end of this list. The view distance
         * is set to the furthers plus {@link #EXPAND_LOD_STEP}, and can then be adjusted.
         *
         * @return A new List with the new LOD added
         */
        List expandLOD();

        /**
         * Adds a new entry to this list with the specified distance threshold and item
         *
         * @param distanceThreshold View distance threshold
         * @param item ItemStack
         * @return A new List with the new LOD item added
         */
        List expandLOD(int distanceThreshold, ItemStack item);

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
        public List updateDistanceThreshold(int lodIndex, int distanceThreshold) {
            checkIndex(lodIndex);
            return this; // Has no thresholds so doesn't do anything
        }

        @Override
        public List updateItem(int lodIndex, ItemStack item) {
            checkIndex(lodIndex);
            return new SingleItemList(item);
        }

        @Override
        public List expandLOD() {
            return expandLOD(EXPAND_LOD_STEP, this.item);
        }

        @Override
        public List expandLOD(int distanceThreshold, ItemStack item) {
            MultiItemList.ListLODItemStack[] items = new MultiItemList.ListLODItemStack[2];
            items[0] = new MultiItemList.ListLODItemStack(this.item, 0);
            items[1] = new MultiItemList.ListLODItemStack(item, distanceThreshold);
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
            if (items[0].minViewDistance != 0) {
                items[0] = new ListLODItemStack(items[0].item, 0);
            }
            for (int i = 0; i < items.length - 1; i++) {
                items[i].maxViewDistance = items[i + 1].minViewDistance;
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
                if (item.item != null) {
                    return item.item;
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
                if (viewDistance >= item.minViewDistance) {
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
        public List updateDistanceThreshold(int lodIndex, int distanceThreshold) {
            return updateListItem(lodIndex, prev -> new ListLODItemStack(prev.item, distanceThreshold));
        }

        @Override
        public List updateItem(int lodIndex, ItemStack item) {
            return updateListItem(lodIndex, prev -> new ListLODItemStack(item, prev.minViewDistance));
        }

        @Override
        public List expandLOD() {
            ListLODItemStack last = items[items.length - 2];
            return expandLOD(last.minViewDistance + EXPAND_LOD_STEP, last.item);
        }

        @Override
        public List expandLOD(int distanceThreshold, ItemStack item) {
            ListLODItemStack[] items = Arrays.copyOf(this.items, this.items.length + 1);
            ListLODItemStack last = items[items.length - 2];
            items[items.length - 1] = new ListLODItemStack(item, distanceThreshold);
            return new MultiItemList(items);
        }

        @Override
        public List removeLOD(int lodIndex) {
            checkIndex(lodIndex);

            // 2 -> 1: Turn into a single-item list
            if (this.items.length == 2) {
                ListLODItemStack other = (lodIndex == 0) ? this.items[1] : this.items[0];
                return new SingleItemList(other.item);
            }

            // Remove item
            return new MultiItemList(LogicUtil.removeArrayElement(this.items, lodIndex));
        }

        private List updateListItem(int lodIndex, Function<ListLODItemStack, ListLODItemStack> func) {
            checkIndex(lodIndex);
            ListLODItemStack[] newItems = items.clone();
            newItems[lodIndex] = func.apply(newItems[lodIndex]);
            return new MultiItemList(newItems);
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
                        .append(item.minViewDistance).append(" ... ")
                        .append(item.maxViewDistance).append("]: ")
                        .append(item.item);
            }
            str.append("\n]");
            return str.toString();
        }

        private static class ListLODItemStack implements LODItemStack, Comparable<ListLODItemStack> {
            private final ItemStack item;
            private final int minViewDistance;
            private int maxViewDistance;

            public ListLODItemStack(ItemStack item, int minViewDistance) {
                this.item = item;
                this.minViewDistance = minViewDistance;
                this.maxViewDistance = Integer.MAX_VALUE;
            }

            @Override
            public ItemStack getItem() {
                return item;
            }

            @Override
            public int getDistanceThreshold() {
                return minViewDistance;
            }

            @Override
            public boolean isForDistance(int viewDistance) {
                return viewDistance >= minViewDistance && viewDistance < maxViewDistance;
            }

            @Override
            public int compareTo(LODItemStack.MultiItemList.ListLODItemStack listLODItemStack) {
                return Integer.compare(this.minViewDistance, listLODItemStack.minViewDistance);
            }

            @Override
            public int hashCode() {
                return 31 * Objects.hashCode(item) + minViewDistance;
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) {
                    return true;
                } else if (o instanceof LODItemStack) {
                    LODItemStack other = (LODItemStack) o;
                    return Objects.equals(this.item, other.getItem()) &&
                            this.minViewDistance == other.getDistanceThreshold();
                } else {
                    return false;
                }
            }

            @Override
            public String toString() {
                return "MultiLODItemStack{threshold=[" +
                        minViewDistance + " ... " + maxViewDistance +
                        "], item=" + this.item + "}";
            }
        }
    }
}
