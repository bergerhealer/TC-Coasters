package com.bergerkiller.bukkit.coasters.animation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnectionState;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeState;
import com.bergerkiller.bukkit.coasters.world.CoasterWorld;
import com.bergerkiller.bukkit.coasters.world.CoasterWorldComponent;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.entity.CommonEntity;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.cache.RailMemberCache;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;

/**
 * Tracks the running node animations and makes sure trains move along with them
 */
public class TrackAnimationWorld implements CoasterWorldComponent {
    private final CoasterWorld _world;
    private final Map<TrackNode, TrackAnimation> _animations = new IdentityHashMap<>();
    private final Map<TrackConnection, TrackConnectionState> _finishedConnections = new IdentityHashMap<>();
    private final List<RailPath.Point> _pointsCache = new ArrayList<RailPath.Point>();

    public TrackAnimationWorld(CoasterWorld world) {
        this._world = world;
    }

    @Override
    public final CoasterWorld getWorld() {
        return this._world;
    }

    public void animate(TrackNode node, TrackNodeState target, TrackConnectionState[] connections, double duration) {
        _animations.put(node, new TrackAnimation(node, target, connections, MathUtil.floor(duration * 20.0)));
    }

    public void updateAll() {
        if (_animations.isEmpty()) {
            return;
        }

        // Store the previous state of minecarts on the connections
        Map<TrackConnection, List<RailPath.Point>> connectionPoints = new IdentityHashMap<>();
        Map<MinecartMember<?>, TrackMemberState> members = new IdentityHashMap<>();
        for (TrackAnimation anim : _animations.values()) {
            for (TrackConnection conn : anim.node.getConnections()) {
                if (connectionPoints.put(conn, Collections.emptyList()) == null) {
                    findMembersOn(conn).forEach(state -> {
                        TrackMemberState old_state = members.put(state.member, state);
                        if (old_state != null && old_state.isOnPath() && !state.isOnPath()) {
                            members.put(old_state.member, old_state);
                        }
                    });
                }
            }
        }

        // Run the animations
        for (TrackAnimation anim : _animations.values()) {
            // Delete connections not part of the target animation state at the start of the animation
            if (anim.connections != null && anim.isAtStart()) {
                for (TrackConnection conn : anim.node.getConnections()) {
                    TrackNode conn_node = conn.getOtherNode(anim.node);

                    boolean exists = false;
                    for (TrackConnectionState target_connection : anim.connections) {
                        if (target_connection.getOtherNode(anim.node).isReference(conn_node)) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        conn.remove();
                    }
                }
            }

            // Update state of the node
            if (anim.isAtEnd()) {
                // Set to target
                anim.node.setState(anim.target);

                // Delete all connections that have existed for this node
                if (anim.connections != null) {
                    getWorld().getTracks().disconnectAll(anim.node, false);
                }
            } else {
                // Update using lerp
                double theta = (double) anim.ticks / (double) anim.ticks_total;
                Vector pos = MathUtil.lerp(anim.start.position, anim.target.position, theta);

                Quaternion q0 = Quaternion.fromLookDirection(anim.node.getDirection(), anim.start.orientation);
                Quaternion q1 = Quaternion.fromLookDirection(anim.node.getDirection(), anim.target.orientation);
                Vector up = Quaternion.slerp(q0, q1, theta).upVector();

                anim.node.setPosition(pos);
                anim.node.setOrientation(up);
            }
        }

        // Create connections at the end of the animations
        Iterator<TrackAnimation> anim_iter;
        anim_iter = _animations.values().iterator();
        while (anim_iter.hasNext()) {
            TrackAnimation anim = anim_iter.next();
            if (anim.isAtEnd()) {
                if (anim.connections != null) {
                    for (TrackConnectionState connection : anim.connections) {
                        if (connection.isConnected(anim.node)) {
                            TrackConnection tc = getWorld().getTracks().connect(connection, false);
                            if (tc != null) {
                                _finishedConnections.put(tc, connection);
                            }
                        }
                    }
                }
                anim_iter.remove();
            } else {
                anim.ticks++;
            }
        }

        // Now that all track connections are made, add the objects
        try {
            for (Map.Entry<TrackConnection, TrackConnectionState> addedConnection : _finishedConnections.entrySet()) {
                addedConnection.getKey().onShapeUpdated();
                addedConnection.getKey().addAllObjects(addedConnection.getValue());
            }
        } finally {
            _finishedConnections.clear();
        }

        // Compute new position on the new, adjusted tracks
        for (TrackMemberState state : members.values()) {
            // Skip unloaded/dead members
            if (state.member.isUnloaded() || state.member.getEntity().isRemoved()) {
                continue;
            }

            // Compute the points of connections, cache them for if multiple members are on one
            List<RailPath.Point> points = connectionPoints.get(state.connection);
            if (points.isEmpty()) {
                loadPoints(state.connection);
                points = new ArrayList<RailPath.Point>(_pointsCache);
                connectionPoints.put(state.connection, points);
            }

            // Use theta to compute a new x/y/z
            // System.out.println("STATE THETA: " + state.theta);
            double remaining_distance = state.theta * getTotalDistance(points);
            Iterator<RailPath.Point> p_iter = points.iterator();
            RailPath.Point p_prev = p_iter.next();
            while (true) {
                RailPath.Point p = p_iter.next();
                
                double dx = p.x - p_prev.x;
                double dy = p.y - p_prev.y;
                double dz = p.z - p_prev.z;
                double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
                if (!p_iter.hasNext() || dist > remaining_distance) {
                    double theta = remaining_distance / dist;
                    double x = p_prev.x + theta * dx;
                    double y = p_prev.y + theta * dy;
                    double z = p_prev.z + theta * dz;
                    applyPosition(state.member, x, y, z);

                    // For debug: verify there is no significant difference in theta before/after adjustment
                    /*
                    TrackMemberState new_state = computeState(state.member, state.connection, points, getTotalDistance(points));
                    if (Math.abs(new_state.theta - state.theta) > 1e-4) {
                        System.out.println("HUGE THETA DIFF " + (new_state.theta - state.theta));
                    }
                    */

                    break;
                }

                remaining_distance -= dist;
                p_prev = p;
            }
        }
    }

    private void loadPoints(TrackConnection connection) {
        _pointsCache.clear();
        connection.buildPath(_pointsCache, IntVector3.ZERO, getPlugin().getSmoothness(), 0.0, 0.5);
        _pointsCache.remove(_pointsCache.size() - 1);
        connection.buildPath(_pointsCache, IntVector3.ZERO, getPlugin().getSmoothness(), 0.5, 1.0);
    }

    /**
     * Finds all Minecarts that are currently on a connection between two nodes.
     * 
     * @param connection
     * @return list of members on the connection
     */
    private Stream<TrackMemberState> findMembersOn(TrackConnection connection) {
        IntVector3 rail_a = connection.getNodeA().getRailBlock(true);
        IntVector3 rail_b = connection.getNodeB().getRailBlock(true);
        Stream<MinecartMember<?>> members;
        if (rail_a.equals(rail_b)) {
            Collection<MinecartMember<?>> members_a = RailMemberCache.findAll(rail_a.toBlock(this.getBukkitWorld()));
            if (members_a.isEmpty()) {
                return Stream.empty();
            }
            members = members_a.stream();
        } else {
            Collection<MinecartMember<?>> members_a = RailMemberCache.findAll(rail_a.toBlock(this.getBukkitWorld()));
            Collection<MinecartMember<?>> members_b = RailMemberCache.findAll(rail_b.toBlock(this.getBukkitWorld()));
            if (members_a.isEmpty() && members_b.isEmpty()) {
                return Stream.empty();
            }
            members = Stream.concat(members_a.stream(), members_b.stream());
        }

        // Compute the exact path between the two nodes right now
        loadPoints(connection);
        if (_pointsCache.size() < 2) {
            return Stream.empty(); // what?!
        }

        // Compute total length of the path
        final double total_len = getTotalDistance(_pointsCache);

        // Go by all found members and compute their theta on this path
        return members.map(member -> {
            return computeState(member, connection, _pointsCache, total_len);
        });
    }

    private static TrackMemberState computeState(MinecartMember<?> member, TrackConnection connection, List<RailPath.Point> points, double total_len) {
        if (member.isUnloaded() || member.getEntity().isRemoved()) {
            return new TrackMemberState(member, connection, 0.0);
        }

        Vector pos = member.getEntity().loc.vector();
        Iterator<RailPath.Point> p_iter = points.iterator();
        RailPath.Point p_prev = p_iter.next();
        double len_sum = 0.0;
        double lowest_distance = Double.MAX_VALUE;
        double best_len = 0.0;
        while (p_iter.hasNext()) {
            RailPath.Point p = p_iter.next();

            double dx = p.x - p_prev.x;
            double dy = p.y - p_prev.y;
            double dz = p.z - p_prev.z;
            double ls = (dx*dx) + (dy*dy) + (dz*dz);
            double len = Math.sqrt(ls);
            double theta = -(((p_prev.x - pos.getX()) * dx + (p_prev.y - pos.getY()) * dy + (p_prev.z - pos.getZ()) * dz) / ls);

            if (theta <= 0.0) {
                double dist_from_point = -(theta * len);
                if (dist_from_point < lowest_distance) {
                    lowest_distance = dist_from_point;
                    best_len = len_sum;
                }
            } else if (theta >= 1.0) {
                double dist_from_point = (theta - 1.0) * len;
                if (dist_from_point < lowest_distance) {
                    lowest_distance = dist_from_point;
                    best_len = len_sum + len;
                }
            } else {
                best_len = len_sum + theta * len;
                break;
            }

            len_sum += len;
            p_prev = p;
        }

        return new TrackMemberState(member, connection, best_len / total_len);
    }
    
    private static double getTotalDistance(List<RailPath.Point> points) {
        double len_sum = 0.0;
        Iterator<RailPath.Point> p_iter = points.iterator();
        RailPath.Point p_prev = p_iter.next();
        while (p_iter.hasNext()) {
            RailPath.Point p = p_iter.next();
            len_sum += MathUtil.distance(
                    p_prev.x, p_prev.y, p_prev.z,
                    p.x, p.y, p.z);
            p_prev = p;
        }
        return len_sum;
    }

    private static void applyPosition(MinecartMember<?> member, double x, double y, double z) {
        CommonEntity<?> entity = member.getEntity();
        entity.setPosition(x, y, z);
        entity.setPositionChanged(true);
    }
}
