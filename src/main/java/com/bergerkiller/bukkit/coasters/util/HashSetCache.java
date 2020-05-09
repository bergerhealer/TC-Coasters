package com.bergerkiller.bukkit.coasters.util;

import java.util.HashSet;
import java.util.Set;

/**
 * Simple implementation of {@link ObjectCache} that produces empty hash sets.
 * Convenience class so that the sets created can have generic element types.
 */
public class HashSetCache {
    @SuppressWarnings("rawtypes")
    private final ObjectCache<Set> _cache = ObjectCache.create(HashSet::new, HashSet::clear);

    /**
     * Creates a new HashSetCache, creating new hash sets using the default constructor
     * and clearing them when re-using.
     * 
     * @return HashSet Cache
     */
    public static HashSetCache create() {
        return new HashSetCache();
    }

    /**
     * Retrieves a new object from this cache, or creates one if more are needed
     * 
     * @return created object
     */
    @SuppressWarnings("unchecked")
    public <E> Set<E> createNew() {
        return _cache.createNew();
    }

    /**
     * Resets all objects previously created to their initial state, invalidating them.
     * This method should be called once done with all created objects.
     */
    public void reset() {
        _cache.reset();
    }
}
