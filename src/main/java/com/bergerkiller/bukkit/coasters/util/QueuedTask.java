package com.bergerkiller.bukkit.coasters.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
    private final List<Entry<T>> queue = new ArrayList<Entry<T>>();
    private int queueStart = 0;

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
        int time = CommonUtil.getServerTicks() + delay;
        for (int i = queueStart; i < queue.size(); i++) {
            Entry<T> entry = queue.get(i);
            if (entry.object == object) {
                if (entry.time == time) {
                    queue.set(i, new Entry<T>(object, time));
                    return;
                } else {
                    queue.remove(i);
                    break;
                }
            }
        }
        queue.add(new Entry<T>(object, time));
        scheduled.add(this);
    }

    /**
     * Gets whether an object is scheduled to run soon.
     * This is a check by reference.
     * 
     * @param object
     * @return True if scheduled
     */
    public boolean isScheduled(T object) {
        for (int i = queueStart; i < queue.size(); i++) {
            if (queue.get(i).object == object) {
                return true;
            }
        }
        return false;
    }

    // Called by runAll() to run this one task
    private void run(int time) {
        // Figure out the number of tasks from 0 we can run right now
        queueStart = 0;
        while (queueStart < queue.size() && time >= queue.get(queueStart).time) {
            queueStart++;
        }
        if (queueStart == 0) {
            return;
        }

        // Run all these
        for (int i = 0; i < queueStart; i++) {
            T object = queue.get(i).object;
            if (precondition.canExecute(object)) {
                function.accept(object);
            }
        }

        // Clear from queue
        queue.subList(0, queueStart).clear();
        queueStart = 0;
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
            Iterator<QueuedTask<?>> iter = scheduled.iterator();
            while (iter.hasNext()) {
                if (iter.next().queue.isEmpty()) {
                    iter.remove();
                }
            }
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

        public Entry(T object, int time) {
            this.object = object;
            this.time = time;
        }
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
