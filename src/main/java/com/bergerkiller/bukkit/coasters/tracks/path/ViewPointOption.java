package com.bergerkiller.bukkit.coasters.tracks.path;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.TCCoastersUtil;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnectionPath;
import com.bergerkiller.bukkit.common.math.Matrix4x4;

/**
 * Helper class for computing the point a player is looking at, based on a view matrix
 */
public class ViewPointOption {
    public static final ViewPointOption NONE = new ViewPointOption();
    public final ViewPointOption prev, next;
    private final Vector position = new Vector();
    public double theta = 0.0;
    public double distance;

    // No alternative constructor
    private ViewPointOption() {
        this.prev = null;
        this.next = null;
        this.theta = 0.0;
        this.distance = Double.MAX_VALUE;
    }

    public ViewPointOption(ViewPointOption prev, int len) {
        this.prev = prev;
        this.next = (len <= 1) ? null : new ViewPointOption(this, len-1);
    }

    public void update(TrackConnectionPath path, Matrix4x4 playerViewMatrixInverted, double theta) {
        this.theta = theta;
        path.getPosition(theta, this.position);
        playerViewMatrixInverted.transformPoint(this.position);
        this.distance = TCCoastersUtil.distanceSquaredXY(this.position);
    }

    public void assign(ViewPointOption opt) {
        this.theta = opt.theta;
        this.distance = opt.distance;
    }

    /**
     * Recursive/loop function that narrows down the point on a path a player is looking at
     * 
     * @param path The path to wander
     * @param playerViewMatrixInverted View matrix of the player, inverted
     * @param head Start option (created with size=5)
     * @param alternative Alternative view point option, if better than we produce, is returned instead
     * @return bestfit view point option
     */
    public static ViewPointOption findClosestPointOptionInView(TrackConnectionPath path, Matrix4x4 playerViewMatrixInverted, ViewPointOption head, ViewPointOption alternative) {
        // Take the middle point and the head/tail of the 5-point view point options
        ViewPointOption mid = head.next.next;
        ViewPointOption tail = mid.next.next;

        search:
        while (true) {
            // Calculate middle 3 points using head and tail theta values
            double ht = 0.5 * (head.theta + tail.theta);
            mid.prev.update(path, playerViewMatrixInverted, 0.5 * (head.theta + ht));
            mid.update(path, playerViewMatrixInverted, ht);
            mid.next.update(path, playerViewMatrixInverted, 0.5 * (tail.theta + ht));

            // Narrow the search window until we've found a peak on the curve
            // If we find two peaks, then split the search in two
            ViewPointOption best = head;

            narrowing: {
                ViewPointOption other = tail;
                while (best.distance >= best.next.distance) {
                    best = best.next;
                    if (best == other) {
                        break narrowing; // Found peak, narrow the window
                    }
                }
                while (other.distance >= other.prev.distance) {
                    other = other.prev;
                    if (best == other) {
                        break narrowing; // Found peak, narrow the window
                    }
                }

                // There are two curves here. Split in two separate search operations.
                // Original method:
                //   ViewPointOption a = findClosestPointOptionInView(playerViewMatrixInverted, head.theta, mid.theta);
                //   ViewPointOption b = findClosestPointOptionInView(playerViewMatrixInverted, mid.theta, tail.theta);
                //   return (a.distance > b.distance) ? b : a;

                // New:
                // One is run in a recursive function invocation, the other continues running here.
                // It replaces any previous (worse) alternative we found prior
                ViewPointOption new_alternative_head = new ViewPointOption(null, 5);
                new_alternative_head.assign(head);
                new_alternative_head.next.next.next.next.assign(mid);
                alternative = findClosestPointOptionInView(path, playerViewMatrixInverted, new_alternative_head, alternative);

                // Resume this function between mid and tail
                head.assign(mid);
                continue search;
            }

            // Found a 'best' node that follows a simple parabolic curve ^
            // Find best view point option with lowest distance from where the player is looking
            if (best == head || best.prev == head) {
                // Only tail has to be assigned, head is already good
                tail.assign(best.next);
            } else if (best == tail || best.next == tail) {
                // Only head has to be assigned, tail is already good
                head.assign(best.prev);
            } else {
                // Head and tail both must be assigned
                head.assign(best.prev);
                tail.assign(best.next);
            }

            // If difference in distance is negligible, return the best result
            if ((head.distance - best.distance) < 1e-5 && (tail.distance - best.distance) < 1e-5) {
                return (best.distance > alternative.distance) ? alternative : best;
            }
        }
    }
}
