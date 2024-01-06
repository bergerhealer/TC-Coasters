package com.bergerkiller.bukkit.coasters.tracks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.TCCoastersUtil;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditMode;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.particles.TrackParticle;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleArrow;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleLitBlock;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleSignText;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleState;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleText;
import com.bergerkiller.bukkit.coasters.signs.power.NamedPowerChannel;
import com.bergerkiller.bukkit.coasters.world.CoasterWorld;
import com.bergerkiller.bukkit.coasters.world.CoasterWorldComponent;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.controller.components.RailJunction;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;

/**
 * A single node of track of a rollercoaster. Stores the 3D position
 * and the 'up' vector.
 */
public class TrackNode implements TrackNodeReference, CoasterWorldComponent, Lockable {
    private TrackCoaster _coaster;
    private Vector _pos, _up, _up_visual, _dir;
    private IntVector3 _railBlock;
    // Signs tied to this node
    private TrackNodeSign[] _signs = TrackNodeSign.EMPTY_ARR;
    // Named target states of this track node, which can be used for animations
    private TrackNodeAnimationState[] _animationStates;
    //private TrackParticleItem _particle;
    private TrackParticleArrow _upParticleArrow;
    private List<TrackParticleText> _junctionParticles;
    private TrackParticleLitBlock _blockParticle;
    private TrackParticleSignText _signTextParticle;
    // Connections are automatically updated when connecting/disconnecting
    protected TrackConnection[] _connections;

    protected TrackNode(TrackCoaster group, Vector pos, Vector up) {
        this(group, TrackNodeState.create(pos, up, null));
    }

    protected TrackNode(TrackCoaster group, TrackNodeState state) {
        this._railBlock = state.railBlock;
        this._coaster = group;
        this._pos = state.position;
        this._connections = TrackConnection.EMPTY_ARR;
        this._animationStates = TrackNodeAnimationState.EMPTY_ARR;
        if (state.orientation.lengthSquared() < 1e-10) {
            this._up = new Vector(0.0, 0.0, 0.0);
        } else {
            this._up = state.orientation.clone().normalize();
        }
        this._up_visual = this._up.clone();
        this._dir = new Vector(0, 0, 1);

        /*
        this._particle = group.getParticles().addParticleItem(this._pos);
        this._particle.setStateSource(new TrackParticleState.Source() {
            @Override
            public TrackParticleState getState(Player viewer) {
                TrackEditState editState = getPlugin().getEditState(viewer);
                return (editState.getMode() == TrackEditState.Mode.POSITION && editState.isEditing(TrackNode.this)) ? 
                        TrackParticleState.SELECTED : TrackParticleState.DEFAULT;
            }
        });
        */

        this._upParticleArrow = getWorld().getParticles().addParticleArrow(this._pos, this._dir, this._up_visual);
        this._upParticleArrow.setStateSource(new TrackParticleState.Source() {
            @Override
            public TrackParticleState getState(PlayerEditState viewer) {
                return viewer.getMode() != PlayerEditMode.OBJECT && viewer.isEditing(TrackNode.this) ? 
                        TrackParticleState.SELECTED : TrackParticleState.DEFAULT;
            }
        });

        this._junctionParticles = Collections.emptyList();

        this._blockParticle = getWorld().getParticles().addParticleLitBlock(this.getRailBlock(true));
        this._blockParticle.setStateSource(new TrackParticleState.Source() {
            @Override
            public TrackParticleState getState(PlayerEditState viewer) {
                if (viewer.isMode(PlayerEditMode.RAILS)) {
                    return viewer.isEditing(TrackNode.this) ? 
                            TrackParticleState.SELECTED : TrackParticleState.DEFAULT;
                } else {
                    return TrackParticleState.HIDDEN;
                }
            }
        });

        this._signTextParticle = null;
        this.setSignsWithoutMarkChanged(LogicUtil.cloneAll(state.signs, TrackNodeSign::clone));
    }

    @Override
    public CoasterWorld getWorld() {
        try {
            return this._coaster.getWorld();
        } catch (NullPointerException ex) {
            throw new IllegalStateException("Node was removed and has no world");
        }
    }

    /**
     * Gets the Coaster this node is a part of
     * 
     * @return Track Coaster
     */
    public TrackCoaster getCoaster() {
        return this._coaster;
    }

    public TrackNodeState getState() {
        return TrackNodeState.create(this);
    }

    public void setState(TrackNodeState state) {
        this.setPosition(state.position);
        this.setOrientation(state.orientation);
        this.setRailBlock(state.railBlock);
        this.setSigns(LogicUtil.cloneAll(state.signs, TrackNodeSign::clone));
    }

    public void markChanged() {
        TrackCoaster coaster = this._coaster;
        if (coaster != null) {
            coaster.markChanged();
        }
    }

    public final void remove() {
        getCoaster().removeNode(this);
    }

    /**
     * Gets whether this TrackNode was removed and is no longer bound to any world
     * 
     * @return True if removed
     */
    public boolean isRemoved() {
        return this._coaster == null;
    }

    public void setPosition(Vector position) {
        Vector curr = this._pos;
        if (curr.getX() != position.getX() || curr.getY() != position.getY() || curr.getZ() != position.getZ()) {
            this._pos = position.clone();
            //this._particle.setPosition(this._pos);
            this._upParticleArrow.setPosition(this._pos);
            if (this._railBlock == null) {
                this._blockParticle.setBlock(this.getRailBlock(true));
            }
            if (this._signTextParticle != null) {
                this._signTextParticle.setPosition(this._pos);
            }
            this.scheduleRefresh();
            this.markChanged();
        }
    }

    /**
     * Gets the movement direction on this node
     * 
     * @return direction
     */
    public Vector getDirection() {
        return this._dir;
    }

    /**
     * Gets the exact direction along which trains move over this node towards
     * a given neighbouring node of this track node. This takes into account special
     * junction logic where the directions are 'hard', as well as makes
     * {@link #getDirection()} flip 180 as needed based on which connection it is.
     *
     * @param neighbour Node towards which to move
     * @return Direction moved from this node to the node specified
     */
    public Vector getDirectionTo(TrackNode neighbour) {
        // Straight paths assumed for junction nodes and nodes with only one connection
        // TODO: Technically there's a little bend there because of the next node's own direction
        if (this._connections.length != 2) {
            Vector v = neighbour.getPosition().clone().subtract(this.getPosition());
            double ls = v.lengthSquared();
            if (ls <= 1e-20) {
                return this.getDirection(); // Fallback eh.
            } else {
                return v.multiply(MathUtil.getNormalizationFactorLS(ls));
            }
        }

        // Use this node's getDirection(), flip as needed
        if (this._connections[1].isConnected(neighbour)) {
            return this.getDirection().clone().multiply(-1.0);
        } else {
            return this.getDirection();
        }
    }

    /**
     * Gets the exact direction along which trains move over this node away from
     * a given neighbouring node of this track node. This takes into account special
     * junction logic where the directions are 'hard', as well as makes
     * {@link #getDirection()} flip 180 as needed based on which connection it is.
     *
     * @param neighbour Node away from which to move
     * @return Direction moved from this node away from node specified
     */
    public Vector getDirectionFrom(TrackNode neighbour) {
        // Straight paths assumed for junction nodes and nodes with only one connection
        // TODO: Technically there's a little bend there because of the next node's own direction
        if (this._connections.length != 2) {
            Vector v = this.getPosition().clone().subtract(neighbour.getPosition());
            double ls = v.lengthSquared();
            if (ls <= 1e-20) {
                return this.getDirection(); // Fallback eh.
            } else {
                return v.multiply(MathUtil.getNormalizationFactorLS(ls));
            }
        }

        // Use this node's getDirection(), flip as needed
        if (this._connections[0].isConnected(neighbour)) {
            return this.getDirection().clone().multiply(-1.0);
        } else {
            return this.getDirection();
        }
    }

    /**
     * Gets the exact coordinates of this node
     * 
     * @return position
     */
    @Override
    public Vector getPosition() {
        return this._pos;
    }

    /**
     * Gets the block this node occupies
     * 
     * @return
     */
    public IntVector3 getPositionBlock() {
        int bx = this._pos.getBlockX();
        int by = this._pos.getBlockY();
        int bz = this._pos.getBlockZ();
        if (this._connections.length == 1) {
            // End-point means the rail block is towards the direction connection
            // Note: make use of the position of the node, not _dir (_dir could be outdated)
            Vector otherPos = this._connections[0].getOtherNode(this).getPosition();
            double dx = this._pos.getX() - bx;
            double dy = this._pos.getY() - by;
            double dz = this._pos.getZ() - bz;
            if (dx <= 1e-10 && this._pos.getX() > otherPos.getX()) {
                bx--;
            }
            if (dx >= (1.0-1e-10) && this._pos.getX() < otherPos.getX()) {
                bx++;
            }
            if (dy <= 1e-10 && this._pos.getY() > otherPos.getY()) {
                by--;
            }
            if (dy >= (1.0-1e-10) && this._pos.getY() < otherPos.getY()) {
                by++;
            }
            if (dz <= 1e-10 && this._pos.getZ() > otherPos.getZ()) {
                bz--;
            }
            if (dz >= (1.0-1e-10) && this._pos.getZ() < otherPos.getZ()) {
                bz++;
            }
        }
        return new IntVector3(bx, by, bz);
    }

    /**
     * Gets the Bukkit world Location of where minecarts should be spawned.
     * Includes the direction vector in the orientation
     * 
     * @param orientation vector
     * @return spawn location
     */
    public Location getSpawnLocation(Vector orientation) {
        Vector dir = this.getDirection().clone();
        if (dir.dot(orientation) < 0.0) {
            dir.multiply(-1.0);
        }
        Vector pos = this.getPosition();
        return new Location(this.getBukkitWorld(),
                pos.getX(), pos.getY(), pos.getZ(),
                MathUtil.getLookAtYaw(dir),
                MathUtil.getLookAtPitch(dir.getX(),dir.getY(),dir.getZ()));
    }

    public Vector getUpPosition() {
        return this._pos.clone().add(this._up.clone().multiply(0.4));
    }

    public void setOrientation(Vector up) {
        // Assign up vector, normalize it to length 1
        double up_n = MathUtil.getNormalizationFactor(up);
        if (!Double.isInfinite(up_n)) {
            up = up.clone().multiply(up_n);
            Vector up_curr = this._up;
            if (up_curr.getX() != up.getX() || up_curr.getY() != up.getY() || up_curr.getZ() != up.getZ()) {
                this._up = up;
                this.scheduleRefresh();
                this.markChanged();
            }
        }
        this.refreshOrientation();
    }

    private final void refreshOrientation() {
        // Calculate what kind of up vector is used 'visually'
        // This is on a 90-degree angle with the track itself (dir)
        this._up_visual = this._dir.clone().crossProduct(this._up).crossProduct(this._dir);
        double n = MathUtil.getNormalizationFactor(this._up_visual);
        if (Double.isInfinite(n)) {
            // Fallback for up-vectors in the same orientation as dir
            this._up_visual = this._dir.clone().crossProduct(new Vector(1,1,1)).crossProduct(this._dir).normalize();
        } else {
            this._up_visual.multiply(n);
        }
        this._upParticleArrow.setDirection(this._dir, this._up_visual);
    }

    /**
     * Pushes a junction connection to the end of the list of connections,
     * in essence disabling the junction
     * 
     * @param connection to push back
     */
    public void pushBackJunction(TrackConnection connection) {
        // Nothing to sort here
        if (this._connections.length <= 1 || this._connections[this._connections.length - 1] == connection) {
            return;
        }

        // Find the junction and push it to the back of the array
        for (int i = 0; i < this._connections.length - 1; i++) {
            if (this._connections[i] == connection) {
                System.arraycopy(this._connections, i+1, this._connections, i, this._connections.length-i-1);
                this._connections[this._connections.length - 1] = connection;
                this.scheduleRefresh();
                this.markChanged();
                return;
            }
        }
    }

    /**
     * Ensures a particular connection is switched and made active.
     * This method guarantees that the previously switched connection stays
     * switch when invoked a second time
     * 
     * @param connection to switch
     */
    public void switchJunction(TrackConnection connection) {
        // With 2 or less connections, the same 2 are always switched
        // This method does nothing in those cases
        if (this._connections.length <= 2) {
            return;
        }

        // Check not already switched, or switched 1 time ago
        if (this._connections[0] == connection) {
            return;
        }
        if (this._connections[1] == connection) {
            this._connections[1] = this._connections[0];
            this._connections[0] = connection;
            this.scheduleRefreshWithPriority();
            this.markChanged();
            return;
        }

        // Find the connection in the array and shift it to the start of the array
        for (int i = 2; i < this._connections.length; i++) {
            if (this._connections[i] == connection) {
                System.arraycopy(this._connections, 0, this._connections, 1, i);
                this._connections[0] = connection;
                this.scheduleRefreshWithPriority();
                this.markChanged();
                return;
            }
        }
    }

    public Vector getOrientation() {
        return this._up;
    }

    /**
     * Called when the shape of the track node has been changed.
     * This can happen as a result of position changes of the node itself,
     * or one of its connected neighbours.
     */
    public void onShapeUpdated() {
        // Refresh dir
        this._dir = new Vector();
        List<TrackConnection> connections = this.getSortedConnections();
        for (int i = 0; i < connections.size(); i++) {
            TrackConnection conn = connections.get(i);
            TrackNode neighbour = conn.getOtherNode(this);

            Vector v = neighbour.getPosition().clone().subtract(this.getPosition());
            double lsq = v.lengthSquared();
            if (lsq > 1e-20) {
                // Divide by length to normalize the vector
                // Divide by length again to make short sections have a bigger impact on directionality
                // This can be combined as dividing by length squared
                v.multiply(1.0 / lsq);

                if (connections.size() > 2) {
                    // Best fit applies
                    if (this._dir.dot(v) > 0.0) {
                        this._dir.add(v);
                    } else {
                        this._dir.subtract(v);
                    }
                } else {
                    // Force direction from node to node at all times
                    // Add/subtract alternate based on index
                    if (i == 0) {
                        this._dir.add(v);
                    } else {
                        this._dir.subtract(v);
                    }
                }
            }
        }

        // Normalize
        double n = MathUtil.getNormalizationFactor(this._dir);
        if (Double.isInfinite(n)) {
            this._dir = new Vector(0, 0, 1);
        } else {
            this._dir.multiply(n);
        }

        // Recalculate the up-vector to ortho to dir
        this.refreshOrientation();

        // Refresh connections connected to this node, for they have changed
        for (int i = 0; i < connections.size(); i++) {
            TrackConnection conn = connections.get(i);

            // Recalculate the smoothening algorithm for all connections
            TrackConnection.NodeEndPoint end;
            if (this == conn._endA.node) {
                end = conn._endA;
            } else {
                end = conn._endB;
            }
            if (connections.size() > 2) {
                end.initAuto();
            } else if (i == 0) {
                end.initNormal();
            } else {
                end.initInverted();
            }
        }

        // If more than 2 connections are added to this node, display junction labels
        if (connections.size() > 2) {
            // Initialize or shrink list of particles as required
            if (this._junctionParticles.isEmpty()) {
                this._junctionParticles = new ArrayList<TrackParticleText>(connections.size());
            } else {
                while (this._junctionParticles.size() > connections.size()) {
                    this._junctionParticles.remove(this._junctionParticles.size() - 1).remove();
                }
            }

            // Refresh the particles
            for (int i = 0; i < connections.size(); i++) {
                TrackConnection conn = connections.get(i);
                boolean active = (conn == this._connections[0] || conn == this._connections[1]);
                Vector pos = conn.getNearEndPosition(this);
                String text = TrackParticleText.getOrdinalText(i, Integer.toString(i+1));
                if (active) {
                    text += "#";
                }
                if (i >= this._junctionParticles.size()) {
                    this._junctionParticles.add(getWorld().getParticles().addParticleText(pos, text));
                } else {
                    TrackParticleText particle = this._junctionParticles.get(i);
                    particle.setPosition(pos);
                    particle.setText(text);
                }
            }

        } else {
            for (TrackParticle particle : this._junctionParticles) {
                particle.remove();
            }
            this._junctionParticles = Collections.emptyList();
        }

        // Block location can change as a result of all of this
        // This happens when at the block border
        if (this._railBlock == null) {
            this._blockParticle.setBlock(this.getRailBlock(true));
        }
    }

    public void onStateUpdated(Player viewer) {
        //this._particle.onStateUpdated(viewer);
        this._blockParticle.onStateUpdated(viewer);
        this._upParticleArrow.onStateUpdated(viewer);
        for (TrackParticle juncParticle : this._junctionParticles) {
            juncParticle.onStateUpdated(viewer);
        }
    }

    /**
     * Gets whether this node has no connections to other nodes. Not right now, and
     * not in saved animation states.
     * 
     * @return True if this is an unconnected node
     */
    public final boolean isUnconnectedNode() {
        if (this._connections.length > 0) {
            return false;
        }
        for (TrackNodeAnimationState anim : this._animationStates) {
            if (anim.connections.length > 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gets the zero-distance neighbour node that is connected to this node,
     * if one exists. This generally only occurs when a node has been 'straightened'
     * and has two connections with other nodes.
     *
     * @return zero-distance neighbour, null if no such neighbour exists
     */
    public TrackNode getZeroDistanceNeighbour() {
        for (TrackConnection connection : this._connections) {
            if (connection.isZeroLength()) {
                return connection.getOtherNode(this);
            }
        }
        return null;
    }

    /**
     * Returns a List of unique TrackNodes that occupy the position of this node. If this
     * node has a {@link #getZeroDistanceNeighbour() zero distance neighbour} it returns
     * a list containing this node and that neighbour. Otherwise, it returns a singleton
     * list of just this node.
     *
     * @return List of this node, or this node and a zero-distance neighbour
     * @see #getZeroDistanceNeighbour()
     */
    public List<TrackNode> getNodesAtPosition() {
        TrackNode zero = getZeroDistanceNeighbour();
        return zero == null ? Collections.singletonList(this) : Arrays.asList(this, zero);
    }

    /**
     * Same as {@link #getZeroDistanceNeighbour()} but only returns the zero-distance
     * neighbour if it has no connections other than with this node. Instead of null
     * it returns this node if those conditions aren't met.
     *
     * @return zero-distance neighbour, or this node if no such neighbour exists or
     *         the neighbour has connections to nodes other than this node.
     */
    public TrackNode selectZeroDistanceOrphan() {
        for (TrackConnection connection : this._connections) {
            if (connection.isZeroLength()) {
                TrackNode zeroDistNeigh = connection.getOtherNode(this);
                if (zeroDistNeigh._connections.length == 1) {
                    return zeroDistNeigh;
                }
            }
        }
        return this;
    }

    /**
     * Gets whether this node is a 'zero distance' orphan. This is a node at the same
     * position as another node, but not connected to anything else. Orphans like that
     * must be purged.
     *
     * @return True if this node is a zero-distance orphan
     */
    public boolean isZeroDistanceOrphan() {
        TrackConnection[] conn = this._connections;
        return conn.length == 1 && conn[0].isZeroLength();
    }

    public final List<RailJunction> getJunctions() {
        List<TrackConnection> connections = this.getSortedConnections();
        if (connections.isEmpty()) {
            return Collections.emptyList();
        }
        RailJunction[] junctions = new RailJunction[connections.size()];
        for (int i = 0; i < connections.size(); i++) {
            String name = Integer.toString(i + 1);
            RailPath.Position position = connections.get(i).getPathPosition(this, 0.5);
            junctions[i] = new RailJunction(name, position);
        }
        return Arrays.asList(junctions);
    }

    public final List<TrackConnection> getConnections() {
        return Arrays.asList(this._connections);
    }

    public final List<TrackConnection> getSortedConnections() {
        // Sorting only required when count > 2
        if (this._connections.length <= 2) {
            return this.getConnections();
        }

        // Create a sorted list of connections, and pre-compute the quaternion facing angles of all connections
        ArrayList<TrackConnection> tmp = new ArrayList<TrackConnection>(this.getConnections());
        Vector[] tmp_vectors = new Vector[tmp.size()];
        for (int i = 0; i < tmp_vectors.length; i++) {
            Vector tmp_pos = tmp.get(i).getOtherNode(this).getPosition();
            tmp_vectors[i] = tmp_pos.clone().subtract(this.getPosition()).normalize();
        }

        // Use the connection with largest difference with the other connections as the base
        int baseIndex = 0;
        Vector baseVector = tmp_vectors[0];
        double max_angle_diff = 0.0;
        for (int i = 0; i < tmp_vectors.length; i++) {
            Vector base = tmp_vectors[i];
            double min_angle = 360.0;
            for (int j = 0; j < tmp_vectors.length; j++) {
                if (i != j) {
                    double a = MathUtil.getAngleDifference(base, tmp_vectors[j]);
                    if (a < min_angle) {
                        min_angle = a;
                    }
                }
            }

            int comp = 0;
            if (Math.abs(min_angle - max_angle_diff) <= 1e-10) {
                // Very similar, select on direction vector instead
                comp = Double.compare(base.getX(), baseVector.getX());
                if (comp == 0) {
                    comp = Double.compare(base.getZ(), baseVector.getZ());
                    if (comp == 0) {
                        comp = Double.compare(base.getY(), baseVector.getY());
                    }
                }
            } else {
                // Select based on angle
                comp = Double.compare(min_angle, max_angle_diff);
            }
            if (comp > 0) {
                max_angle_diff = min_angle;
                baseIndex = i;
                baseVector = base;
            }
        }

        // Perform the sorting logic based on yaw
        TrackConnection base = tmp.remove(baseIndex);
        Vector bp = base.getOtherNode(this).getPosition();
        final Vector p = this.getPosition();
        final float base_yaw = MathUtil.getLookAtYaw(bp.getX()-p.getX(), bp.getZ()-p.getZ());
        Collections.sort(tmp, new Comparator<TrackConnection>() {
            @Override
            public int compare(TrackConnection o1, TrackConnection o2) {
                Vector p1 = o1.getOtherNode(TrackNode.this).getPosition();
                Vector p2 = o2.getOtherNode(TrackNode.this).getPosition();
                float y1 = MathUtil.getLookAtYaw(p1.getX()-p.getX(), p1.getZ()-p.getZ()) - base_yaw;
                float y2 = MathUtil.getLookAtYaw(p2.getX()-p.getX(), p2.getZ()-p.getZ()) - base_yaw;
                while (y1 < 0.0f) y1 += 360.0f;
                while (y2 < 0.0f) y2 += 360.0f;
                return Float.compare(y1, y2);
            }
        });
        tmp.add(0, base);
        return tmp;
    }

    /**
     * Checks the connections of this node for a connection with another node.
     * Only node position must match.
     * 
     * @param nodeReference The node (reference) of the node to find a connection with
     * @return Track connection found, null if no such connection exists
     */
    public final TrackConnection findConnectionWithReference(TrackNodeReference nodeReference) {
        for (TrackConnection connection : this._connections) {
            if (connection.getOtherNode(this).isReference(nodeReference)) {
                return connection;
            }
        }
        return null;
    }

    /**
     * Checks the connections of this node for a connection with another node.
     * Node must match exactly.
     *
     * @param node The node of the node to find a connection with
     * @return Track connection found, null if no such connection exists
     */
    public final TrackConnection findConnectionWithNode(TrackNode node) {
        for (TrackConnection connection : this._connections) {
            if (connection.getOtherNode(this) == node) {
                return connection;
            }
        }
        return null;
    }

    public List<TrackNode> getNeighbours() {
        TrackNode[] result = new TrackNode[this._connections.length];
        for (int i = 0; i < this._connections.length; i++) {
            result[i] = this._connections[i].getOtherNode(this);
        }
        return Arrays.asList(result);
    }

    /**
     * Animates this node's position towards a target state, by name.
     * 
     * @param name      The name of the state to animate towards
     * @param duration  The duration in seconds the animation should take, 0 for instant
     * @return True if the animation state by this name exists, and the animation is now playing
     */
    public boolean playAnimation(String name, double duration) {
        for (TrackNodeAnimationState animState : this._animationStates) {
            if (animState.name.equals(name)) {
                if (this.doAnimationStatesChangeConnections()) {
                    getWorld().getAnimations().animate(this, animState.state, animState.connections, duration);
                } else {
                    getWorld().getAnimations().animate(this, animState.state, null, duration);
                }
                return true;
            }
        }
        return false;
    }

    public boolean removeAnimationState(String name) {
        final TCCoasters plugin = this.getPlugin();
        for (int i = 0; i < this._animationStates.length; i++) {
            TrackNodeAnimationState state = this._animationStates[i];
            if (state.name.equals(name)) {
                state.destroyParticles();
                this._animationStates = LogicUtil.removeArrayElement(this._animationStates, i);
                for (int j = i; j < this._animationStates.length; j++) {
                    this._animationStates[j].updateIndex(j);
                }
                handleSignOwnerUpdates(plugin, state.state.signs, TrackNodeSign.EMPTY_ARR, true);
                plugin.forAllEditStates(editState -> editState.notifyNodeAnimationRemoved(TrackNode.this, name));
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether this node stores an animation state by the given name
     *
     * @param name Name to check
     * @return True if an animation state is stored by this name, False if not
     */
    public boolean hasAnimationState(String name) {
        for (int i = 0; i < this._animationStates.length; i++) {
            if (this._animationStates[i].name.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Renames a stored animation state to a new name. Before calling this, make sure
     * the new name isn't already used!
     *
     * @param name Name of the state to rename
     * @param newName New name for the state
     * @return True if an animation state by this name was found, and thus, renamed
     */
    public boolean renameAnimationState(String name, String newName) {
        for (int i = 0; i < this._animationStates.length; i++) {
            if (this._animationStates[i].name.equals(name)) {
                this._animationStates[i].destroyParticles();
                this._animationStates[i] = this._animationStates[i].rename(newName);
                this._animationStates[i].spawnParticles(this, i);

                // Refresh any player edit states so they become aware of this rename
                this.getPlugin().forAllEditStates(editState -> {
                    editState.notifyNodeAnimationRemoved(this, name);
                    editState.notifyNodeAnimationAdded(this, newName);
                });

                return true;
            }
        }
        return false;
    }

    /**
     * Saves the current position, orientation, rail block and connections as an animation state.
     * Any previous animation state of the same name is overwritten.
     * 
     * @param name
     */
    public void saveAnimationState(String name) {
        // Collect the current connections that are made
        TrackConnectionState[] connectedNodes = new TrackConnectionState[this._connections.length];
        for (int i = 0; i < this._connections.length; i++) {
            connectedNodes[i] = TrackConnectionState.create(this._connections[i]);
        }

        setAnimationState(name, this.getState(), connectedNodes);
    }

    /**
     * Adds or updates an animation state by name.
     * 
     * @param name The name of the animation
     * @param state The position, orientation and rail block information
     * @param connections The connections made while this animation is active
     */
    public void setAnimationState(String name, TrackNodeState state, TrackConnectionState[] connections) {
        this.updateAnimationState(TrackNodeAnimationState.create(name, state, connections));
    }

    /**
     * Adds or updates an animation state. If an animation state by the same name already exists,
     * it is updated to this state. Otherwise it is added.
     *
     * @param state The animation state to update
     */
    public void updateAnimationState(TrackNodeAnimationState state) {
        updateAnimationState(state, null);
    }

    /**
     * Adds or updates an animation state. If an animation state by the same name already exists,
     * it is updated to this state. Otherwise it is added.
     * 
     * @param state The animation state to update
     * @param filter A filter predicate that any animation state changes are sent to before they are
     *               applied. Can return false to cancel the operation.
     */
    public void updateAnimationState(TrackNodeAnimationState state, UpdateAnimationStatePredicate filter) {
        // Important for saving!
        this.markChanged();

        // Overwrite existing
        final TCCoasters plugin = getPlugin();
        for (int i = 0; i < this._animationStates.length; i++) {
            TrackNodeAnimationState old_state = this._animationStates[i];
            if (old_state == state) {
                return;
            } else if (old_state.name.equals(state.name)) {
                if (filter != null && filter.canUpdateState(this, old_state, state)) {
                    return;
                }

                old_state.destroyParticles();
                this._animationStates[i] = state;
                state.spawnParticles(this, i);
                handleSignOwnerUpdates(plugin, old_state.state.signs, state.state.signs, true);
                return;
            }
        }

        // Check before adding
        if (filter != null && !filter.canUpdateState(this, null, state)) {
            return;
        }

        // Add new
        this._animationStates = LogicUtil.appendArrayElement(this._animationStates, state);
        state.spawnParticles(this, this._animationStates.length - 1);
        handleSignOwnerUpdates(plugin, TrackNodeSign.EMPTY_ARR, state.state.signs, true);

        // Refresh any player edit states so they become aware of this animation
        plugin.forAllEditStates(editState -> editState.notifyNodeAnimationAdded(this, state.name));
    }

    /**
     * Updates all the animation states of this track node smartly by making use of a manipulator function to alter them.
     * When name is null, all animation states are updated, otherwise only the one matching the name is.
     *
     * @param name The name of the animation state to update, null to update all of them
     * @param manipulator Manipulator function to alter the original animation states
     */
    public void updateAnimationStates(String name,
                                      Function<TrackNodeAnimationState, TrackNodeAnimationState> manipulator
    ) {
        updateAnimationStates(name, manipulator, null);
    }

    /**
     * Updates all the animation states of this track node smartly by making use of a manipulator function to alter them.
     * When name is null, all animation states are updated, otherwise only the one matching the name is.
     * A filter can be specified to filter what state changes are allowed through. Permission handling can be done
     * this way.
     * 
     * @param name The name of the animation state to update, null to update all of them
     * @param manipulator Manipulator function to alter the original animation states
     * @param filter An animation state change filter predicate. If it returns false, the change is not made.
     */
    public void updateAnimationStates(String name,
                                      Function<TrackNodeAnimationState, TrackNodeAnimationState> manipulator,
                                      UpdateAnimationStatePredicate filter
    ) {
        final TCCoasters plugin = getPlugin();
        for (int i = 0; i < this._animationStates.length; i++) {
            TrackNodeAnimationState old_state = this._animationStates[i];
            if (name == null || name.equals(old_state.name)) {
                TrackNodeAnimationState new_state = manipulator.apply(old_state);
                if (new_state != old_state) {
                    if (filter != null && !filter.canUpdateState(this, old_state, new_state)) {
                        continue;
                    }

                    old_state.destroyParticles();
                    this._animationStates[i] = new_state;
                    new_state.spawnParticles(this, i);
                    handleSignOwnerUpdates(plugin, old_state.state.signs, new_state.state.signs, true);
                    this.markChanged();
                }
            }
        }
    }

    /**
     * Adds a sign to an existing node animation state. The filter specifies permission handling to perform
     * before the node is added, and can be null to ignore all that.
     *
     * @param name Animation state name
     * @param sign Sign state to add
     * @param filter Filter predicate to run before adding the sign. Pass null to skip.
     */
    public void addAnimationStateSign(String name, TrackNodeSign sign, AddSignPredicate filter) {
        this.updateAnimationStates(name, anim_state -> {
            if (filter != null) {
                // Go to verify we can actually add it first. For this the sign must be bound to this sign.
                TrackNode oldNode = sign.getNode();
                boolean oldAddedAsAnimation = sign.isAddedAsAnimation();
                try {
                    // Must be addedAsAnimation=false otherwise errors occur (=not a sign)
                    sign.updateOwner(this.getPlugin(), this, false);

                    // Check
                    if (!filter.canAddSign(this, sign)) {
                        return anim_state; // Unchanged
                    }
                } finally {
                    // Restore
                    sign.updateOwner(this.getPlugin(), oldNode, oldAddedAsAnimation);
                }
            }

            return anim_state.updateSigns(LogicUtil.appendArrayElement(anim_state.state.signs, sign));
        });
    }

    /**
     * Adds a connection with another node to an animation state, or to all of them if name is null.
     * If the connection already existed in an animation state, its information such as track objects
     * are updated.
     *
     * @param name The name of the animation state to add the connection to, null to add to all of them
     * @param connection The connection to add
     */
    public void addAnimationStateConnection(String name, TrackConnectionState connection) {
        addAnimationStateConnection(name, connection, null);
    }

    /**
     * Adds a connection with another node to an animation state, or to all of them if name is null.
     * If the connection already existed in an animation state, its information such as track objects
     * are updated.
     * 
     * @param name The name of the animation state to add the connection to, null to add to all of them
     * @param connection The connection to add
     * @param filter A filter to call before making an animation state change final. If it returns false, the
     *               state is not updated
     */
    public void addAnimationStateConnection(String name, TrackConnectionState connection, UpdateAnimationStatePredicate filter) {
        if (this.hasAnimationStates() && connection.isConnected(this)) {
            final TrackConnectionState referenced_connection = connection.reference(this.getWorld().getTracks());
            this.updateAnimationStates(name, state -> state.updateConnection(referenced_connection), filter);
        }
    }

    /**
     * Removes a connection with another node from an animation state, or from all of them if name is null
     * 
     * @param name The name of the animation state to remove the connection from, null to remove from all of them
     * @param nodeReference The reference to the node to remove (position or TrackNode)
     */
    public void removeAnimationStateConnection(String name, TrackNodeReference nodeReference) {
        this.updateAnimationStates(name, state -> state.removeConnectionWith(nodeReference));
    }

    /**
     * Removes all connections with all other nodes from all animation states
     */
    public void clearAnimationStateConnections() {
        this.updateAnimationStates(null, TrackNodeAnimationState::clearConnections);
    }

    /**
     * Gets whether any of the configured animation states alter the connections with this node.
     * 
     * @return True if connections are made and broken when animations are played
     */
    public boolean doAnimationStatesChangeConnections() {
        if (this._connections.length == 2) {
            // When 2 connections exist, we are more lenient with the order of those connections
            TrackNode node_a = this._connections[0].getOtherNode(this);
            TrackNode node_b = this._connections[1].getOtherNode(this);
            for (TrackNodeAnimationState state : this._animationStates) {
                if (state.connections.length != 2) {
                    return true; // Different than normal number of connections
                }
                if (state.connections[0].hasObjects() || state.connections[1].hasObjects()) {
                    return true; // Objects are defined, we must save it
                }
                TrackNodeReference conn_node_a = state.connections[0].getOtherNode(this);
                TrackNodeReference conn_node_b = state.connections[1].getOtherNode(this);
                if (!conn_node_a.isReference(node_a) && !conn_node_a.isReference(node_b)) {
                    return true; // Connection with first node either doesn't exist or is different
                }
                if (!conn_node_b.isReference(node_a) && !conn_node_b.isReference(node_b)) {
                    return true; // Connection with second node either doesn't exist or is different
                }
            }
        } else {
            // When 0, 1, 3 or more connections exist, a change in order also means animations change connections
            for (TrackNodeAnimationState state : this._animationStates) {
                if (state.connections.length != this._connections.length) {
                    return true; // Different than normal number of connections
                }
                for (int i = 0; i < this._connections.length; i++) {
                    if (state.connections[i].hasObjects()) {
                        return true; // Objects are defined, we must save it
                    }
                    if (!state.connections[i].getOtherNode(this).isReference(this._connections[i].getOtherNode(this))) {
                        return true; // Connection either doesn't exist or is different, or order differs
                    }
                }
            }
        }
        return false;
    }

    public boolean hasAnimationStates() {
        return this._animationStates.length > 0;
    }

    /**
     * Gets all animation states added to this node as an immutable list.
     * It can be safely iterated while making changes to animation states of this node.
     * 
     * @return immutable list of animation states
     */
    public List<TrackNodeAnimationState> getAnimationStates() {
        return LogicUtil.asImmutableList(this._animationStates);
    }

    public TrackNodeAnimationState findAnimationState(String name) {
        if (name != null) {
            for (TrackNodeAnimationState state : this._animationStates) {
                if (state.name.equals(name)) {
                    return state;
                }
            }
        }
        return null;
    }

    public double getJunctionViewDistance(Matrix4x4 cameraTransform, TrackConnection connection) {
        return getViewDistance(cameraTransform, connection.getNearEndPosition(this));
    }

    public double getViewDistance(Matrix4x4 cameraTransform) {
        return Math.min(getViewDistance(cameraTransform, this.getPosition()),
                        getViewDistance(cameraTransform, this.getUpPosition()));
    }

    public double getRailBlockViewDistance(Matrix4x4 cameraTransform) {
        if (this._railBlock != null) {
            return getViewDistance(cameraTransform, new Vector(
                    this._railBlock.midX(),
                    this._railBlock.midY(),
                    this._railBlock.midZ()));
        }
        return getViewDistance(cameraTransform, this.getPosition());
    }

    private static double getViewDistance(Matrix4x4 cameraTransform, Vector pos) {
        pos = pos.clone();
        cameraTransform.transformPoint(pos);

        // Behind the player
        if (pos.getZ() <= 1e-6) {
            return Double.MAX_VALUE;
        }

        // Calculate limit based on depth (z) and filter based on it
        double lim = Math.max(1.0, pos.getZ() * Math.pow(4.0, -pos.getZ()));
        if (Math.abs(pos.getX()) > lim || Math.abs(pos.getY()) > lim) {
            return Double.MAX_VALUE;
        }

        // Calculate 2d distance
        return Math.sqrt(TCCoastersUtil.distanceSquaredXY(pos)) / lim;
    }

    protected void onRemoved() {
        final TCCoasters plugin = getPlugin();

        destroyParticles();
        handleSignOwnerUpdates(plugin, this._signs, TrackNodeSign.EMPTY_ARR, false);
        for (TrackNodeAnimationState animState : this._animationStates) {
            handleSignOwnerUpdates(plugin, animState.state.signs, TrackNodeSign.EMPTY_ARR, true);
        }
        _coaster = null; // mark removed by breaking reference to coaster
    }

    public void destroyParticles() {
        this._upParticleArrow.remove();
        this._blockParticle.remove();
        for (TrackParticle particle : this._junctionParticles) {
            particle.remove();
        }
        this._junctionParticles = Collections.emptyList();
        for (TrackNodeAnimationState animState : this._animationStates) {
            animState.destroyParticles();
        }
        if (this._signTextParticle != null) {
            this._signTextParticle.remove();
            this._signTextParticle = null;
        }
    }

    /**
     * Gets the rail block, where signs are triggered.
     * When createDefault is true, the current node position is turned into a rails block
     * when no rails block is set. Otherwise, null is returned in that case.
     * 
     * @return rails block
     */
    public IntVector3 getRailBlock(boolean createDefault) {
        if (createDefault && this._railBlock == null) {
            return this.getPositionBlock();
        } else {
            return this._railBlock;
        }
    }

    /**
     * Sets the rail block, where signs are triggered
     * 
     * @param railBlock New rail block
     */
    public void setRailBlock(IntVector3 railBlock) {
        if (!LogicUtil.bothNullOrEqual(this._railBlock, railBlock)) {
            this._railBlock = railBlock;
            this._blockParticle.setBlock(this.getRailBlock(true));
            this.markChanged();
            this.scheduleRefresh();
        }
    }

    /**
     * Gets an array of all the fake signs tied to this track node
     *
     * @return signs
     */
    public TrackNodeSign[] getSigns() {
        return this._signs;
    }

    /**
     * Sets a new array of fake signs tied to this track node. Properly cleans up
     * any old state.
     *
     * @param new_signs
     */
    public void setSigns(TrackNodeSign[] new_signs) {
        setSignsWithoutMarkChanged(new_signs);
        markChanged();
    }

    private void setSignsWithoutMarkChanged(TrackNodeSign[] new_signs) {
        TrackNodeSign[] prev_signs = this._signs;
        this._signs = new_signs;
        handleSignOwnerUpdates(getPlugin(), prev_signs, new_signs, false);
        updateSignParticle();
    }

    /**
     * Adds a single fake sign
     *
     * @param sign
     */
    public void addSign(TrackNodeSign sign) {
        addSign(sign, null);
    }

    /**
     * Adds a single fake sign
     *
     * @param sign
     * @param filter Optional predicate to check whether adding the sign is allowed.
     *               Can be null to ignore.
     */
    public void addSign(TrackNodeSign sign, AddSignPredicate filter) {
        // Remember old state to restore if adding is not allowed
        TrackNodeSign[] oldSigns = this._signs;
        TrackNode oldNode = sign.getNode();
        boolean oldAsAnimation = sign.isAddedAsAnimation();

        this._signs = LogicUtil.appendArrayElement(this._signs, sign);
        sign.updateOwner(this.getPlugin(), this, false);

        if (filter != null && !filter.canAddSign(this, sign)) {
            this._signs = oldSigns;
            sign.updateOwner(this.getPlugin(), oldNode, oldAsAnimation);
            return;
        }

        updateSignParticle();
        markChanged();
    }

    private void handleSignOwnerUpdates(TCCoasters plugin, TrackNodeSign[] prev_signs, TrackNodeSign[] new_signs, boolean isAnimationState) {
        if (prev_signs.length == 0) {
            // Adding stuff only
            for (TrackNodeSign sign : new_signs) {
                sign.updateOwner(plugin, this, isAnimationState);
            }
        } else if (new_signs.length == 0) {
            // Removing stuff only
            for (TrackNodeSign sign : prev_signs) {
                sign.updateOwner(plugin, null, false);
            }
        } else {
            List<TrackNodeSign> prev_signs_list = Arrays.asList(prev_signs);
            List<TrackNodeSign> new_signs_list = Arrays.asList(new_signs);

            // Add the new signs first. This makes sure named state common to old and
            // new signs is preserved.
            for (TrackNodeSign sign : new_signs) {
                if (!prev_signs_list.contains(sign)) {
                    sign.updateOwner(plugin, this, isAnimationState);
                }
            }

            // Remove signs that no longer exist
            for (TrackNodeSign sign : prev_signs_list) {
                if (!new_signs_list.contains(sign)) {
                    sign.updateOwner(plugin, null, false);
                }
            }
        }
    }

    void updateSignParticle() {
        TrackNodeSign[] signs = this._signs;
        if (signs.length == 0) {
            if (this._signTextParticle != null) {
                this._signTextParticle.remove();
                this._signTextParticle = null;
            }
        } else {
            String[][] lines = new String[signs.length][];
            for (int s = 0; s < signs.length; s++) {
                // Remove lines from the end which are all empty (saves space)
                TrackNodeSign sign = signs[s];
                String[] sign_lines = sign.getLines();
                int line_count = sign_lines.length;
                while (line_count > 0 && sign_lines[line_count-1].isEmpty()) {
                    line_count--;
                }
                if (line_count != sign_lines.length) {
                    sign_lines = Arrays.copyOf(sign_lines, line_count);
                }

                // Add redstone power input channels, if any
                {
                    NamedPowerChannel[] channels = sign.getInputPowerChannels();
                    if (channels.length > 0) {
                        int len = sign_lines.length;
                        sign_lines = Arrays.copyOf(sign_lines, len + channels.length);
                        for (int i = 0; i < channels.length; i++) {
                            sign_lines[len + i] = channels[i].getInputTooltipText();
                        }
                    }
                }

                // Add redstone power output channel, if any
                {
                    NamedPowerChannel[] channels = sign.getOutputPowerChannels();
                    if (channels.length > 0) {
                        int len = sign_lines.length;
                        sign_lines = Arrays.copyOf(sign_lines, len + channels.length);
                        for (int i = 0; i < channels.length; i++) {
                            sign_lines[len + i] = channels[i].getOutputTooltipText();
                        }
                    }
                }

                lines[s] = sign_lines;
            }
            if (this._signTextParticle == null) {
                this._signTextParticle = this.getWorld().getParticles().addParticleSignText(this.getPosition(), lines);
            } else {
                this._signTextParticle.setSignLines(lines);
            }
        }
    }

    /**
     * If this track node contains signs with output power states, checks that the given
     * sender has permission to change those power states. Checks node itself and all
     * animations.
     *
     * @param sender
     * @throws ChangeCancelledException If sender lacks permission
     */
    public void checkPowerPermissions(CommandSender sender) throws ChangeCancelledException {
        for (TrackNodeSign sign : this._signs) {
            sign.checkPowerPermissions(sender);
        }
        for (TrackNodeAnimationState animState : this._animationStates) {
            for (TrackNodeSign sign : animState.state.signs) {
                sign.checkPowerPermissions(sender);
            }
        }
    }

    /**
     * Builds a rail path for this track node, covering half of the connections connecting to this node.
     * The first two connections, if available, are used for the path.
     * 
     * @return rail path
     */
    public RailPath buildPath() {
        if (this._connections.length == 0) {
            return buildPath(null, null);
        } else if (this._connections.length == 1) {
            return buildPath(this._connections[0], null);
        } else {
            return buildPath(this._connections[0], this._connections[1]);
        }
    }

    /**
     * Builds a rail path for this track node, covering half of the connections connecting to this node.
     * 
     * @param connection_a first connection to include in the path, null to ignore
     * @param connection_b second connection to include in the path, null to ignore
     * @return rail path
     */
    public RailPath buildPath(TrackConnection connection_a, TrackConnection connection_b) {
        if (connection_a == null && connection_b == null) {
            return RailPath.EMPTY;
        }

        IntVector3 railsPos = getRailBlock(true);
        List<RailPath.Point> points = new ArrayList<RailPath.Point>();

        if (connection_a != null) {
            if (connection_a.getNodeA() == this) {
                connection_a.buildPath(points, railsPos, getPlugin().getSmoothness(), 0.5, 0.0);
            } else {
                connection_a.buildPath(points, railsPos, getPlugin().getSmoothness(), 0.5, 1.0);
            }
        }
        if (connection_b != null) {
            // Remove last point from previous half added, as it's the same
            // as the first point of this half
            // Do keep the one point of a zero-length connection or it won't reset rotation.
            // Exception is if both connections are zero-length. In that case we only want one point.
            if (connection_a != null && (!connection_a.isZeroLength() || connection_b.isZeroLength())) {
                points.remove(points.size() - 1);
            }

            if (connection_b.getNodeA() == this) {
                connection_b.buildPath(points, railsPos, getPlugin().getSmoothness(), 0.0, 0.5);
            } else {
                connection_b.buildPath(points, railsPos, getPlugin().getSmoothness(), 1.0, 0.5);
            }
        }

        // This happens when this node has two zero-length connections,
        // or a single connection with zero length. Properly resolve that.
        if (points.size() <= 1) {
            return RailPath.EMPTY;
        }

        return RailPath.create(points.toArray(new RailPath.Point[points.size()]));
    }

    private void scheduleRefresh() {
        getWorld().getTracks().scheduleNodeRefresh(this);
    }

    private void scheduleRefreshWithPriority() {
        getWorld().getTracks().scheduleNodeRefreshWithPriority(this);
    }

    // CoasterWorldAccess

    @Override
    public boolean isLocked() {
        return this._coaster.isLocked();
    }

    // TrackNodeReference

    /**
     * Searches for this node. If this node is already on a world, this
     * same node is returned. Otherwise, the node is looked up in the world
     * at the position of this node.
     * 
     * @param world The world to look the node up on
     * @param excludedNode If multiple track nodes exist at a position, makes sure to
     *                     exclude this node. Ignored if null. If the excluded node
     *                     is equal to this node, looks for another node at the same
     *                     position.
     */
    @Override
    public TrackNode findOnWorld(TrackWorld world, TrackNode excludedNode) {
        return (this._coaster != null && this != excludedNode) ? this : world.findNodeExact(this._pos, excludedNode);
    }

    @Override
    public boolean isExistingNode() {
        return !isRemoved();
    }

    @Override
    public TrackNodeReference dereference() {
        return TrackNodeReference.at(this._pos);
    }

    @Override
    public TrackNodeReference reference(TrackWorld world) {
        if (this._coaster == null) {
            TrackNode node = world.findNodeExact(this._pos);
            if (node != null) {
                return node;
            }
        }
        return this;
    }

    @FunctionalInterface
    public interface AddSignPredicate {
        boolean canAddSign(TrackNode node, TrackNodeSign sign);
    }

    @FunctionalInterface
    public interface UpdateAnimationStatePredicate {
        boolean canUpdateState(TrackNode node, TrackNodeAnimationState oldState, TrackNodeAnimationState newState);
    }
}
