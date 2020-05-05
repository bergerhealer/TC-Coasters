package com.bergerkiller.bukkit.coasters.tracks.path;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.tracks.TrackConnectionPath;

/**
 * A single node on a discrete version of the connection path.
 * Only one discrete path can exist at one time, this memory is cached and reused.
 */
public class Node {
    private static Node[] node_cache = new Node[] { new Node(), new Node() };
    private static final AtomicInteger node_index = new AtomicInteger(0);
    public Node next;
    public final Vector position;
    public double theta;
    public double distanceToNext;

    private Node() {
        this.position = new Vector();
    }

    /**
     * Inserts nodes between this node and this node's end of the chain until the
     * difference falls below the precision specified. The path is defined from start to finish,
     * with max_distance used to abort path construction early on.
     * 
     * @param path The path used to calculate the node positions
     * @param precision The precision at which to subdivide the path
     * @param max_distance The maximum distance above which to stop defining further
     * @return total distance of the computed path
     */
    public double define(TrackConnectionPath path, double precision, double max_distance) {
        // Process in a loop
        double totalDistance = 0.0;
        Node current = this;
        while (true) {
            // Insert two nodes between current and current's next node
            // Keep the original distance to next, we need it for termination
            double currentNextDistance = current.distanceToNext;
            double t0 = current.theta;
            double t1 = current.next.theta;
            double ht = 0.5 * (t0 + t1);

            // Insert three nodes between current and next and compute their distances
            // We insert three so we can avoid the S-curve trap that can occur with a quadratic bezier curve
            // We don't insert two because the theta values are slow in binary (0.333...) (0.5* is fast!)
            current.insertThree();

            // Compute positions and distances between the creates nodes
            Node m0 = current.next;
            Node m1 = m0.next;
            Node m2 = m1.next;
            m0.computePosition(path, 0.5 * (t0 + ht));
            m1.computePosition(path, ht);
            m2.computePosition(path, 0.5 * (t1 + ht));
            double summed = current.computeDistanceToNext() +
                            m0.computeDistanceToNext() +
                            m1.computeDistanceToNext() +
                            m2.computeDistanceToNext();
 
            // If distance difference is small enough, then this section is complete
            if ((summed - currentNextDistance) < precision) {
                totalDistance += summed;
                current = m2.next;
                if (current.next == null || totalDistance > max_distance) {
                    break;
                }
            }
        }
        return totalDistance;
    }

    public void computePosition(TrackConnectionPath path, double theta) {
        this.theta = theta;
        path.getPosition(theta, this.position);
    }

    public double computeDistanceToNext() {
        return this.distanceToNext = this.position.distance(this.next.position);
    }

    /**
     * Inserts tree more nodes between this node and this node's next node.
     */
    public void insertThree() {
        int index = node_index.getAndAdd(3);
        Node last;

        // Get the last node. (Ab)use out of bounds exception to resize the cache if needed
        try {
            last = node_cache[index + 2];
        } catch (ArrayIndexOutOfBoundsException ex) {
            reserve();
            last = node_cache[index + 2];
        }

        last.next = this.next;
        this.next = node_cache[index];
        this.next.next = node_cache[index + 1];
        this.next.next.next = last;
    }

    /**
     * Resets the node cache and initializes it with the start and end nodes of the path
     * to compute.
     * 
     * @param path
     * @param t0
     * @param t1
     * @return root node
     */
    public static Node init(TrackConnectionPath path, double t0, double t1) {
        node_index.set(2);
        Node root = node_cache[0];
        root.next = node_cache[1];
        root.next.next = null;
        root.computePosition(path, t0);
        root.next.computePosition(path, t1);
        root.computeDistanceToNext();
        return root;
    }

    // Doubles the capacity of the cache, with a size of at least 512
    private static void reserve() {
        int old_size = node_cache.length;
        int new_size = Math.max(512, old_size * 2);
        node_cache = Arrays.copyOf(node_cache, new_size);
        for (int i = old_size; i < new_size; i++) {
            node_cache[i] = new Node();
        }
    }
}
