package com.bergerkiller.bukkit.coasters.tracks;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.bergerkiller.bukkit.coasters.objects.TrackObject;
import com.bergerkiller.bukkit.coasters.rails.TrackRailsWorld;
import com.google.common.collect.Iterables;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.world.CoasterWorld;
import com.bergerkiller.bukkit.coasters.world.CoasterWorldComponent;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.LogicUtil;

/**
 * Stores all the track groups and the special connections between track nodes.
 * There is one storage per world.
 */
public class TrackWorld implements CoasterWorldComponent {
    private final CoasterWorld _world;
    private final List<TrackCoaster> _coasters;
    private final NodeUpdateList _changedNodes = new NodeUpdateList();
    private final NodeUpdateList _changedNodesPriority = new NodeUpdateList();

    public TrackWorld(CoasterWorld world) {
        this._world = world;
        this._coasters = new ArrayList<TrackCoaster>();
    }

    @Override
    public final CoasterWorld getWorld() {
        return this._world;
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
     * Gets a track coaster by name
     *
     * @param name
     * @return coaster
     */
    public TrackCoaster getCoasterByName(String name) {
        for (TrackCoaster coaster : this._coasters) {
            if (coaster.getName().equals(name)) {
                return coaster;
            }
        }
        return null;
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
     * Looks for the node nearest to a point on a path looking at using a given eye
     * location. If no such node exists, returns null.
     *
     * @param eyeLocation
     * @param fov Field of view factor, 1.0 is default
     * @param maxDistance Maximum distance away from the eye location to include the results
     * @return Nearest node result, or null
     */
    public TrackNode findNodeLookingAt(Location eyeLocation, double fov, double maxDistance) {
        // Create an inverted camera transformation of the player's view direction
        Matrix4x4 cameraTransform = new Matrix4x4();
        cameraTransform.translateRotate(eyeLocation);
        return findNodeLookingAt(cameraTransform, fov, maxDistance);
    }

    /**
     * Looks for the node nearest to a point on a path looking at using a given eye
     * location camera transformation matrix. If no such node exists, returns null.
     *
     * @param cameraTransform Player eye camera transform
     * @param fov Field of view factor, 1.0 is default
     * @param maxDistance Maximum distance away from the eye location to include the results
     * @return Nearest node result, or null
     */
    public TrackNode findNodeLookingAt(Matrix4x4 cameraTransform, double fov, double maxDistance) {
        Vector startPos = cameraTransform.toVector();

        cameraTransform = cameraTransform.clone();
        cameraTransform.invert();

        double maxDistSq = maxDistance * maxDistance;
        double bestViewDistance = Double.MAX_VALUE;
        TrackNode bestNode = null;
        for (TrackCoaster coaster : getCoasters()) {
            for (TrackNode node : coaster.getNodes()) {
                if (node.getPosition().distanceSquared(startPos) > maxDistSq) {
                    continue;
                }

                double viewDistance = getViewDistance(cameraTransform, node.getPosition(), fov);
                if (viewDistance < bestViewDistance) {
                    bestViewDistance = viewDistance;
                    bestNode = node;
                }
            }
        }

        return bestNode;
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
     * Finds the track node that exists precisely at a particular 3d position.<br>
     * <br>
     * If multiple nodes exist at the same position (=zero distance neighbours),
     * then it will select the orphan node if i has zero connections to other nodes.
     * This makes sure that when creating links, the 'straightened' effect of the
     * node is preferred instead of creating random broken junctions.
     *
     * @param position Node position
     * @return node at the position, null if not found
     */
    public TrackNode findNodeExact(Vector position) {
        return findNodeExact(position, null);
    }

    /**
     * Finds the track node that exists precisely at a particular 3d position.<br>
     * <br>
     * If multiple nodes exist at the same position (=zero distance neighbours),
     * then it will select the orphan node if i has zero connections to other nodes.
     * This makes sure that when creating links, the 'straightened' effect of the
     * node is preferred instead of creating random broken junctions.
     *
     * @param position Node position
     * @param excludedNode If multiple track nodes exist at a position, makes sure to
     *                     exclude this node. Ignored if null.
     * @return node at the position, null if not found
     */
    public TrackNode findNodeExact(Vector position, TrackNode excludedNode) {
        for (TrackCoaster coaster : this._coasters) {
            TrackNode node = coaster.findNodeExact(position, excludedNode);
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
        TrackNode newNode = node.getCoaster().createNewNode(position, node.getOrientation().clone());
        this.addConnection(node, newNode);
        return newNode;
    }

    /**
     * Resets the connections a node has with other nodes. This removes all previous
     * connections and creates new ones to all the connected nodes specified.
     *
     * @param node Node to reset connections of
     * @param connections New connections of the node
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
        TrackConnection connection = connectWithoutAddingObjects(state);
        if (connection != null && addObjects && state.hasObjects()) {
            // Collect a list of objects that already exist on the connection
            List<TrackObject> existingObjects = connection.getObjects();

            // Add objects, but it is possible that the same connection already existed
            // So do verify the objects aren't already all added
            connection.addAllObjects(state, (conn, o) -> !Iterables.any(existingObjects, existing -> existing.isSameAs(o)));
        }
        return connection;
    }

    /**
     * Attempts making a connection using the connection state provided.
     * If no nodes exist at the positions inside the state, the operation fails and null is returned.
     * 
     * @param state TrackConnectionState
     * @return created connection, null on failure
     */
    private TrackConnection connectWithoutAddingObjects(TrackConnectionState state) {
        TrackNode nodeA, nodeB;
        if (state.node_a.isExistingNode()) {
            // If both are specified as an existing node, then do the normal connect() logic
            if (state.node_b.isExistingNode()) {
                return connect((TrackNode) state.node_a, (TrackNode) state.node_b);
            }

            // Try to find an existing connection with node_b reference
            nodeA = (TrackNode) state.node_a;
            TrackConnection existing = nodeA.findConnectionWithReference(state.node_b);
            if (existing != null) {
                return existing;
            }

            // Find node_b, if not found, fail
            if ((nodeB = state.node_b.findOnWorld(this, nodeA)) == null) {
                return null;
            }
        } else if (state.node_b.isExistingNode()) {
            // Try to find an existing connection with node_a reference
            nodeB = (TrackNode) state.node_b;
            TrackConnection existing = nodeB.findConnectionWithReference(state.node_a);
            if (existing != null) {
                return existing;
            }

            // Find node_a, if not found, fail
            if ((nodeA = state.node_a.findOnWorld(this, nodeB)) == null) {
                return null;
            }
        } else {
            nodeA = state.node_a.findOnWorld(this, null);
            nodeB = state.node_b.findOnWorld(this, nodeA);

            // Either node not found, fail
            if (nodeA == null || nodeB == null) {
                return null;
            }

            // Before proceeding, check whether there is a zero-distance neighbour of the nodes
            // that connects with the other nodes/zero-distance neighbour of that node
            // This avoids a bunch of weird situations where junctions come into existence.
            for (TrackNode nodeAZ : nodeA.getNodesAtPosition()) {
                for (TrackNode nodeBZ : nodeB.getNodesAtPosition()) {
                    TrackConnection existing = nodeAZ.findConnectionWithNode(nodeBZ);
                    if (existing != null) {
                        return existing;
                    }
                }
            }
        }

        // Create a new connection
        return this.addConnection(nodeA, nodeB);
    }

    /**
     * Connects two track nodes together, unless the two nodes are already connected together
     * 
     * @param nodeA Node A
     * @param nodeB Node B
     * @return connection that was found or created
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
        {
            TrackConnection existing = nodeA.findConnectionWithNode(nodeB);
            if (existing != null) {
                return existing;
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
     * @param state TrackConnectionState describing the connection between two nodes
     * @return True if removed, False if the connection could not be found and
     *         was not removed
     */
    public boolean disconnect(TrackConnectionState state) {
        TrackConnection connection = state.findOnWorld(this);
        return connection != null && disconnect(connection);
    }

    /**
     * Disconnects two track nodes, removing any existing connection between them
     * 
     * @param nodeA
     * @param nodeB
     * @return True if removed, False if the connection could not be found and
     *         was not removed
     */
    public boolean disconnect(TrackNode nodeA, TrackNode nodeB) {
        if (nodeA == nodeB) {
            return false; // Ignore
        }

        // Find the connection in nodeA
        for (TrackConnection connection : nodeA._connections) {
            if (connection.isConnected(nodeB)) {
                disconnect(connection);
                return true; // Done
            }
        }

        // Not found
        return false;
    }

    /**
     * Disconnects a connection that was previously created
     *
     * @param connection
     * @return True if the connection was broken. False if the connection didn't exist.
     * @see TrackConnection#remove()
     */
    public boolean disconnect(TrackConnection connection) {
        if (connection.getWorld() != _world) {
            throw new IllegalArgumentException("Connection is not on this world");
        }
        TrackNode nodeA = connection.getNodeA();
        TrackNode nodeB = connection.getNodeB();
        boolean disconnectedNodeA = removeConnectionFromNode(nodeA, connection);
        boolean disconnectedNodeB = removeConnectionFromNode(nodeB, connection);
        if (disconnectedNodeA || disconnectedNodeB) {
            scheduleNodeRefresh(nodeA);
            scheduleNodeRefresh(nodeB);
            connection.onRemoved();
            connection.markChanged();
            return true;
        } else {
            return false;
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
        this._changedNodesPriority.clear();
        this.rebuild();
    }

    /**
     * Resets and loads all coasters from file
     */
    public void load() {
        this.clear();

        // List all coasters saved on disk. List both .csv and .csv.tmp coasters.
        HashSet<String> coasterNames = new HashSet<String>();
        File[] filesInFolder = this.getWorld().getConfigFolder().listFiles();
        if (filesInFolder != null) {
            for (File coasterFile : filesInFolder) {
                String name = coasterFile.getName();
                if (name.endsWith(".csv.tmp")) {
                    coasterNames.add(TCCoasters.unescapeName(name.substring(0, name.length() - 8)));
                } else if (name.endsWith(".csv")) {
                    coasterNames.add(TCCoasters.unescapeName(name.substring(0, name.length() - 4)));
                }
            }
        }

        // Build and load the base of all coasters
        List<TrackCoaster.CoasterLoadFinalizeAction> finalizeActions = new ArrayList<>(coasterNames.size());
        for (String name : coasterNames) {
            TrackCoaster coaster = new TrackCoaster(this.getWorld(), name);
            this._coasters.add(coaster);
            finalizeActions.add(coaster.loadBase());
        }

        // Now all coasters are loaded in, create all the inter-coaster and junction connections
        finalizeActions.forEach(TrackCoaster.CoasterLoadFinalizeAction::finishCoaster);

        // Mark all coasters as unchanged
        for (TrackCoaster coaster : this._coasters) {
            coaster.refreshConnections();
            coaster.markUnchanged();
        }

        // Apply pending node changes and rebuild all track-rail information
        rebuild();
    }

    /**
     * Updates all nodes that have changed and rebuilds all track information
     * of {@link TrackRailsWorld}
     */
    public void rebuild() {
        // Ensure all updates have been notified/completed
        this._changedNodesPriority.clear(); // At this stage this shouldn't even contain elements
        runAllUpdates(this._changedNodes, false);

        // Rebuild all rail-tracked information of all nodes on this world
        {
            TrackRailsWorld rails = getWorld().getRails();
            rails.clear();

            // For debug: randomize the order of the nodes before rebuilding
            // This can be used to find and reproduce track rails section bugs that
            // are caused by specific orders of merging.
            if (isRandomizeRebuildEnabled()) {
                List<TrackNode> allNodes = getCoasters().stream()
                        .flatMap(c -> c.getNodes().stream())
                        .collect(Collectors.toCollection(ArrayList::new));

                long seed = new Random().nextLong();
                // seed = 123L;
                System.out.println("== Rebuilding with " + seed + " ==");
                Collections.shuffle(allNodes, new Random(seed));
                allNodes.forEach(rails::store);
            } else {
                for (TrackCoaster coaster : getCoasters()) {
                    for (TrackNode node : coaster.getNodes()) {
                        rails.store(node);
                    }
                }
            }
        }
    }

    private boolean isRandomizeRebuildEnabled() {
        return false;
    }

    /**
     * Called every tick to update any changed nodes
     */
    @Override
    public void updateAll() {
        this._changedNodesPriority.clear(); // At this stage this shouldn't even contain elements
        runAllUpdates(this._changedNodes, true);
    }

    /**
     * To be called manually, to update all the (adjacent) nodes scheduled using
     * {@link #scheduleNodeRefreshWithPriority(TrackNode)}
     */
    public void updateAllWithPriority() {
        this._changedNodes.removeAll(this._changedNodesPriority); // No need for these next tick
        runAllUpdates(this._changedNodesPriority, true);
    }

    private void runAllUpdates(NodeUpdateList updates, boolean updateRails) {
        Set<TrackNode> nodesToUpdate = updates.getAllNodesToUpdate();
        if (!nodesToUpdate.isEmpty()) {
            // Connections of changedNodes
            HashSet<TrackConnection> changedConnections = new HashSet<TrackConnection>(nodesToUpdate.size()+1);

            // Remove nodes from the changed list that have been removed
            // This avoids executing logic on removed nodes, or worse, adding to the rails world
            // Refresh all the node's shape and track the connections that also changed
            for (Iterator<TrackNode> iter = nodesToUpdate.iterator(); iter.hasNext(); ) {
                TrackNode changedNode = iter.next();
                if (changedNode.isRemoved()) {
                    iter.remove();
                    continue;
                }

                try {
                    changedNode.onShapeUpdated();
                } catch (Throwable t) {
                    getPlugin().getLogger().log(Level.SEVERE, "An error occurred updating shape of node " +
                            changedNode.getPosition(), t);
                }

                changedConnections.addAll(changedNode.getConnections());
            }

            for (TrackConnection changedConnection : changedConnections) {
                try {
                    changedConnection.onShapeUpdated();
                } catch (Throwable t) {
                    getPlugin().getLogger().log(Level.SEVERE, "An error occurred updating shape of connection [" +
                            changedConnection.getNodeA().getPosition() + " TO " +
                            changedConnection.getNodeB().getPosition() + "]", t);
                }
            }

            if (updateRails) {
                // Purge all cached rail information for the changed nodes
                this.getWorld().getRails().purge(nodesToUpdate);

                // Re-create all the cached rail information for the changed nodes
                for (TrackNode changedNode : nodesToUpdate) {
                    this.getWorld().getRails().store(changedNode);
                }
            }
            updates.clear();
        }
    }

    /**
     * Schedules a node for refreshing it's shape and path information in the world right away.
     * Caller should next call {@link #updateAllWithPriority()}.
     *
     * @param node
     */
    public void scheduleNodeRefreshWithPriority(TrackNode node) {
        this._changedNodes.add(node); // Just to make sure this gets handled at all in the future
        this._changedNodesPriority.add(node);
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
                File folder = this.getWorld().getConfigFolder(true);
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
        node._connections = LogicUtil.appendArray(node._connections, connection);
    }

    private static boolean removeConnectionFromNode(TrackNode node, TrackConnection connection) {
        if (node._connections.length != 1) {
            for (int i = 0; i < node._connections.length; i++) {
                if (node._connections[i] == connection) {
                    TrackConnection[] new_connections = new TrackConnection[node._connections.length - 1];
                    System.arraycopy(node._connections, 0, new_connections, 0, i);
                    System.arraycopy(node._connections, i+1, new_connections, i, node._connections.length-i-1);
                    node._connections = new_connections;
                    return true;
                }
            }
        } else if (node._connections[0] == connection) {
            node._connections = TrackConnection.EMPTY_ARR;
            return true;
        }

        /* Connection not found */
        return false;
    }

    private static final class NodeUpdateList {
        private final Set<TrackNode> changed = new HashSet<>();
        private final List<TrackNode> buffer = new ArrayList<>();

        public void add(TrackNode node) {
            changed.add(node);
        }

        public void remove(TrackNode node) {
            changed.remove(node);
        }

        public void removeAll(NodeUpdateList list) {
            changed.removeAll(list.changed);
        }

        public void clear() {
            changed.clear();
        }

        public Set<TrackNode> getAllNodesToUpdate() {
            Set<TrackNode> changed = this.changed;
            if (!changed.isEmpty()) {
                addTwoDeepNeighbours(changed, buffer);
            }
            return changed;
        }

        private static void addTwoDeepNeighbours(Set<TrackNode> changed, List<TrackNode> buffer) {
            // For two cycles, add the neighbours of the nodes we have already collected
            // this is because a node change has a ripple effect that extends two connected nodes down
            try {
                buffer.addAll(changed);
                for (int n = 0; n < 2; n++) {
                    int size = buffer.size();
                    for (int i = 0; i < size; i++) {
                        TrackNode node = buffer.get(i);
                        for (TrackConnection conn_a : node._connections) {
                            TrackNode other = conn_a.getOtherNode(node);
                            if (changed.add(other)) {
                                buffer.add(other);
                            }
                        }
                    }
                    buffer.subList(0, size).clear();
                }
            } finally {
                buffer.clear();
            }
        }
    }
}
