package com.bergerkiller.bukkit.coasters.rails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;

import com.bergerkiller.bukkit.coasters.rails.single.TrackRailsSingleNodeElement;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.collections.CollectionBasics;

/**
 * Stores the rail sections and unique list of rail block coordinates
 * at a particular block position.
 */
public interface TrackRailsSectionsAtPosition {
    /**
     * Represents no rail sections being stored at a position
     */
    public static final TrackRailsSectionsAtPosition NONE = new TrackRailsSectionsAtPosition() {
        @Override
        public List<IntVector3> rails() {
            return Collections.emptyList();
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public List<TrackRailsSingleNodeElement> values() {
            return Collections.emptyList();
        }

        @Override
        public TrackRailsSectionsAtPosition tryAdd(TrackRailsSingleNodeElement sectionToAdd) {
            return single(sectionToAdd);
        }

        @Override
        public TrackRailsSectionsAtPosition tryRemove(TrackRailsSingleNodeElement sectionToRemove) {
            return null;
        }
    };

    /**
     * Gets all the rail block coordinates
     *
     * @return rails
     */
    List<IntVector3> rails();

    /**
     * Gets an unmodifiable list of sections stored at this position
     *
     * @return values
     */
    List<TrackRailsSingleNodeElement> values();

    /**
     * Gets the number of track rail sections stored here
     *
     * @return size
     */
    int size();

    /**
     * Gets whether there are no rail sections at this position
     *
     * @return True if empty
     */
    boolean isEmpty();

    /**
     * Adds a section to be included
     *
     * @param sectionToAdd Section to add
     * @return Updated object, might be same instance.
     *         Returns null if the section was already added.
     */
    TrackRailsSectionsAtPosition tryAdd(TrackRailsSingleNodeElement sectionToAdd);

    /**
     * Removes a section so it is no longer included
     *
     * @param sectionToRemove Section to remove
     * @return Updated object, might be same instance.
     *         Returns null if the section was not added here, and was
     *         not removed as a result.
     */
    TrackRailsSectionsAtPosition tryRemove(TrackRailsSingleNodeElement sectionToRemove);

    /**
     * Creates a track rails section at position object for a single section of rails
     *
     * @param section
     * @return TrackRailsSectionsAtPosition
     */
    public static TrackRailsSectionsAtPosition single(final TrackRailsSingleNodeElement section) {
        return new TrackRailsSectionsAtPosition() {
            @Override
            public List<IntVector3> rails() {
                return Collections.singletonList(section.rail());
            }

            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public int size() {
                return 1;
            }

            @Override
            public List<TrackRailsSingleNodeElement> values() {
                return Collections.singletonList(section);
            }

            @Override
            public TrackRailsSectionsAtPosition tryAdd(TrackRailsSingleNodeElement sectionToAdd) {
                if (section == sectionToAdd) {
                    return null;
                }

                // Upgrade to a List
                return new TrackRailsSectionsAtPositionList(section, sectionToAdd);
            }

            @Override
            public TrackRailsSectionsAtPosition tryRemove(TrackRailsSingleNodeElement sectionToRemove) {
                return (section == sectionToRemove) ? NONE : null;
            }
        };
    }

    /**
     * List of rail sections at a position. Always contains 2 or more elements. When removing
     * an element results in a list of 1 element or less, it returns a new list instead.
     */
    public static final class TrackRailsSectionsAtPositionList implements TrackRailsSectionsAtPosition {
        private final List<TrackRailsSingleNodeElement> sections;
        private List<IntVector3> rails;

        public TrackRailsSectionsAtPositionList(TrackRailsSingleNodeElement first, TrackRailsSingleNodeElement second) {
            sections = new ArrayList<>(4); // We assume 4 is probably on the higher end stored at a single block
            sections.add(first);
            sections.add(second);

            IntVector3 firstRail = first.rail();
            IntVector3 secondRail = second.rail();
            if (firstRail.equals(secondRail)) {
                rails = Collections.singletonList(firstRail);
            } else {
                rails = new ArrayList<>(2);
                rails.add(firstRail);
                rails.add(secondRail);
            }
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public int size() {
            return sections.size();
        }

        @Override
        public List<TrackRailsSingleNodeElement> values() {
            return Collections.unmodifiableList(sections);
        }

        @Override
        public List<IntVector3> rails() {
            // Rails are lazily initialized when elements are added/removed
            // Avoids unneeded lag rebuilding the list when many of them are
            // added/removed one at a time.
            // Skip bounds check for the first two elements (we know its 2 or more in size)
            List<IntVector3> rails = this.rails;
            if (rails == null) {
                int numSections = sections.size();
                int i = 1;

                rails = new ArrayList<IntVector3>(numSections);
                rails.add(sections.get(0).rail());
                do {
                    IntVector3 sectionRails = sections.get(i).rail();
                    if (!rails.contains(sectionRails)) {
                        rails.add(sectionRails);
                    }
                } while (++i < numSections);

                this.rails = rails;
            }
            return rails;
        }

        @Override
        public TrackRailsSectionsAtPosition tryAdd(TrackRailsSingleNodeElement sectionToAdd) {
            if (sections.contains(sectionToAdd)) {
                return null;
            }

            sections.add(sectionToAdd);
            rails = null; // Lazily re-initialize on next rails() call
            return this;
        }

        @Override
        public TrackRailsSectionsAtPosition tryRemove(TrackRailsSingleNodeElement sectionToRemove) {
            if (sections.size() == 2) {
                if (sections.get(0) == sectionToRemove) {
                    return single(sections.get(1));
                } else if (sections.get(1) == sectionToRemove) {
                    return single(sections.get(0));
                } else {
                    return null;
                }
            }

            if (sections.remove(sectionToRemove)) {
                rails = null; // Lazily re-initialize on next rails() call
                return this;
            }

            return null;
        }
    }

    /**
     * Extends a HashMap of block positions to sections at these positions to include some
     * helper methods for adding/removing/iterating sections at block positions.
     */
    public static final class Map extends HashMap<IntVector3, TrackRailsSectionsAtPosition> {
        private static final long serialVersionUID = 8352853908003741766L;

        /**
         * Wraps the values mapped at a block position as a collection of rail sections
         *
         * @param blockPos Block position
         * @return collection of rail sections at the block position
         */
        public Collection<TrackRailsSingleNodeElement> getSections(IntVector3 blockPos) {
            return new CollectionWrapper(blockPos);
        }

        /**
         * Tries to add a section to this map at a block position if it not already added
         *
         * @param blockPos Block position
         * @param section Section to add
         * @return True if added
         */
        public boolean addSection(IntVector3 blockPos, TrackRailsSingleNodeElement section) {
            AddToMapComputeFunction compute = new AddToMapComputeFunction(section);
            super.compute(blockPos, compute);
            return compute.added;
        }

        /**
         * Tries to remove a section from this map at a block position if it is present
         *
         * @param blockPos Block position
         * @param section Section to remove
         * @return True if removed
         */
        public boolean removeSection(IntVector3 blockPos, TrackRailsSingleNodeElement section) {
            RemoveFromMapComputeFunction compute = new RemoveFromMapComputeFunction(section);
            super.computeIfPresent(blockPos, compute);
            return compute.removed;
        }

        /**
         * Compute function that adds a section to a mapping by block position. Can be used to check
         * whether the element was added.
         */
        private static class AddToMapComputeFunction implements BiFunction<IntVector3, TrackRailsSectionsAtPosition, TrackRailsSectionsAtPosition> {
            /** Set to true if the element was added */
            public boolean added = false;
            private final TrackRailsSingleNodeElement section;

            public AddToMapComputeFunction(TrackRailsSingleNodeElement section) {
                this.section = section;
            }

            @Override
            public TrackRailsSectionsAtPosition apply(IntVector3 blockPos, TrackRailsSectionsAtPosition sectionsAtPosition) {
                if (sectionsAtPosition == null) {
                    added = true;
                    return single(section);
                }

                TrackRailsSectionsAtPosition updated = sectionsAtPosition.tryAdd(section);
                if (added = (updated != null)) {
                    return updated;
                } else {
                    return sectionsAtPosition;
                }
            }
        }

        /**
         * Compute function taht removes a section from a ampping by block position. Can be used to check
         * whether the element was removed.
         */
        private static class RemoveFromMapComputeFunction implements BiFunction<IntVector3, TrackRailsSectionsAtPosition, TrackRailsSectionsAtPosition> {
            /** Set to true if the element was removed */
            public boolean removed = false;
            private final TrackRailsSingleNodeElement section;

            public RemoveFromMapComputeFunction(TrackRailsSingleNodeElement section) {
                this.section = section;
            }

            @Override
            public TrackRailsSectionsAtPosition apply(IntVector3 t, TrackRailsSectionsAtPosition sectionsAtPosition) {
                if (sectionsAtPosition == null) {
                    removed = false;
                    return null;
                }

                TrackRailsSectionsAtPosition updated = sectionsAtPosition.tryRemove(section);
                if (updated == null) {
                    removed = false;
                    return sectionsAtPosition; // Section was not stored here, keep old value
                } else if (updated.isEmpty()) {
                    removed = true;
                    return null; // Instead of storing NONE constant, remove from map
                } else {
                    removed = true;
                    return updated; // Section was removed
                }
            }
        }

        /**
         * Wraps the positions at a single block stored in a map and presents it as a
         * collection of rail sections.
         */
        private final class CollectionWrapper implements Collection<TrackRailsSingleNodeElement> {
            private final IntVector3 pos;
            private TrackRailsSectionsAtPosition curr;

            private CollectionWrapper(IntVector3 pos) {
                this.pos = pos;
                this.curr = Map.this.getOrDefault(pos, NONE);
            }

            @Override
            public int size() {
                return curr.size();
            }

            @Override
            public boolean isEmpty() {
                return curr.isEmpty();
            }

            @Override
            public Iterator<TrackRailsSingleNodeElement> iterator() {
                if (curr.isEmpty()) {
                    return Collections.emptyIterator();
                } else {
                    // Note: relies on reliable order of values()
                    return new Iterator<TrackRailsSingleNodeElement>() {
                        TrackRailsSingleNodeElement last = null;
                        List<TrackRailsSingleNodeElement> values = curr.values();
                        int index = 0;

                        @Override
                        public boolean hasNext() {
                            return index < values.size();
                        }

                        @Override
                        public TrackRailsSingleNodeElement next() {
                            TrackRailsSingleNodeElement value;
                            try {
                                value = values.get(index);
                            } catch (IndexOutOfBoundsException ex) {
                                throw new NoSuchElementException();
                            }

                            index++;
                            last = value;
                            return value;
                        }

                        @Override
                        public void remove() {
                            if (last == null) {
                                throw new IllegalStateException("Did not call next() before calling remove()");
                            }

                            if (CollectionWrapper.this.remove(last)) {
                                values = curr.values();
                            }

                            last = null;
                            index--;
                        }
                    };
                }
            }

            @Override
            public boolean add(TrackRailsSingleNodeElement e) {
                TrackRailsSectionsAtPosition updated = curr.tryAdd(e);
                if (updated == null) {
                    return false;
                }

                if (updated != curr) {
                    curr = updated;
                    Map.this.put(pos, updated);
                }

                return true;
            }

            @Override
            public boolean remove(Object o) {
                TrackRailsSectionsAtPosition updated = curr.tryRemove((TrackRailsSingleNodeElement) o);
                if (updated == null) {
                    return false;
                }

                if (updated != curr) {
                    curr = updated;
                    if (updated.isEmpty()) {
                        Map.this.remove(pos);
                    } else {
                        Map.this.put(pos, updated);
                    }
                }

                return true;
            }

            @Override
            public void clear() {
                if (!curr.isEmpty()) {
                    Map.this.remove(pos);
                    curr = NONE;
                }
            }

            @Override
            public boolean contains(Object o) {
                return curr.values().contains(o);
            }

            @Override
            public boolean containsAll(Collection<?> c) {
                return curr.values().containsAll(c);
            }

            @Override
            public Object[] toArray() {
                return curr.values().toArray();
            }

            @Override
            public <T> T[] toArray(T[] a) {
                return curr.values().toArray(a);
            }

            @Override
            public boolean addAll(Collection<? extends TrackRailsSingleNodeElement> c) {
                return CollectionBasics.addAll(this, c);
            }

            @Override
            public boolean removeAll(Collection<?> c) {
                return CollectionBasics.removeAll(this, c);
            }

            @Override
            public boolean retainAll(Collection<?> c) {
                return CollectionBasics.retainAll(this, c);
            }
        }
    }
}
