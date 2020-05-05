package com.bergerkiller.bukkit.coasters.tracks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.TCCoastersUtil;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditMode;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.particles.TrackParticle;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleArrow;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleLitBlock;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleState;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleText;
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
    // Named target states of this track node, which can be used for animations
    private TrackNodeAnimationState[] _animationStates;
    //private TrackParticleItem _particle;
    private TrackParticleArrow _upParticleArrow;
    private List<TrackParticleText> _junctionParticles;
    private TrackParticleLitBlock _blockParticle;
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
    }

    @Override
    public CoasterWorld getWorld() {
        return this._coaster.getWorld();
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
    }

    public void markChanged() {
        this._coaster.markChanged();
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
        if (!this._pos.equals(position)) {
            this._pos = position.clone();
            //this._particle.setPosition(this._pos);
            this._upParticleArrow.setPosition(this._pos);
            if (this._railBlock == null) {
                this._blockParticle.setBlock(this.getRailBlock(true));
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
            if (!this._up.equals(up)) {
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
            this.scheduleRefresh();
            this.markChanged();
            return;
        }

        // Find the connection in the array and shift it to the start of the array
        for (int i = 0; i < this._connections.length; i++) {
            if (this._connections[i] == connection) {
                System.arraycopy(this._connections, 0, this._connections, 1, i);
                this._connections[0] = connection;
                this.scheduleRefresh();
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
     * Checks the connections to this node for a connection with another node
     * 
     * @param nodeReference The node (reference) of the node to find a connection with
     * @return Track connection found, null if no such connection exists
     */
    public final TrackConnection findConnectionWith(TrackNodeReference nodeReference) {
        for (TrackConnection connection : this._connections) {
            if (connection.getOtherNode(this).isReference(nodeReference)) {
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
        for (int i = 0; i < this._animationStates.length; i++) {
            if (this._animationStates[i].name.equals(name)) {
                this._animationStates[i].destroyParticles();
                this._animationStates = LogicUtil.removeArrayElement(this._animationStates, i);
                for (int j = i; j < this._animationStates.length; j++) {
                    this._animationStates[j].updateIndex(j);
                }
                this.getPlugin().forAllEditStates(editState -> editState.notifyNodeAnimationRemoved(TrackNode.this, name));
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
        // Overwrite existing
        for (int i = 0; i < this._animationStates.length; i++) {
            TrackNodeAnimationState old_state = this._animationStates[i];
            if (old_state == state) {
                return;
            } else if (old_state.name.equals(state.name)) {
                old_state.destroyParticles();
                this._animationStates[i] = state;
                state.spawnParticles(this, i);
                return;
            }
        }

        // Add new
        int new_index = this._animationStates.length;
        this._animationStates = Arrays.copyOf(this._animationStates, new_index+1);
        this._animationStates[new_index] = state;
        state.spawnParticles(this, new_index);

        // Refresh any player edit states so they become aware of this animation
        this.getPlugin().forAllEditStates(editState -> editState.notifyNodeAnimationAdded(this, state.name));
    }

    /**
     * Updates all the animation states of this track node smartly by making use of a manipulator function to alter them.
     * When name is null, all animation states are updated, otherwise only the one matching the name is.
     * 
     * @param name The name of the animation state to update, null to update all of them
     * @param manipulator Manipulator function to alter the original animation states
     */
    public void updateAnimationStates(String name, Function<TrackNodeAnimationState, TrackNodeAnimationState> manipulator) {
        for (int i = 0; i < this._animationStates.length; i++) {
            TrackNodeAnimationState old_state = this._animationStates[i];
            if (name == null || name.equals(old_state.name)) {
                TrackNodeAnimationState new_state = manipulator.apply(old_state);
                if (new_state != old_state) {
                    old_state.destroyParticles();
                    this._animationStates[i] = new_state;
                    new_state.spawnParticles(this, i);
                }
            }
        }
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
        if (this.hasAnimationStates() && connection.isConnected(this)) {
            final TrackConnectionState referenced_connection = connection.reference(this.getWorld().getTracks());
            this.updateAnimationStates(name, state -> state.updateConnection(referenced_connection));
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
        destroyParticles();
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
     * @param railsBlock
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
            if (connection_a != null) {
                points.remove(points.size() - 1);
            }

            if (connection_b.getNodeA() == this) {
                connection_b.buildPath(points, railsPos, getPlugin().getSmoothness(), 0.0, 0.5);
            } else {
                connection_b.buildPath(points, railsPos, getPlugin().getSmoothness(), 1.0, 0.5);
            }
        }

        RailPath.Builder builder = new RailPath.Builder();
        for (RailPath.Point point : points) {
            builder.add(point);
        }
        return builder.build();

        // Minimalist.
        /*
        IntVector3 railsPos = getRailsBlock();
        RailPath.Builder builder = new RailPath.Builder();
        if (connection_a != null) {
            if (connection_a.getNodeA() == this) {
                builder.add(connection_a.getPathPoint(railsPos, 0.5));
                builder.add(connection_a.getPathPoint(railsPos, 0.0));
            } else {
                builder.add(connection_a.getPathPoint(railsPos, 0.5));
                builder.add(connection_a.getPathPoint(railsPos, 1.0));
            }
        }
        if (connection_b != null) {
            builder.add(connection_b.getPathPoint(railsPos, 0.5));
        }
        return builder.build();
        */

        /*
        //TODO: We can use less or more iterations here based on how much is really needed
        // This would help performance a lot!
        RailPath.Builder builder = new RailPath.Builder();
        if (connection_a != null) {
            if (connection_a.getNodeA() == this) {
                for (int n = 499; n >= 0; --n) {
                    double t = 0.001 * n;
                    builder.add(connection_a.getPathPoint(railsPos, t));
                }
            } else {
                for (int n = 499; n <= 1000; n++) {
                    double t = 0.001 * n;
                    builder.add(connection_a.getPathPoint(railsPos, t));
                }
            }
        }
        if (connection_b != null) {
            if (connection_b.getNodeA() == this) {
                for (int n = 1; n <= 501; n++) {
                    double t = 0.001 * n;
                    builder.add(connection_b.getPathPoint(railsPos, t));
                }
            } else {
                for (int n = 1000-1; n >= 490; --n) {
                    double t = 0.001 * n;
                    builder.add(connection_b.getPathPoint(railsPos, t));
                }
            }
        }
        return builder.build();
        */
    }

    private void scheduleRefresh() {
        getWorld().getTracks().scheduleNodeRefresh(this);
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
     */
    @Override
    public TrackNode findOnWorld(TrackWorld world) {
        return (this._coaster != null) ? this : world.findNodeExact(this._pos);
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
}
