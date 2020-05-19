package com.bergerkiller.bukkit.coasters.tracks;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.world.CoasterWorld;
import com.bergerkiller.bukkit.coasters.world.CoasterWorldComponent;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Quaternion;

/**
 * Stores all the track groups and the special connections between track nodes.
 * There is one storage per world.
 */
public class TrackWorld implements CoasterWorldComponent {
    private final CoasterWorld _world;
    private final List<TrackCoaster> _coasters;
    private final Set<TrackNode> _changedNodes;
    private final List<TrackNode> _changedNodesLive;

    public TrackWorld(CoasterWorld world) {
        this._world = world;
        this._coasters = new ArrayList<TrackCoaster>();
        this._changedNodes = new HashSet<TrackNode>();
        this._changedNodesLive = new ArrayList<TrackNode>();
    }

    @Override
    public final CoasterWorld getWorld() {
        return this._world;
    }

    /**
     * Gets the folder in which coasters and other world-specific data is saved for this world.
     * This function also ensures that the folder itself exists.
     * 
     * @return world config folder
     */
    public File getConfigFolder() {
        World w = this.getBukkitWorld();
        File f = new File(this.getPlugin().getDataFolder(), w.getName() + "_" + w.getUID());
        f.mkdirs();
        return f;
    }

    /**
     * Gets a list of all coasters on this world
     * 
     * @return coasters
     */
    public List<TrackCoaster> getCoasters() {
        return this._coasters;
    }

    /**
     * Attempts to find the coaster by a given name
     * 
     * @param name
     * @return coaster
     */
    public TrackCoaster findCoaster(String name) {
        for (TrackCoaster coaster: this._coasters) {
            if (coaster.getName().equals(name)) {
                return coaster;
            }
        }
        return null;
    }

    /**
     * Looks for all nearby nodes and their connections and computes the exact point on a path
     * looking at using a given eye Location. If no such point can be found, then null is returned.
     * 
     * @param eyeLocation
     * @param fov Field of view factor, 1.0 is default
     * @return Point on the path looked at, null if not found
     */
    public TrackConnection.PointOnPath findPointOnPath(Location eyeLocation, double fov) {
        // Create an inverted camera transformation of the player's view direction
        Matrix4x4 cameraTransform = new Matrix4x4();
        cameraTransform.translateRotate(eyeLocation);
        return findPointOnPath(cameraTransform, fov);
    }

    /**
     * Looks for all nearby nodes and their connections and computes the exact point on a path
     * looking at using a given eye Location. If no such point can be found, then null is returned.
     * 
     * @param cameraTransform Player eye camera transform
     * @param fov Field of view factor, 1.0 is default
     * @return Point on the path looked at, null if not found
     */
    public TrackConnection.PointOnPath findPointOnPath(Matrix4x4 cameraTransform, double fov) {
        cameraTransform = cameraTransform.clone();
        cameraTransform.invert();

        double bestViewDistance = Double.MAX_VALUE;
        TrackConnection bestConnection = null;
        double bestTheta = 0.0;
        Vector bestPosition = null;
        for (TrackCoaster coaster : getCoasters()) {
            for (TrackNode node : coaster.getNodes()) {
                // Node itself
                Vector node_pos = node.getPosition().clone();
                cameraTransform.transformPoint(node_pos);

                // Compute x/z distance, which is used to quickly filter connections down below
                double node_viewDistanceSq = distanceSquaredXY(node_pos);

                // Check all connections
                for (TrackConnection connection : node.getConnections()) {
                    // Skip if way out of range
                    double d_sq = connection._endA.getDistance();
                    if (node_pos.getZ() < -d_sq) {
                        continue; // likely entirely behind the player
                    }
                    d_sq *= d_sq;
                    if (node_viewDistanceSq > d_sq) {
                        continue; // too far away left/right/up/down
                    }

                    // Find closest point
                    double theta;
                    if (connection.getNodeA() == node) {
                        theta = connection.findClosestPointInView(cameraTransform, 0.0, 0.5);
                    } else {
                        theta = connection.findClosestPointInView(cameraTransform, 0.5, 1.0);
                    }

                    // View function matching
                    Vector positionOnPath = connection.getPosition(theta);
                    double viewDistance = getViewDistance(cameraTransform, positionOnPath, fov);
                    if (viewDistance < bestViewDistance) {
                        bestViewDistance = viewDistance;
                        bestConnection = connection;
                        bestTheta = theta;
                        bestPosition = positionOnPath;
                    }
                }
            }
        }
        if (bestConnection == null) {
            return null;
        }

        // Get motion vector on the path. Make sure it is consistent, flip it to always be net positive.
        Vector motionVector = bestConnection.getMotionVector(bestTheta);
        if ((motionVector.getX() + motionVector.getY() + motionVector.getZ()) < 0.0) {
            motionVector.multiply(-1.0);
        }

        double bestDistance = bestConnection.computeDistance(0.0, bestTheta);
        Quaternion bestOrientation = Quaternion.fromLookDirection(motionVector, bestConnection.getOrientation(bestTheta));
        return new TrackConnection.PointOnPath(bestConnection, bestTheta, bestDistance, bestPosition, bestOrientation);
    }

    private static double getViewDistance(Matrix4x4 cameraTransform, Vector pos, double fov) {
        pos = pos.clone();
        cameraTransform.transformPoint(pos);

        // Behind the player
        if (pos.getZ() <= 1e-6) {
            return Double.MAX_VALUE;
        }

        // Calculate limit based on depth (z) and filter based on it
        double lim = fov * Math.max(1.0, pos.getZ() * Math.pow(4.0, -pos.getZ()));
        if (Math.abs(pos.getX()) > lim || Math.abs(pos.getY()) > lim) {
            return Double.MAX_VALUE;
        }

        // Calculate 2d distance
        return Math.sqrt(pos.getX() * pos.getX() + pos.getY() * pos.getY()) / lim;
    }

    private static double distanceSquaredXY(Vector v) {
        return v.getX()*v.getX() + v.getY()*v.getY();
    }

    /**
     * Checks whether a particular node actually exists on this World.
     * 
     * @param node to check
     * @return True if it exists on this World
     */
    public boolean containsNode(TrackNode node) {
        for (TrackCoaster coaster : this._coasters) {
            if (coaster.getNodes().contains(node)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the track node that exists precisely at a particular 3d position
     * 
     * @param position
     * @return node at the position, null if not found
     */
    public TrackNode findNodeExact(Vector position) {
        for (TrackCoaster coaster : this._coasters) {
            TrackNode node = coaster.findNodeExact(position);
            if (node != null) {
                return node;
            }
        }
        return null;
    }

    /**
     * Finds the track nodes that are approximately nearby a particular 3d position.
     * Is used when snapping two nodes together and combine them, and also to check a node
     * doesn't already exist at a particular location when creating new nodes.
     * 
     * @param result to add found nodes to
     * @param position
     * @param radius
     * @return result
     */
    public List<TrackNode> findNodesNear(List<TrackNode> result, Vector position, double radius) {
        for (TrackCoaster coaster : this._coasters) {
            coaster.findNodesNear(result, position, radius);
        }
        return result;
    }

    /**
     * Removes a coaster from this world, removing all nodes contained within
     * 
     * @param coaster  The coaster to remove
     */
    public void removeCoaster(TrackCoaster coaster) {
        coaster.clear();
        this._coasters.remove(coaster);
    }

    /**
     * Creates a very new coaster, with only a single track node.
     * It will have an auto-generated name.
     * 
     * @param state of the first node of the new coaster
     * @return new track coaster
     */
    public TrackCoaster createNew(TrackNodeState state) {
        return createNew(this.getPlugin().generateNewCoasterName(), state);
    }

    /**
     * Creates a very new coaster, with only a single track node.
     * It will have an auto-generated name.
     * 
     * @param firstNodePosition
     * @return new track coaster
     */
    public TrackCoaster createNew(Vector firstNodePosition) {
        return createNew(this.getPlugin().generateNewCoasterName(), firstNodePosition, new Vector(0.0, 1.0, 0.0));
    }

    /**
     * Creates a very new coaster, with only a single track node.
     * Coaster name can be specified. If the coaster by this name already exists,
     * a new node is added to the coaster and no new coaster is created.
     * 
     * @param coasterName
     * @param state of the new node for the coaster
     * @return new track coaster
     */
    public TrackCoaster createNew(String coasterName, TrackNodeState state) {
        TrackCoaster coaster = this.createNewEmpty(coasterName);
        coaster.createNewNode(state);
        return coaster;
    }

    /**
     * Creates a new empty coaster with no initial nodes and a default generated name
     * 
     * @return new track coaster
     */
    public TrackCoaster createNewEmpty() {
        return this.createNewEmpty(this.getPlugin().generateNewCoasterName());
    }

    /**
     * Creates a new empty coaster with no initial nodes.
     * Coaster name can be specified. If the coaster by this name already exists,
     * it is returned instead. If no node is added to this coaster and it remains empty,
     * it will not be saved in the future.
     * 
     * @param coasterName
     * @return new track coaster
     */
    public TrackCoaster createNewEmpty(String coasterName) {
        TrackCoaster coaster = this.findCoaster(coasterName);
        if (coaster == null) {
            coaster = new TrackCoaster(this.getWorld(), coasterName);
            this._coasters.add(coaster);
        }
        return coaster;
    }

    /**
     * Creates a very new coaster, with only a single track node.
     * Coaster name can be specified. If the coaster by this name already exists,
     * a new node is added to the coaster and no new coaster is created.
     * 
     * @param coasterName
     * @param firstNodePosition
     * @param firstNodeUp
     * @return new track coaster
     */
    public TrackCoaster createNew(String coasterName, Vector firstNodePosition, Vector firstNodeUp) {
        return createNew(coasterName, TrackNodeState.create(firstNodePosition, firstNodeUp, null));
    }

    /**
     * Adds a new track node that is connected to another node, with the same up-vector as
     * the node connected to.
     * 
     * @param node
     * @param position
     * @return newly created node
     */
    public TrackNode addNode(TrackNode node, Vector position) {
        if (node.getBukkitWorld() != this.getBukkitWorld()) {
            throw new IllegalArgumentException("Input node is not on world " + this.getBukkitWorld().getName());
        }
        TrackNode newNode = node.getCoaster().createNewNode(position, node.getOrientation());
        this.addConnection(node, newNode);
        return newNode;
    }

    /**
     * Changes the connections a node has with other nodes. This removes all previous
     * connections and creates new ones to all the connected nodes specified.
     * 
     * @param node
     * @param connectedNodes
     */
    public void resetConnections(TrackNode node, List<TrackConnectionState> connections) {
        //TODO: Some connections may not change, and this can be optimized!
        //TODO: This optimization should take into account changes of track objects on the connections!
        disconnectAll(node, false);

        for (TrackConnectionState connection : connections) {
            if (connection.isConnected(node)) {
                this.connect(connection, true);
            }
        }
    }

    /**
     * Attempts making a connection using the connection state provided.
     * If no nodes exist at the positions inside the state, the operation fails and null is returned.
     * 
     * @param state
     * @return created connection, null on failure
     */
    public TrackConnection connect(TrackConnectionState state, boolean addObjects) {
        TrackNode nodeA = state.node_a.findOnWorld(this);
        TrackNode nodeB = state.node_b.findOnWorld(this);
        if (nodeA == null || nodeB == null || nodeA == nodeB) {
            return null;
        } else {
            TrackConnection connection = this.connect(nodeA, nodeB);
            if (addObjects) {
                connection.addAllObjects(state);
            }
            return connection;
        }
    }

    /**
     * Connects two track nodes together
     * 
     * @param nodeA
     * @param nodeB
     * @return connection
     */
    public TrackConnection connect(TrackNode nodeA, TrackNode nodeB) {
        // Let's not do this
        if (nodeA == nodeB) {
            throw new IllegalArgumentException("Input nodeA and nodeB are the same nodes!");
        }
        // Verify both nodes actually exist
        if (!this.containsNode(nodeA)) {
            throw new IllegalArgumentException("Input nodeA was deleted and does not exist");
        }
        if (!this.containsNode(nodeB)) {
            throw new IllegalArgumentException("Input nodeB was deleted and does not exist");
        }
        // Verify no such connection exists yet
        for (TrackConnection connection : nodeA._connections) {
            if (connection.isConnected(nodeB)) {
                return connection;
            }
        }
        // Safety!
        if (nodeA.getBukkitWorld() != this.getBukkitWorld()) {
            throw new IllegalArgumentException("Input nodeA is not on world " + this.getBukkitWorld().getName());
        }
        if (nodeB.getBukkitWorld() != this.getBukkitWorld()) {
            throw new IllegalArgumentException("Input nodeB is not on world " + this.getBukkitWorld().getName());
        }
        // Create new connection
        return addConnection(nodeA, nodeB);
    }

    /**
     * Disconnects two track nodes, removing any existing connection between them
     * 
     * @param nodeA
     * @param nodeB
     */
    public void disconnect(TrackNode nodeA, TrackNode nodeB) {
        if (nodeA == nodeB) {
            return; // Ignore
        }

        // Find the connection in nodeA
        for (TrackConnection connection : nodeA._connections) {
            if (connection.isConnected(nodeB)) {
                removeConnectionFromNode(nodeA, connection);
                removeConnectionFromNode(nodeB, connection);
                scheduleNodeRefresh(nodeA);
                scheduleNodeRefresh(nodeB);
                connection.onRemoved();
                connection.markChanged();
                return; // Done
            }
        }
    }

    /**
     * Removes all connections from/to a particular node.
     * Does not remove connections stored in animations.
     * 
     * @param node The node to disconnect all other nodes from
     */
    public void disconnectAll(TrackNode node) {
        disconnectAll(node, false);
    }

    /**
     * Removes all connections from/to a particular node
     * 
     * @param node The node to disconnect all other nodes from
     * @param fromAnimations Whether to remove connections stored in animations
     */
    public void disconnectAll(TrackNode node, boolean fromAnimations) {
        // Store connections and clear for the node itself
        TrackConnection[] connections = node._connections;
        node._connections = new TrackConnection[0];

        // Clear connections of all animation states if needed
        if (fromAnimations) {
            node.clearAnimationStateConnections();
        }

        // Schedule refresh of node
        scheduleNodeRefresh(node);

        // Also perform proper removal logic of the connections themselves
        for (TrackConnection conn : connections) {
            TrackNode other = conn.getOtherNode(node);

            // Remove all connections from the other node
            removeConnectionFromNode(other, conn);

            // Remove from animations also
            if (fromAnimations && other.hasAnimationStates()) {
                other.removeAnimationStateConnection(null, node);
            }

            // Schedule refresh of other node
            scheduleNodeRefresh(other);

            // Destroy connection
            conn.onRemoved();
            conn.markChanged();
        }
    }

    private final TrackConnection addConnection(TrackNode nodeA, TrackNode nodeB) {
        TrackConnection connection = new TrackConnection(nodeA, nodeB);
        addConnectionToNode(nodeA, connection);
        addConnectionToNode(nodeB, connection);
        scheduleNodeRefresh(nodeA);
        scheduleNodeRefresh(nodeB);
        connection.markChanged();
        return connection;
    }

    /**
     * Deletes all coasters from this world
     */
    public void clear() {
        // Perform clearing logic
        for (TrackCoaster coaster : this._coasters) {
            coaster.clear();
        }
        this._coasters.clear();
        this._changedNodes.clear();

        this.getWorld().getRails().clear();
    }

    /**
     * Resets and loads all coasters from file
     */
    public void load() {
        this.clear();

        // List all coasters saved on disk. List both .csv and .csv.tmp coasters.
        HashSet<String> coasterNames = new HashSet<String>();
        for (File coasterFile : this.getConfigFolder().listFiles()) {
            String name = coasterFile.getName().toLowerCase(Locale.ENGLISH);
            if (name.endsWith(".csv.tmp")) {
                coasterNames.add(TCCoasters.unescapeName(name.substring(0, name.length() - 8)));
            } else if (name.endsWith(".csv")) {
                coasterNames.add(TCCoasters.unescapeName(name.substring(0, name.length() - 4)));
            }
        }

        // Build and load all coasters
        for (String name : coasterNames) {
            TrackCoaster coaster = new TrackCoaster(this.getWorld(), name);
            this._coasters.add(coaster);
            coaster.load();
        }

        // Mark all coasters as unchanged
        for (TrackCoaster coaster : this._coasters) {
            coaster.refreshConnections();
            coaster.markUnchanged();
        }
    }

    /**
     * Called every tick to update any changed nodes
     */
    public void updateAll() {
        if (!this._changedNodes.isEmpty()) {
            // For two cycles, add the neighbours of the nodes we have already collected
            // this is because a node change has a ripple effect that extends two connected nodes down
            this._changedNodesLive.addAll(this._changedNodes);
            for (int n = 0; n < 2; n++) {
                int size = this._changedNodesLive.size();
                for (int i = 0; i < size; i++) {
                    TrackNode node = this._changedNodesLive.get(i);
                    for (TrackConnection conn_a : node._connections) {
                        TrackNode other = conn_a.getOtherNode(node);
                        if (this._changedNodes.add(other)) {
                            this._changedNodesLive.add(other);
                        }
                    }
                }
                this._changedNodesLive.subList(0, size).clear();
            }
            this._changedNodesLive.clear();

            // Connections of changedNodes
            HashSet<TrackConnection> changedConnections = new HashSet<TrackConnection>(this._changedNodes.size()+1);

            // Remove nodes from the changed list that have been removed
            // This avoids executing logic on removed nodes, or worse, adding to the rails world
            // Refresh all the node's shape and track the connections that also changed
            for (Iterator<TrackNode> iter = this._changedNodes.iterator(); iter.hasNext(); ) {
                TrackNode changedNode = iter.next();
                if (changedNode.isRemoved()) {
                    iter.remove();
                    continue;
                }

                changedNode.onShapeUpdated();
                changedConnections.addAll(changedNode.getConnections());
            }
            for (TrackConnection changedConnection : changedConnections) {
                changedConnection.onShapeUpdated();
            }

            // Purge all cached rail information for the changed nodes
            this.getWorld().getRails().purge(this._changedNodes);

            // Re-create all the cached rail information for the changed nodes
            for (TrackNode changedNode : this._changedNodes) {
                this.getWorld().getRails().store(changedNode);
            }
            this._changedNodes.clear();
        }
    }

    /**
     * Schedules a node for refreshing it's shape and path information in the world for the next tick
     * 
     * @param node
     */
    public void scheduleNodeRefresh(TrackNode node) {
        this._changedNodes.add(node);
    }

    /**
     * Cancels any refreshing planned for a node
     * 
     * @param node
     */
    public void cancelNodeRefresh(TrackNode node) {
        this._changedNodes.remove(node);
    }

    /**
     * Saves all coasters stored inside the world to disk, even when they do not have
     * any changes in them. This is useful if the original files on the disk were manipulated,
     * and need to be regenerated.
     */
    public void saveForced() {
        save(false);
    }

    /**
     * Saves all coasters stored inside the world to disk when they have changes
     * since the last time it was saved.
     */
    public void saveChanges() {
        save(true);
    }

    /**
     * Saves all coasters stored inside the world to disk
     * 
     * @param autosave whether to save only when changes occurred (true), or all the time (false)
     */
    private void save(boolean autosave) {
        Iterator<TrackCoaster> iter = this._coasters.iterator();
        while (iter.hasNext()) {
            TrackCoaster coaster = iter.next();
            if (coaster.getNodes().isEmpty()) {
                coaster.clear();
                iter.remove();

                // Deletes the physical saved files of the coasters
                String baseName = TCCoasters.escapeName(coaster.getName());
                File folder = getConfigFolder();
                File tmpFile = new File(folder, baseName + ".csv.tmp");
                File realFile = new File(folder, baseName + ".csv");
                if (tmpFile.exists()) {
                    tmpFile.delete();
                }
                if (realFile.exists()) {
                    realFile.delete();
                }
            } else {
                coaster.save(autosave);
            }
        }
    }

    private static void addConnectionToNode(TrackNode node, TrackConnection connection) {
        node._connections = Arrays.copyOf(node._connections, node._connections.length + 1);
        node._connections[node._connections.length - 1] = connection;
    }

    private static void removeConnectionFromNode(TrackNode node, TrackConnection connection) {
        if (node._connections.length != 1) {
            for (int i = 0; i < node._connections.length; i++) {
                if (node._connections[i] == connection) {
                    TrackConnection[] new_connections = new TrackConnection[node._connections.length - 1];
                    System.arraycopy(node._connections, 0, new_connections, 0, i);
                    System.arraycopy(node._connections, i+1, new_connections, i, node._connections.length-i-1);
                    node._connections = new_connections;
                    break;
                }
            }
        } else if (node._connections[0] == connection) {
            node._connections = TrackConnection.EMPTY_ARR;
        }
    }
}
