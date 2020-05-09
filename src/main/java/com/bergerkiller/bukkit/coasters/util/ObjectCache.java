package com.bergerkiller.bukkit.coasters.util;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.bergerkiller.bukkit.common.utils.CommonUtil;

/**
 * Simple cache of objects, avoiding the need to create large data structures from scratch
 * frequently in a loop.
 *
 * @param <T>
 */
public class ObjectCache<T> {
    private final Supplier<Object> _allocator;
    private final Consumer<Object> _resetMethod;
    private Object[] _cache;
    private int _index;

    /**
     * Creates a new Object Cache, creating the object using the allocator method specified,
     * and resetting the state of the object using the reset method specified.
     * 
     * @param allocator The method producing new objects
     * @param resetMethod The method resetting existing objects to constructed state
     * @return Object Cache
     */
    public static <T, I> ObjectCache<T> create(Supplier<I> allocator, Consumer<I> resetMethod) {
        return new ObjectCache<T>(allocator, resetMethod);
    }

    protected ObjectCache(Supplier<?> allocator, Consumer<?> resetMethod) {
        _allocator = CommonUtil.unsafeCast(allocator);
        _resetMethod = CommonUtil.unsafeCast(resetMethod);
        _cache = new Object[] {allocator.get()};
        _index = 0;
    }

    /**
     * Retrieves a new object from this cache, or creates one if more are needed
     * 
     * @return created object
     */
    @SuppressWarnings("unchecked")
    public T createNew() {
        if (_index >= _cache.length) {
            // Double size of cache
            Object[] new_cache = Arrays.copyOf(_cache, _cache.length << 1);
            for (int i = _cache.length; i < new_cache.length; i++) {
                new_cache[i] = _allocator.get();
            }
            _cache = new_cache;
        }
        return (T) _cache[_index++];
    }

    /**
     * Resets all objects previously created to their initial state, invalidating them.
     * This method should be called once done with all created objects.
     */
    public void reset() {
        while (_index > 0) {
            _resetMethod.accept(_cache[--_index]);
        }
    }
}
