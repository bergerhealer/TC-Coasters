package com.bergerkiller.bukkit.coasters.animation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.bergerkiller.bukkit.coasters.signs.actions.TrackAnimationListener;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
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
import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.common.offline.OfflineWorld;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.rails.RailLookup;

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
        animate("", node, target, connections, duration);
    }

    public void animate(String animationName, TrackNode node, TrackNodeState target, TrackConnectionState[] connections, double duration) {
        _animations.put(node, new TrackAnimation(animationName, node, target, connections, MathUtil.floor(duration * 20.0)));
    }

    @Override
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

        // Toggle up all the levers on all tcc animate signs at the current rail blocks being animated
        // In case the animation changes these signs later, doing this now avoids stuck-up levers
        {
            Map<IntVector3, RailAnimationChangeTracker> railAnimationsStarted = new HashMap<>();
            for (TrackAnimation anim : _animations.values()) {
                if (anim.isAtStart()) {
                    RailAnimationChangeTracker tracker = railAnimationsStarted.computeIfAbsent(
                            anim.node.getRailBlock(true),
                            this::initRailAnimationChangeTracker);

                    tracker.add(anim);
                }
            }
            railAnimationsStarted.values().forEach(RailAnimationChangeTracker::fireStarted);
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

                // Delete all connections that have existed for this node that no longer exist
                if (anim.connections != null) {
                    for (TrackConnection connection : anim.node.getConnections()) {
                        if (!anim.shouldKeepConnection(connection)) {
                            connection.remove();
                        }
                    }
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

        // Track finished animations, they can toggle levers on animate signs back down
        List<TrackAnimation> endedAnimations = new ArrayList<>();

        // Create connections at the end of the animations
        Iterator<TrackAnimation> anim_iter;
        anim_iter = _animations.values().iterator();
        while (anim_iter.hasNext()) {
            TrackAnimation anim = anim_iter.next();
            if (anim.isAtEnd()) {
                endedAnimations.add(anim);

                if (anim.connections != null) {
                    for (TrackConnectionState connection : anim.connections) {
                        //TODO: Why is this even happening?
                        if (connection.node_a.isReference(connection.node_b)) {
                            continue;
                        }

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
                addedConnection.getKey().setAllObjects(addedConnection.getValue());
            }
        } finally {
            _finishedConnections.clear();
        }

        // After moving the nodes around again, also rebuild the track information
        // This is basically done twice per tick when animations play, because it needs
        // track information prior to calculate where members are, and after to make
        // sure physics update correctly.
        this.getWorld().getTracks().updateAll();

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

            // Reverse points if wrong
            double distanceDiffA = points.get(0).distanceSquared(state.posA) + points.get(points.size() - 1).distanceSquared(state.posB);
            double distanceDiffB = points.get(0).distanceSquared(state.posB) + points.get(points.size() - 1).distanceSquared(state.posA);
            if (distanceDiffB < distanceDiffA) {
                Collections.reverse(points);
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
                    TrackMemberState new_state = computeMemberStates(state.connection, points, Stream.of(state.member)
                            .map(MemberOnPath::new)
                            .collect(Collectors.toList()))
                            .findFirst().orElse(null);
                    if (new_state != null && Math.abs(new_state.theta - state.theta) > 1e-4) {
                        System.out.println("HUGE THETA DIFF " + (new_state.theta - state.theta));
                    }
                     */

                    break;
                }

                remaining_distance -= dist;
                p_prev = p;
            }
        }

        // Fire off sign events after the animations have concluded
        // Must be done at the very end, because the active signs may have changed after the animation
        if (!endedAnimations.isEmpty()) {
            Map<IntVector3, RailAnimationChangeTracker> railAnimationsEnded = new HashMap<>();
            for (TrackAnimation anim : endedAnimations) {
                RailAnimationChangeTracker tracker = railAnimationsEnded.computeIfAbsent(
                        anim.node.getRailBlock(true),
                        this::initRailAnimationChangeTracker);
                tracker.add(anim);
            }
            railAnimationsEnded.values().forEach(RailAnimationChangeTracker::fireEnded);
        }
    }

    private RailAnimationChangeTracker initRailAnimationChangeTracker(IntVector3 rail) {
        RailPiece railPiece = RailPiece.create(getPlugin().getRailType(), rail.toBlock(getBukkitWorld()));
        List<RailLookup.TrackedSign> signsWithListeners = Collections.emptyList();
        for (RailLookup.TrackedSign sign : railPiece.signs()) {
            if (sign.getAction() instanceof TrackAnimationListener) {
                if (signsWithListeners.isEmpty()) {
                    signsWithListeners = new ArrayList<>();
                }
                signsWithListeners.add(sign);
            }
        }
        if (signsWithListeners.isEmpty()) {
            return RailAnimationChangeTracker.INACTIVE;
        } else {
            return new RailAnimationChangeTrackerActive(signsWithListeners);
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
        OfflineWorld world = getOfflineWorld();
        OfflineBlock rail_a = world.getBlockAt(connection.getNodeA().getRailBlock(true));
        OfflineBlock rail_b = world.getBlockAt(connection.getNodeB().getRailBlock(true));
        Stream<MinecartMember<?>> members;
        if (rail_a.equals(rail_b)) {
            members = RailLookup.findMembersOnRail(rail_a).stream();
        } else {
            Collection<MinecartMember<?>> members_a = RailLookup.findMembersOnRail(rail_a);
            Collection<MinecartMember<?>> members_b = RailLookup.findMembersOnRail(rail_b);
            members = Stream.concat(members_a.stream(), members_b.stream());
        }

        // Prepares members whose path is being calculated
        List<MemberOnPath> membersOnPath = members
                .filter(member -> !member.isUnloaded() && !member.getEntity().isRemoved())
                .map(MemberOnPath::new)
                .collect(Collectors.toList());
        if (membersOnPath.isEmpty()) {
            return Stream.empty();
        }

        // Compute the exact path between the two nodes right now
        loadPoints(connection);
        return computeMemberStates(connection, _pointsCache, membersOnPath);
    }

    private Stream<TrackMemberState> computeMemberStates(TrackConnection connection, List<RailPath.Point> points, List<MemberOnPath> membersOnPath) {
        if (points.size() < 2) {
            return Stream.empty(); // what?!
        }

        // Iterate all segments of the path
        // Calculate the total length of the path
        // Then, also calculate the (best matching) distance to reaching each member
        double calc_total_len = 0.0;
        for (MatchableSegment s : SegmentIterable.of(points, MatchableSegment::new)) {
            // Track and match best position on the path
            for (MemberOnPath m : membersOnPath) {
                m.match(s, calc_total_len);
            }

            // Accumulate total length
            calc_total_len += s.len;
        }
        final double total_len = calc_total_len;

        // Turn into TrackMemberState
        return membersOnPath.stream()
                .filter(MemberOnPath::isMatched)
                .map(m -> new TrackMemberState(m.member, connection, m.bestPathDistance / total_len));
    }

    private static double getTotalDistance(List<RailPath.Point> points) {
        double len_sum = 0.0;
        for (Segment s : SegmentIterable.of(points, Segment::new)) {
            len_sum += s.len;
        }
        return len_sum;
    }

    private static void applyPosition(MinecartMember<?> member, double x, double y, double z) {
        CommonEntity<?> entity = member.getEntity();
        entity.setPosition(x, y, z);
        entity.setPositionChanged(true);
    }

    // Keeps track on what segment and where a member is (best) located
    private static class MemberOnPath {
        public final MinecartMember<?> member;
        public final Vector position;
        public double bestDistanceSq = (0.2 * 0.2); // Minecart must be at most 0.2 block away from the path being moved
        public double bestPathDistance = Double.NaN;

        public MemberOnPath(MinecartMember<?> member) {
            this.member = member;
            this.position = member.getEntity().loc.vector();
        }

        public boolean isMatched() {
            return !Double.isNaN(bestPathDistance);
        }

        public void match(MatchableSegment segment, double pathDistance) {
            double theta = segment.calcTheta(position);
            if (theta < 0.0 || theta > 1.0) {
                return;
            }

            Vector posOnPath = segment.getPosition(theta);
            double distSq = posOnPath.distanceSquared(this.position);
            if (distSq < bestDistanceSq) {
                bestDistanceSq = distSq;
                bestPathDistance = pathDistance + theta * segment.len;
            }
        }
    }

    // Iterates a List of PathPoint as Segments
    private static class SegmentIterable<S> implements Iterable<S> {
        private final List<RailPath.Point> points;
        private final BiFunction<RailPath.Point, RailPath.Point, S> func;

        public static <S> SegmentIterable<S> of(List<RailPath.Point> points, BiFunction<RailPath.Point, RailPath.Point, S> func) {
            return new SegmentIterable<>(points, func);
        }

        private SegmentIterable(List<RailPath.Point> points, BiFunction<RailPath.Point, RailPath.Point, S> func) {
            this.points = points;
            this.func = func;
        }

        @Override
        public Iterator<S> iterator() {
            return new SegmentIterator<>(points.iterator(), func);
        }

        private static class SegmentIterator<S> implements Iterator<S> {
            private final Iterator<RailPath.Point> iter;
            private final BiFunction<RailPath.Point, RailPath.Point, S> func;
            private RailPath.Point prev;

            public SegmentIterator(Iterator<RailPath.Point> iter, BiFunction<RailPath.Point, RailPath.Point, S> func) {
                this.iter = iter;
                this.func = func;
                this.prev = iter.hasNext() ? iter.next() : null;
            }

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public S next() {
                RailPath.Point prev = this.prev;
                RailPath.Point next = iter.next();
                this.prev = next;
                return func.apply(prev, next);
            }
        }
    }

    // Includes mot_dt (as in RailPath.Segment) for theta and path distance calculations
    private static class MatchableSegment extends Segment {
        public final double mot_dt_x, mot_dt_y, mot_dt_z;

        public MatchableSegment(RailPath.Point prev, RailPath.Point curr) {
            super(prev, curr);
            double inv_len_sq = 1.0 / this.len_sq;
            this.mot_dt_x = this.dx * inv_len_sq;
            this.mot_dt_y = this.dy * inv_len_sq;
            this.mot_dt_z = this.dz * inv_len_sq;
        }

        public final double calcTheta(Vector pos) {
            return -((this.x - pos.getX()) * mot_dt_x + (this.y - pos.getY()) * mot_dt_y + (this.z - pos.getZ()) * mot_dt_z);
        }

        public Vector getPosition(double theta) {
            if (theta <= 0.0) {
                return new Vector(x, y, z);
            } else if (theta >= 1.0) {
                return new Vector(x + dx, y + dy, z + dz);
            } else {
                return new Vector(x + theta * dx, y + theta * dy, z + theta * dz);
            }
        }
    }

    // Simplified version of RailPath.Segment for point-on-path calculations
    private static class Segment {
        public final double x, y, z;
        public final double dx, dy, dz;
        public final double len_sq;
        public final double len;
        private Vector mot_dt;

        public Segment(RailPath.Point prev, RailPath.Point curr) {
            this.x = prev.x;
            this.y = prev.y;
            this.z = prev.z;
            this.dx = curr.x - prev.x;
            this.dy = curr.y - prev.y;
            this.dz = curr.z - prev.z;
            this.len_sq = (dx * dx + dy * dy + dz * dz);
            this.len = Math.sqrt(this.len_sq);
        }
    }

    private interface RailAnimationChangeTracker {
        /** Used when the rails do not have any sign actions on it that are informed of animation changes */
        RailAnimationChangeTracker INACTIVE = new RailAnimationChangeTracker() {
            @Override
            public void add(TrackAnimation animation) {
                //No-op
            }

            @Override
            public void fireStarted() {
                //No-op
            }

            @Override
            public void fireEnded() {
                //No-op
            }
        };

        /**
         * Notify of the initial or another animation on this same track that was started or finished.
         *
         * @param animation TrackAnimation
         */
        void add(TrackAnimation animation);

        /**
         * Fires off the animation-start event for all animations added
         */
        void fireStarted();

        /**
         * Fires off the animation-end event for all animations added
         */
        void fireEnded();
    }

    private static class RailAnimationChangeTrackerActive implements RailAnimationChangeTracker {
        public final List<RailLookup.TrackedSign> signs;
        public List<AnimationChangeList> changes = new ArrayList<>();

        public RailAnimationChangeTrackerActive(List<RailLookup.TrackedSign> signs) {
            this.signs = signs;
        }

        @Override
        public void add(TrackAnimation animation) {
            for (AnimationChangeList list : changes) {
                if (list.tryMerge(animation)) {
                    return;
                }
            }

            changes.add(new AnimationChangeList(animation));
        }

        @Override
        public void fireStarted() {
            for (RailLookup.TrackedSign sign : signs) {
                SignAction action = sign.getAction();
                if (action instanceof TrackAnimationListener) {
                    SignActionEvent event = sign.createEvent(SignActionType.NONE);
                    for (AnimationChangeList list : changes) {
                        ((TrackAnimationListener) action).onTrackAnimationBegin(
                                event,
                                list.name,
                                list.animationStates);
                    }
                }
            }
        }

        @Override
        public void fireEnded() {
            for (RailLookup.TrackedSign sign : signs) {
                SignAction action = sign.getAction();
                if (action instanceof TrackAnimationListener) {
                    SignActionEvent event = sign.createEvent(SignActionType.NONE);
                    for (AnimationChangeList list : changes) {
                        ((TrackAnimationListener) action).onTrackAnimationEnd(
                                event,
                                list.name,
                                list.animationStates);
                    }
                }
            }
        }
    }

    private static class AnimationChangeList {
        public final String name;
        public final List<TrackAnimation> animationStates = new ArrayList<>();

        public AnimationChangeList(TrackAnimation animation) {
            this.name = animation.name;
            this.animationStates.add(animation);
        }

        public boolean tryMerge(TrackAnimation animation) {
            if (this.name.equals(animation.name)) {
                this.animationStates.add(animation);
                return true;
            } else {
                return false;
            }
        }
    }
}
