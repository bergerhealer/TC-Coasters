package com.bergerkiller.bukkit.coasters.util;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.function.Consumer;

import com.bergerkiller.bukkit.common.collections.ImplicitlySharedSet;
import com.bergerkiller.bukkit.common.utils.CommonUtil;

/**
 * Allows scheduling a function to be run after a set delay (or as soon as possible with
 * delay 0), on an object. The {@link #runAll()} method is called by TC-Coasters and
 * runs all the tasks scheduled after the set delay.
 *
 * @param <T>
 */
public class QueuedTask<T> {
    private static final ImplicitlySharedSet<QueuedTask<?>> scheduled = new ImplicitlySharedSet<QueuedTask<?>>();
    private static final Precondition<Object> no_precondition = object -> { return true; };
    private final int delay;
    private final Precondition<T> precondition;
    private final Consumer<T> function;
    private IdentityHashMap<T, Entry<T>> queuedByObject = new IdentityHashMap<>();
    private final Deque<Entry<T>> queue = new ArrayDeque<Entry<T>>();

    /**
     * Constructs a new delayed Queued Task function
     * 
     * @param delay Delay in ticks until this function is run
     * @param precondition This function is called first, and the function is only executed if it returns True
     * @param function The function to execute
     */
    protected QueuedTask(int delay, Precondition<T> precondition, Consumer<T> function) {
        this.delay = delay;
        this.precondition = precondition;
        this.function = function;
    }

    /**
     * Creates a new Queued Task function that runs without delay (next tick)
     *
     * @param precondition This function is called first, and the function is only executed if it returns True
     * @param function The function to execute
     */
    public static <T> QueuedTask<T> create(Precondition<T> precondition, Consumer<T> function) {
        return new QueuedTask<T>(0, precondition, function);
    }

    /**
     * Creates a new delayed Queued Task function
     * 
     * @param delay Delay in ticks until this function is run
     * @param precondition This function is called first, and the function is only executed if it returns True
     * @param function The function to execute
     */
    public static <T> QueuedTask<T> create(int delay, Precondition<T> precondition, Consumer<T> function) {
        return new QueuedTask<T>(delay, precondition, function);
    }

    /**
     * Schedules a new object to be passed as parameter to the function and
     * executed. If this object was already scheduled, the original
     * schedule is cancelled, causing the delay to reset.
     * 
     * @param object Object to schedule execution
     */
    public void schedule(T object) {
        final int time = CommonUtil.getServerTicks() + delay;

        // Add a new entry if for this object, no task was scheduled yet.
        // Also add it to an ordered queue, so that scheduled tasks are executed in the same order they are scheduled.
        // Remove (cancel) an already scheduled task whose time is different from the requested time
        // If the time is unchanged (already scheduled), ignore and keep the existing (earlier) entry.
        // To avoid having to scan the entire queue to remove the old entry, mark it cancelled instead.
        queuedByObject.compute(object, (obj, entry) -> {
            if (entry != null && entry.state == EntryState.SCHEDULED) {
                if (entry.time == time) {
                    return entry; // Already scheduled, same time. Unchanged.
                } else {
                    entry.state = EntryState.CANCELLED; // Cancel old entry. New entry will be re-added at the end.
                }
            }

            boolean isFirstScheduledEntry = queue.isEmpty();
            entry = new Entry<>(obj, time);
            queue.add(entry);
            if (isFirstScheduledEntry) {
                scheduled.add(QueuedTask.this);
            }
            return entry;
        });
    }

    /**
     * Gets whether an object is scheduled to run soon.
     * This is a check by reference.
     * 
     * @param object
     * @return True if scheduled
     */
    public boolean isScheduled(T object) {
        Entry<T> e = queuedByObject.get(object);
        return e != null && e.state == EntryState.SCHEDULED;
    }

    // Called by runAll() to run this one task
    private void run(int time) {
        // Take all items from the queue which need to run right now and process them.
        // We do not mutate the identity hashmap item by item as the remove() shrink operation is far too slow.
        // Instead, we do this shrinking with a rebuild after the fact.
        boolean hasRunTasks = false;
        for (Entry<T> e; (e = queue.poll()) != null;) {
            // Ignore cancelled or already-run entries
            if (e.state != EntryState.SCHEDULED) {
                continue;
            }

            // When reaching an entry that still has a pending delay, re-add and stop processing.
            if (time < e.time) {
                queue.addFirst(e);
                break;
            }

            // Mark to be run, so if a concurrent schedule happens, a new entry is added again
            e.state = EntryState.ALREADY_RUN;

            // Run the task
            T object = e.object;
            if (precondition.canExecute(object)) {
                function.accept(object);
            }

            // Something has run, so it's worth compressing the queuedByObject map
            hasRunTasks = true;
        }

        // Rebuild the identity hashmap to omit entries that have already been run (if not empty)
        // Only do this when we mutated something interesting or the queue is emptied.
        if (queue.isEmpty()) {
            if (!queuedByObject.isEmpty()) {
                queuedByObject = new IdentityHashMap<>();
            }
        } else if (hasRunTasks) {
            IdentityHashMap<T, Entry<T>> newQueuedByObject = new IdentityHashMap<>(queue.size());
            queue.forEach(e -> {
                if (e.state == EntryState.SCHEDULED) {
                    newQueuedByObject.put(e.object, e);
                }
            });
            queuedByObject = newQueuedByObject;
        }
    }

    /**
     * Runs all scheduled queued tasks for all objects, if any.
     */
    public static void runAll() {
        if (!scheduled.isEmpty()) {
            // Process the queue
            int time = CommonUtil.getServerTicks();
            for (QueuedTask<?> task : scheduled.cloneAsIterable()) {
                task.run(time);
            }

            // Remove tasks from the scheduled set that have no tasks queued up
            // It will be re-added the very next time a new task is scheduled.
            scheduled.removeIf(queuedTask -> queuedTask.queue.isEmpty());
        }
    }

    /**
     * A single scheduled execution entry
     *
     * @param <T>
     */
    private static final class Entry<T> {
        public final T object;
        public final int time;
        public EntryState state;

        public Entry(T object, int time) {
            this.object = object;
            this.time = time;
            this.state = EntryState.SCHEDULED;
        }
    }

    private enum EntryState {
        /** Entry is scheduled to run later */
        SCHEDULED,
        /** Entry was cancelled and will be removed later */
        CANCELLED,
        /** Entry has already run, and will be cleaned up later */
        ALREADY_RUN
    }

    /**
     * Precondition method
     *
     * @param <T>
     */
    public static interface Precondition<T> {
        /**
         * Whether we can execute the task right now on this object
         * 
         * @param object
         * @return True if it can be executed
         */
        public boolean canExecute(T object);

        /**
         * No precondition (always true)
         * 
         * @return no precondition constant
         */
        public static <T> Precondition<T> none() {
            return CommonUtil.unsafeCast(no_precondition);
        }
    }
}
