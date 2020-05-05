package com.bergerkiller.bukkit.coasters.tracks.path;

import java.util.Arrays;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.tracks.TrackConnectionPath;

/**
 * Discrete version of a connection path at a sufficient precision.
 * Does not compute all the same things a standard Traincarts path
 * computes. Holds an internal cache of nodes to more efficiently
 * calculate different paths with repeated calls to {@link #init(path, t0, t1)}.
 */
public class Discrete {
    /**
     * Singleton instance, can be re-used
     */
    public static final Discrete INSTANCE = new Discrete();
    public final Node head = new Node();
    public final Node tail = new Node();
    private Node[] node_cache = new Node[] { head, tail };
    private TrackConnectionPath path;
    private int node_index = 0;
    private double totalDistance;

    /**
     * Resets the node cache and initializes it with the full path
     * to compute.
     * 
     * @param path Input bezier path
     * @return this
     */
    public Discrete init(TrackConnectionPath path) {
        return init(path, 0.0, 1.0);
    }

    /**
     * Resets the node cache and initializes it with the start and end nodes of the path
     * to compute.
     * 
     * @param path Input bezier path
     * @param t0 Start theta
     * @param t1 End theta
     * @return this
     */
    public Discrete init(TrackConnectionPath path, double t0, double t1) {
        this.path = path;
        this.node_index = 2;
        head.next = tail;
        tail.next = null;
        head.computePosition(t0);
        tail.computePosition(t1);
        head.computeDistanceToNext();
        totalDistance = head.define();
        return this;
    }

    /**
     * Gets the total distance of the computed discrete path
     * 
     * @return total distance
     */
    public double getTotalDistance() {
        return this.totalDistance;
    }

    /**
     * Walks this discrete path from head to tail to find the exact theta value
     * at the distance
     * 
     * @param distance Distance from the head
     * @return theta at the distance
     */
    public double findTheta(double distance) {
        if (distance <= 0.0) {
            return 0.0;
        } else if (distance >= this.totalDistance) {
            return 1.0;
        } else {
            // Walk down the produced node linked list and find the exact theta at distance
            Node current = head;
            double remaining = distance;
            while (current != null) {
                if (current.distanceToNext >= remaining) {
                    // End reached. Interpolate positions using remaining distance
                    double s = remaining / current.distanceToNext;
                    return (1.0 - s) * current.theta + s * current.next.theta;
                }
                remaining -= current.distanceToNext;
                current = current.next;
            }

            // Past end of path (weird!)
            return 1.0;
        }
    }

    // Doubles the capacity of the cache, with a size of at least 512
    private void reserve() {
        int old_size = node_cache.length;
        int new_size = Math.max(512, old_size * 2);
        node_cache = Arrays.copyOf(node_cache, new_size);
        for (int i = old_size; i < new_size; i++) {
            node_cache[i] = new Node();
        }
    }

    /**
     * A single node on a discrete version of the connection path.
     * Only one discrete path can exist at one time, this memory is cached and reused.
     */
    public final class Node {
        private static final double PRECISION = 1e-4;
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
         * @return total distance of the computed path
         */
        public double define() {
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
                m0.computePosition(0.5 * (t0 + ht));
                m1.computePosition(ht);
                m2.computePosition(0.5 * (t1 + ht));
                double summed = current.computeDistanceToNext() +
                                m0.computeDistanceToNext() +
                                m1.computeDistanceToNext() +
                                m2.computeDistanceToNext();
     
                // If distance difference is small enough, then this section is complete
                if ((summed - currentNextDistance) < PRECISION) {
                    totalDistance += summed;
                    current = m2.next;
                    if (current.next == null) {
                        break;
                    }
                }
            }
            return totalDistance;
        }

        private void computePosition(double theta) {
            this.theta = theta;
            path.getPosition(theta, this.position);
        }

        private double computeDistanceToNext() {
            return this.distanceToNext = this.position.distance(this.next.position);
        }

        /**
         * Inserts tree more nodes between this node and this node's next node.
         */
        private void insertThree() {
            int index = node_index;
            node_index += 3;
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
    }
}
