package com.bergerkiller.bukkit.coasters.editor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.TCCoastersLocalization;
import com.bergerkiller.bukkit.coasters.TCCoastersUtil;
import com.bergerkiller.bukkit.coasters.TCCoastersUtil.TargetedBlockInfo;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChange;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChangeCollection;
import com.bergerkiller.bukkit.coasters.editor.object.ObjectEditState;
import com.bergerkiller.bukkit.coasters.events.CoasterSelectNodeEvent;
import com.bergerkiller.bukkit.coasters.objects.TrackObject;
import com.bergerkiller.bukkit.coasters.tracks.TrackCoaster;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeAnimationState;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeSearchPath;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeState;
import com.bergerkiller.bukkit.coasters.tracks.TrackPendingConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackWorld;
import com.bergerkiller.bukkit.coasters.world.CoasterWorld;
import com.bergerkiller.bukkit.coasters.world.CoasterWorldComponent;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.google.common.collect.Ordering;
import com.google.common.collect.TreeMultimap;

/**
 * The track editing state of a single player.
 * Note that this state is only ever valid for the World the player is on.
 * When the player changed world, any edited nodes are automatically cleared.
 */
public class PlayerEditState implements CoasterWorldComponent {
    private static final int EDIT_AUTO_TIMEOUT = 100;
    private final TCCoasters plugin;
    private final Player player;
    private final PlayerEditInput input;
    private final PlayerEditHistory history;
    private final PlayerEditClipboard clipboard;
    private final ObjectEditState objectState;
    private final Map<TrackNode, PlayerEditNode> editedNodes = new LinkedHashMap<TrackNode, PlayerEditNode>();
    private final TreeMultimap<String, TrackNode> editedNodesByAnimationName = TreeMultimap.create(Ordering.natural(), Ordering.arbitrary());
    private final Map<TrackObject, PlayerEditTrackObject> editedTrackObjects = new LinkedHashMap<TrackObject, PlayerEditTrackObject>();
    private CoasterWorld cachedCoasterWorld = null;
    private TrackNode lastEdited = null;
    private TrackObject lastEditedTrackObject = null;
    private long lastEditTime = System.currentTimeMillis();
    private long lastEditTrackObjectTime = System.currentTimeMillis();
    private PlayerEditMode editMode = PlayerEditMode.DISABLED;
    private PlayerEditMode afterEditMode = null;
    private int heldDownTicks = 0;
    private boolean changed = false;
    private boolean editedAnimationNamesChanged = false;
    private Matrix4x4 editStartTransform = null;
    private Vector editRotInfo = new Vector(); // virtual coordinate of the vector
    private Block targetedBlock = null;
    private BlockFace targetedBlockFace = BlockFace.UP;
    private String selectedAnimation = null;

    public PlayerEditState(TCCoasters plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.input = new PlayerEditInput(player);
        this.history = new PlayerEditHistory(player);
        this.clipboard = new PlayerEditClipboard(this);
        this.objectState = new ObjectEditState();
    }

    /**
     * Gets the coaster world the player is currently on
     * 
     * @return coaster world
     */
    @Override
    public CoasterWorld getWorld() {
        World bukkitWorld = this.player.getWorld();
        if (this.cachedCoasterWorld == null || this.cachedCoasterWorld.getBukkitWorld() != bukkitWorld) {
            this.cachedCoasterWorld = this.plugin.getCoasterWorld(bukkitWorld);
        }
        return this.cachedCoasterWorld;
    }

    public void load() {
        FileConfiguration config = this.plugin.getPlayerConfig(this.player);
        if (config.exists()) {
            config.load();

            this.editMode = config.get("mode", PlayerEditMode.DISABLED);
            this.selectedAnimation = config.get("selectedAnimation", String.class, null);
            this.getObjectState().load(config.getNode("object"));
            this.editedNodes.clear();
            this.editedNodesByAnimationName.clear();
            this.editedAnimationNamesChanged = true;
            List<String> editNodePositions = config.getList("editedNodes", String.class);
            if (editNodePositions != null && !editNodePositions.isEmpty()) {
                for (String nodeStr : editNodePositions) {
                    String[] coords = nodeStr.split("_");
                    if (coords.length == 3) {
                        try {
                            double x, y, z;
                            x = Double.parseDouble(coords[0]);
                            y = Double.parseDouble(coords[1]);
                            z = Double.parseDouble(coords[2]);
                            TrackNode node = getWorld().getTracks().findNodeExact(new Vector(x, y, z));
                            if (node != null) {
                                this.editedNodes.put(node, new PlayerEditNode(node));
                                for (TrackNodeAnimationState animation : node.getAnimationStates()) {
                                    this.editedNodesByAnimationName.put(animation.name, node);
                                }
                            }
                        } catch (NumberFormatException ex) {}
                    }
                }
            }
        }
        this.changed = false;
    }

    public void save() {
        if (!this.changed) {
            return;
        }
        this.changed = false;
        FileConfiguration config = this.plugin.getPlayerConfig(this.player);
        config.set("mode", this.editMode);
        List<String> editedNodeNames = new ArrayList<String>(this.editedNodes.size());
        for (TrackNode node : this.getEditedNodes()) {
            Vector p = node.getPosition();
            editedNodeNames.add(p.getX() + "_" + p.getY() + "_" + p.getZ());
        }
        config.set("editedNodes", editedNodeNames);
        if (this.selectedAnimation == null) {
            config.remove("selectedAnimation");
        } else {
            config.set("selectedAnimation", this.selectedAnimation);
        }
        this.getObjectState().save(config.getNode("object"));
        config.save();
    }

    public Player getPlayer() {
        return this.player;
    }

    public PlayerEditHistory getHistory() {
        return this.history;
    }

    public PlayerEditClipboard getClipboard() {
        return this.clipboard;
    }

    public ObjectEditState getObjectState() {
        return this.objectState;
    }

    public long getLastEditTime(TrackNode node) {
        if (this.lastEdited != node) {
            return Long.MAX_VALUE;
        } else {
            return System.currentTimeMillis() - this.lastEditTime;
        }
    }

    public long getLastEditTime(TrackObject object) {
        if (this.lastEditedTrackObject != object) {
            return Long.MAX_VALUE;
        } else {
            return System.currentTimeMillis() - this.lastEditTrackObjectTime;
        }
    }

    public void clearEditedNodes() {
        if (!this.editedNodes.isEmpty()) {
            ArrayList<TrackNode> oldNodes = new ArrayList<TrackNode>(this.getEditedNodes());
            this.editedNodes.clear();
            this.editedAnimationNamesChanged |= !this.editedNodesByAnimationName.isEmpty();
            this.editedNodesByAnimationName.clear();
            this.lastEdited = null;
            this.changed = true;
            for (TrackNode oldNode : oldNodes) {
                oldNode.onStateUpdated(this.player);
            }
        }
    }

    public void clearEditedTrackObjects() {
        if (!this.editedTrackObjects.isEmpty()) {
            ArrayList<TrackObject> oldObjects = new ArrayList<TrackObject>(this.getEditedTrackObjects());
            this.editedTrackObjects.clear();

            //this.editedAnimationNamesChanged |= !this.editedNodesByAnimationName.isEmpty();
            //this.editedNodesByAnimationName.clear();
            //this.lastEdited = null;

            this.changed = true;
            for (TrackObject oldObject : oldObjects) {
                oldObject.onStateUpdated(this.player);
            }
        }
    }

    /**
     * Called when an animation is added to a node. For internal use.
     * 
     * @param node
     * @param animationName
     */
    public void notifyNodeAnimationAdded(TrackNode node, String animationName) {
        Set<TrackNode> nodes = this.editedNodesByAnimationName.get(animationName);
        this.editedAnimationNamesChanged |= (nodes.add(node) && nodes.size() == 1);
    }

    /**
     * Called when an animation is removed for a node. For internal use.
     * 
     * @param node
     * @param animationName
     */
    public void notifyNodeAnimationRemoved(TrackNode node, String animationName) {
        Set<TrackNode> nodes = this.editedNodesByAnimationName.get(animationName);
        this.editedAnimationNamesChanged |= (nodes.remove(node) && nodes.isEmpty());
    }

    /**
     * Gets an alphabetically sorted collection of distinct animation names present in the currently edited nodes
     * 
     * @return edited animation names
     */
    public Collection<String> getEditedAnimationNames() {
        return this.editedNodesByAnimationName.keySet();
    }

    /**
     * Sets the name of an animation to assign to the selected nodes while editing them.
     * Keeps changes to position, orientation, connections and rail block synchronized.
     * Set to null to stop editing animations.
     * 
     * @param animationName
     */
    public void setSelectedAnimation(String animationName) {
        if (!LogicUtil.bothNullOrEqual(this.selectedAnimation, animationName)) {
            this.selectedAnimation = animationName;
            this.editedAnimationNamesChanged = true;
            if (animationName != null) {
                for (TrackNode node : this.getSelectedAnimationNodes()) {
                    node.playAnimation(animationName, 0.0);
                }
            }
        }
    }

    /**
     * Gets the name of the animation being edited. Is null if no animation is being edited.
     * 
     * @return selected animation name
     */
    public String getSelectedAnimation() {
        return this.selectedAnimation;
    }

    /**
     * Gets a set of nodes that contain the animation with the name that is currently selected
     * 
     * @return selected animation nodes
     */
    public Set<TrackNode> getSelectedAnimationNodes() {
        return this.editedNodesByAnimationName.get(this.selectedAnimation);
    }

    /**
     * Finds the track node the player is currently looking at exactly
     * 
     * @return node being looked at
     */
    public TrackNode findLookingAt() {
        // Create an inverted camera transformation of the player's view direction
        Matrix4x4 cameraTransform = new Matrix4x4();
        cameraTransform.translateRotate(this.player.getEyeLocation());
        cameraTransform.invert();

        // Go by all selected track nodes, and pick those close in view on the same world
        // The transformed point is a projective view of the Minecart in the player's vision
        // X/Y is left-right/up-down and Z is depth after the transformation is applied
        TrackNode bestNode = null;
        double bestDistance = 0.3;
        for (TrackNode node : this.getEditedNodes()) {
            double distance = node.getViewDistance(cameraTransform);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestNode = node;
            }
        }
        return bestNode;
    }

    public PlayerEditMode getMode() {
        return this.editMode;
    }

    public boolean isMode(PlayerEditMode... modes) {
        return LogicUtil.contains(this.editMode, modes);
    }

    public Set<TrackNode> getEditedNodes() {
        return this.editedNodes.keySet();
    }

    public Set<TrackObject> getEditedTrackObjects() {
        return this.editedTrackObjects.keySet();
    }

    /**
     * Gets a set of all the coasters of which a node is being edited
     * 
     * @return edited coasters
     */
    public Set<TrackCoaster> getEditedCoasters() {
        HashSet<TrackCoaster> coasters = new HashSet<TrackCoaster>();
        for (TrackNode node : getEditedNodes()) {
            coasters.add(node.getCoaster());
        }
        return coasters;
    }

    /**
     * Gets the last node that was clicked by the player to be edited
     * 
     * @return last edited node, null if no nodes are being edited.
     */
    public TrackNode getLastEditedNode() {
        TrackNode last = null;
        for (TrackNode node : this.editedNodes.keySet()) {
            last = node;
        }
        return last;
    }

    public boolean hasEditedNodes() {
        return !this.editedNodes.isEmpty();
    }

    public boolean hasEditedTrackObjects() {
        return !this.editedTrackObjects.isEmpty();
    }

    /**
     * Deselects all nodes that we cannot actually edit, because the coaster they
     * are part of is locked.
     * 
     * @return True if nodes were deselected
     */
    public boolean deselectLockedNodes() {
        boolean hadLockedNodes = false;
        Iterator<TrackNode> iter = this.editedNodes.keySet().iterator();
        while (iter.hasNext()) {
            TrackNode node = iter.next();
            if (node.isLocked()) {
                iter.remove();
                hadLockedNodes = true;
                node.onStateUpdated(this.player);
                this.lastEdited = node;
                this.editedAnimationNamesChanged |= node.hasAnimationStates();
            }
        }
        if (hadLockedNodes) {
            this.lastEditTime = System.currentTimeMillis();
            this.changed = true;
            TCCoastersLocalization.LOCKED.message(this.player);
        }
        return hadLockedNodes;
    }

    /**
     * Deselects all track objects that we cannot actually edit, because the connection
     * they are on is on a coaster that is locked.
     * 
     * @return True if track objects were deselected
     */
    public boolean deselectLockedTrackObjects() {
        boolean hadLockedTrackObjects = false;
        Iterator<PlayerEditTrackObject> iter = this.editedTrackObjects.values().iterator();
        while (iter.hasNext()) {
            PlayerEditTrackObject editObject = iter.next();
            if (editObject.connection.isLocked()) {
                iter.remove();
                hadLockedTrackObjects = true;
                editObject.object.onStateUpdated(this.player);
                this.lastEditedTrackObject = editObject.object;
            }
        }
        if (hadLockedTrackObjects) {
            this.lastEditTrackObjectTime = System.currentTimeMillis();
            this.changed = true;
            TCCoastersLocalization.LOCKED.message(this.player);
        }
        return hadLockedTrackObjects;
    }

    public void setMode(PlayerEditMode mode) {
        this.afterEditMode = null;
        if (this.editMode != mode) {
            this.editMode = mode;
            this.changed = true;

            // Mode change may have changed what particles are visible
            getWorld().getParticles().scheduleViewerUpdate(this.player);
            getWorld().getParticles().update(this.player);

            // Refresh the nodes and their particles based on the mode
            for (TrackNode node : this.getEditedNodes()) {
                node.onStateUpdated(this.player);
            }
        }
    }

    /**
     * Sets the mode to switch to after releasing right-click in the current mode
     * 
     * @param mode
     */
    public void setAfterEditMode(PlayerEditMode mode) {
        this.afterEditMode = mode;
    }

    /**
     * Tries to select a node for editing. If not already editing,
     * an event is fired to check whether selecting the node is permitted
     * 
     * @param node The node to select
     * @return True if the node was selected or was already selected, False if this was disallowed
     */
    public boolean selectNode(TrackNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Node can not be null");
        }
        if (this.editedNodes.containsKey(node)) {
            return true;
        }
        if (CommonUtil.callEvent(new CoasterSelectNodeEvent(this.player, node)).isCancelled()) {
            return false;
        }
        this.setEditing(node, true);
        return true;
    }

    /**
     * Tries to select a track object for editing. If not already editing,
     * an event is fired to check whether selecting the track object is permitted
     * 
     * @param connection The connection the object is on
     * @param object The object being selected
     * @return True if the object was selected or was already selected, False if this was disallowed
     */
    public boolean selectTrackObject(TrackConnection connection, TrackObject object) {
        if (object == null) {
            throw new IllegalArgumentException("Track object can not be null");
        }
        if (this.editedTrackObjects.containsKey(object)) {
            return true;
        }

        //TODO!
        //if (CommonUtil.callEvent(new CoasterSelectNodeEvent(this.player, node)).isCancelled()) {
        //    return false;
        //}

        this.setEditingTrackObject(connection, object, true);
        return true;
    }

    public void setEditing(TrackNode node, boolean editing) {
        if (node == null) {
            throw new IllegalArgumentException("Node can not be null");
        }
        boolean changed;
        if (editing) {
            if (this.editedNodes.containsKey(node)) {
                changed = false;
            } else {
                changed = true;
                this.editedNodes.put(node, new PlayerEditNode(node));
            }
        } else {
            changed = (this.editedNodes.remove(node) != null);
        }
        if (changed) {
            // Can be caused by the node being removed, handle that here
            if (!node.isRemoved()) {
                node.onStateUpdated(this.player);
            }
            for (TrackNodeAnimationState state : node.getAnimationStates()) {
                Set<TrackNode> values = this.editedNodesByAnimationName.get(state.name);
                if (editing && values.add(node)) {
                    this.editedAnimationNamesChanged |= (values.size() == 1);
                } else if (!editing && values.remove(node)) {
                    this.editedAnimationNamesChanged |= values.isEmpty();
                }
            }

            this.lastEdited = node;
            this.lastEditTime = System.currentTimeMillis();
            this.changed = true;
        }
    }

    public void setEditingTrackObject(TrackConnection connection, TrackObject object, boolean editing) {
        if (object == null) {
            throw new IllegalArgumentException("Track Object can not be null");
        }
        boolean changed;
        if (editing) {
            if (this.editedTrackObjects.containsKey(object)) {
                changed = false;
            } else {
                changed = true;
                this.editedTrackObjects.put(object, new PlayerEditTrackObject(connection, object));
            }
        } else {
            changed = (this.editedTrackObjects.remove(object) != null);
        }
        if (changed) {
            // Can be caused by the node being removed, handle that here
            object.onStateUpdated(this.player);

            /*
            for (TrackNodeAnimationState state : node.getAnimationStates()) {
                Set<TrackNode> values = this.editedNodesByAnimationName.get(state.name);
                if (editing && values.add(node)) {
                    this.editedAnimationNamesChanged |= (values.size() == 1);
                } else if (!editing && values.remove(node)) {
                    this.editedAnimationNamesChanged |= values.isEmpty();
                }
            }
            */

            this.lastEditedTrackObject = object;
            this.lastEditTrackObjectTime = System.currentTimeMillis();
            this.changed = true;
        }
    }

    public boolean isEditing(TrackNode node) {
        return this.editedNodes.containsKey(node);
    }

    public boolean isEditingTrackObject(TrackObject object) {
        return this.editedTrackObjects.containsKey(object);
    }

    /**
     * Gets whether the player is sneaking (holding shift), indicating special modifiers are active.
     * Also checks whether the player is holding down the same key while holding the editor map.
     * 
     * @return True if sneaking
     */
    public boolean isSneaking() {
        if (this.player.isSneaking()) {
            return true;
        }
        TCCoastersDisplay display = MapDisplay.getHeldDisplay(this.player, TCCoastersDisplay.class);
        if (display != null && display.getInput(this.player).isPressed(MapPlayerInput.Key.BACK)) {
            return true;
        }
        return false;
    }

    public boolean onLeftClick() {
        if (this.getMode() == PlayerEditMode.OBJECT) {
            return this.onLeftClickTrackObject();
        } else {
            return this.onLeftClickOther();
        }
    }

    private boolean onLeftClickTrackObject() {
        // Single object selection mode
        if (!this.isSneaking()) {
            clearEditedTrackObjects();
        }

        // Find point on path clicked
        TrackConnection.PointOnPath point = this.getWorld().getTracks().findPointOnPath(this.player.getEyeLocation());
        if (point == null) {
            return true;
        }

        // Check for objects on this connection that are very close to the point clicked
        TrackObject bestObject = null;
        double bestDistance = 4.0; // within 4.0 distance to select it (TODO: larger wiggle room when further away?)
        for (TrackObject object : point.connection.getObjects()) {
            double distance = Math.abs(object.getDistance() - point.distance);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestObject = object;
            }
        }
        if (bestObject == null) {
            return true;
        }

        long lastEditTime = getLastEditTime(bestObject);
        if (lastEditTime > 300) {
            // Toggle edit state
            if (isEditingTrackObject(bestObject)) {
                setEditingTrackObject(point.connection, bestObject, false);
            } else {
                selectTrackObject(point.connection, bestObject);
            }
        } else {
            // Mass-selection mode
            if (this.isSneaking()) {
                // Select all objects between the clicked object and the nearest other selected track object
                this.floodSelectNearest(point.connection, bestObject);
            } else {
                // Flood-fill select all nodes connected from bestObject
                this.floodSelectTrackObjects(point.connection, bestObject);
            }
        }
        return true;
    }

    private boolean onLeftClickOther() {
        // When holding right-click and clicking left-click in position mode, create a new node and drag that
        if (this.getMode() == PlayerEditMode.POSITION && this.isHoldingRightClick()) {
            Vector pos;
            if (this.getEditedNodes().size() == 1) {
                pos = this.getEditedNodes().iterator().next().getPosition();
            } else {
                pos = this.getNewNodePos();
            }
            try {
                this.createNewNode(pos, null, false);
            } catch (ChangeCancelledException e) {
                // Do nothing, the left click simply didn't do anything.
            }
            return true;
        }

        // Create an inverted camera transformation of the player's view direction
        Matrix4x4 cameraTransform = new Matrix4x4();
        cameraTransform.translateRotate(player.getEyeLocation());
        cameraTransform.invert();

        // Go by all track nodes on the server, and pick those close in view on the same world
        // The transformed point is a projective view of the Minecart in the player's vision
        // X/Y is left-right/up-down and Z is depth after the transformation is applied
        TrackNode bestNode = null;
        TrackConnection bestJunction = null;
        double bestDistance = Double.MAX_VALUE;

        for (TrackCoaster coaster : getWorld().getTracks().getCoasters()) {
            for (TrackNode node : coaster.getNodes()) {
                // Node itself
                {
                    double distance = node.getViewDistance(cameraTransform);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestNode = node;
                        bestJunction = null;
                    }
                }

                // If node has multiple junctions, check if the player is clicking on any of the junction nodes
                // This follows very precise rules
                if (node.getConnections().size() > 2) {
                    for (TrackConnection conn : node.getConnections()) {
                        double distance = node.getJunctionViewDistance(cameraTransform, conn);
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            bestNode = node;
                            bestJunction = conn;
                        }
                    }
                }
            }
        }

        if (bestNode == null) {
            if (!this.isSneaking()) {
                clearEditedNodes();
            }
            return false;
        }

        // Switch junction when clicking on one
        if (bestJunction != null) {
            bestNode.switchJunction(bestJunction);
            return true;
        }

        long lastEditTime = getLastEditTime(bestNode);
        if (lastEditTime > 300) {
            // Single node selection mode
            if (!this.isSneaking()) {
                clearEditedNodes();
            }

            // Toggle edit state
            if (isEditing(bestNode)) {
                setEditing(bestNode, false);
            } else {
                selectNode(bestNode);
            }

        } else if (isEditing(bestNode)) {
            // Mass-selection mode
            if (this.isSneaking()) {
                // Select all nodes between the clicked node and the nearest other selected node
                this.floodSelectNearest(bestNode);
            } else {
                // Flood-fill select all nodes connected from bestNode
                this.floodSelect(bestNode);
            }
        }

        return true;
    }

    /**
     * Selects a node and all nodes connected to it, recursively.
     * This will select an entire coaster or piece of track, for example.
     * Any previous selection is cleared.
     * 
     * @param startNode to flood select from
     */
    public void floodSelect(TrackNode startNode) {
        this.clearEditedNodes();

        List<TrackNode> pending = new ArrayList<TrackNode>(2);
        pending.add(startNode);

        while (!pending.isEmpty()) {
            TrackNode node = pending.remove(0);
            selectNode(node);
            for (TrackNode neighbour : node.getNeighbours()) {
                if (!isEditing(neighbour)) {
                    pending.add(neighbour);
                }
            }
        }
    }

    /**
     * Selects all the track objects of all connections accessible from a connection, recursively.
     * This will select all the objects of an entire coaster or piece of track, for example.
     * Any previous selection is cleared.
     * 
     * @param startConnection
     * @param startObject
     */
    public void floodSelectTrackObjects(TrackConnection startConnection, TrackObject startObject) {
        this.clearEditedTrackObjects();

        List<TrackConnection> pending = new ArrayList<TrackConnection>();
        pending.add(startConnection);
        HashSet<TrackConnection> processed = new HashSet<TrackConnection>(pending);
        while (!pending.isEmpty()) {
            int size = pending.size();
            for (int i = 0; i < size; i++) {
                TrackConnection connection = pending.get(i);
                for (TrackObject object : connection.getObjects()) {
                    this.selectTrackObject(connection, object);
                }
                for (TrackConnection neighbour : connection.getNodeA().getConnections()) {
                    if (processed.add(neighbour)) {
                        pending.add(neighbour);
                    }
                }
                for (TrackConnection neighbour : connection.getNodeB().getConnections()) {
                    if (processed.add(neighbour)) {
                        pending.add(neighbour);
                    }
                }
            }
            pending.subList(0, size).clear();
        }
    }

    /**
     * Selects all the track objects of all connections accessible from the start connection,
     * that are between the startObject and the closest other selected track object.
     * The selected track objects are added and the previous selection is kept.
     * 
     * @param startConnection
     * @param startObject
     */
    public void floodSelectNearest(TrackConnection startConnection, TrackObject startObject) {
        // If there is a selected track object with the same connection, then we can optimize this
        // This is technically incorrect, because we could be at the end/start of the connection, with
        // another selected object on a connection next to it. For now, this is good enough, really.
        // Normally people only select a single object prior to flood-selecting anyway.
        {
            TrackObject closestObjectOnSameConnection = null;
            double closestDistance = Double.MAX_VALUE;
            for (PlayerEditTrackObject editObject : this.editedTrackObjects.values()) {
                if (editObject.connection == startConnection && editObject.object != startObject) {
                    double distance = Math.abs(startObject.getDistance() - editObject.object.getDistance());
                    if (distance < closestDistance) {
                        closestDistance = distance;
                        closestObjectOnSameConnection = editObject.object;
                    }
                }
            }
            if (closestObjectOnSameConnection != null) {
                // Add all objects in between the distance range
                double d_from = Math.min(startObject.getDistance(), closestObjectOnSameConnection.getDistance());
                double d_to   = Math.max(startObject.getDistance(), closestObjectOnSameConnection.getDistance());
                for (TrackObject object : startConnection.getObjects()) {
                    if (object.getDistance() >= d_from && object.getDistance() <= d_to) {
                        this.selectTrackObject(startConnection, object);
                    }
                }
                return; // done.
            }
        }

        // Make use of the node flood selecting, using the nodes from the objects we have selected
        // For start node, we pick the node closest to the start object
        List<PlayerEditTrackObject> editedTrackObjects = new ArrayList<PlayerEditTrackObject>(this.editedTrackObjects.values());
        HashSet<TrackNode> nodesOfTrackObjects = new HashSet<TrackNode>();
        for (PlayerEditTrackObject editObject : editedTrackObjects) {
            if (editObject.object != startObject) {
                nodesOfTrackObjects.add(editObject.connection.getNodeA());
                nodesOfTrackObjects.add(editObject.connection.getNodeB());
            }
        }
        boolean searchRight = (startObject.getDistance() >= 0.5 * startConnection.getFullDistance());
        TrackNode searchStart = searchRight ? startConnection.getNodeB() : startConnection.getNodeA();
        TrackNodeSearchPath bestPath = TrackNodeSearchPath.findShortest(searchStart, nodesOfTrackObjects);

        // Now do stuff with the found path, if found
        if (bestPath != null) {
            // If best path contains the other node of the start connection, then fill select to the right
            // Otherwise, select all objects to the left of the start object
            boolean searchWentRight = searchRight == (bestPath.path.contains(startConnection.getOtherNode(searchStart)));
            this.selectTrackObjectsBeyondDistance(startConnection, searchWentRight, startObject.getDistance());
            bestPath.pathConnections.remove(startConnection);

            // The current node is one that has a connection with one of the track objects we had selected
            // We need to find the connection of the node which is closest to it
            TrackConnection bestConnection = null;
            double bestObjectDistance = 0.0;
            boolean bestObjectDirection = false;
            double bestObjectDistanceToEnd = Double.MAX_VALUE;
            for (PlayerEditTrackObject editObject : editedTrackObjects) {
                if (editObject.object == startObject) {
                    continue;
                }

                boolean direction = (editObject.connection.getNodeA() == bestPath.current);
                if (!direction && editObject.connection.getNodeB() != bestPath.current) {
                    continue;
                }

                double distance = editObject.object.getDistance();
                if (!direction) {
                    distance = editObject.connection.getFullDistance() - distance;
                }
                if (distance < bestObjectDistanceToEnd) {
                    bestObjectDistanceToEnd = distance;
                    bestConnection = editObject.connection;
                    bestObjectDistance = editObject.object.getDistance();
                    bestObjectDirection = direction;
                }
            }
            if (bestConnection != null) {
                bestPath.pathConnections.remove(bestConnection);
                this.selectTrackObjectsBeyondDistance(bestConnection, bestObjectDirection, bestObjectDistance);
            }

            // Add all objects on connections in-between
            for (TrackConnection connection : bestPath.pathConnections) {
                for (TrackObject object : connection.getObjects()) {
                    this.selectTrackObject(connection, object);
                }
            }
        }
    }

    private void selectTrackObjectsBeyondDistance(TrackConnection connection, boolean direction, double distance) {
        for (TrackObject object : connection.getObjects()) {
            if (direction ? (object.getDistance() <= distance) :
                            (object.getDistance() >= distance)
            ) {
                this.selectTrackObject(connection, object);
            }
        }
    }

    /**
     * Attempts to find the closest node that is also selected, but not the start node,
     * and selects the nodes between them. The selected nodes are added and the previous
     * selection is kept.
     * 
     * @param startNode
     */
    public void floodSelectNearest(TrackNode startNode) {
        // Find shortest path from startNode to any other nodes being edited
        TrackNodeSearchPath bestPath = TrackNodeSearchPath.findShortest(startNode, this.getEditedNodes());

        // Now do stuff with the found path, if found
        if (bestPath != null) {
            for (TrackNode node : bestPath.path) {
                this.selectNode(node);
            }
        }
    }

    public void setTargetedBlock(Block clickedBlock, BlockFace clickedFace) {
        this.targetedBlock = clickedBlock;
        this.targetedBlockFace = clickedFace;
    }

    public boolean onRightClick() {
        this.input.click();
        return true;
    }

    public boolean isHoldingRightClick() {
        return this.input.hasInput() && this.plugin.isHoldingEditTool(this.player);
    }

    public void update() {
        if (this.isHoldingRightClick()) {
            this.input.update();
            this.changed = true;
            if (this.input.heldDuration() >= EDIT_AUTO_TIMEOUT) {
                try {
                    if (this.heldDownTicks == 0 || this.editMode.autoActivate(this.heldDownTicks)) {
                        this.updateEditing();
                    }
                    this.heldDownTicks++;
                } catch (ChangeCancelledException ex) {
                    this.clearEditedNodes();
                }
            }
        } else {
            if (this.heldDownTicks > 0) {
                try {
                    this.onEditingFinished();
                } catch (ChangeCancelledException e) {
                    this.clearEditedNodes();
                }
                this.heldDownTicks = 0;
                this.targetedBlock = null;
            }
            if (this.afterEditMode != null) {
                this.setMode(this.afterEditMode);
            }
        }
        if (this.editedAnimationNamesChanged) {
            this.editedAnimationNamesChanged = false;
            this.onEditedAnimationNamedChanged();
        }
    }

    private void updateEditing() throws ChangeCancelledException {
        if (this.editMode == PlayerEditMode.CREATE) {
            // Create new tracks
            createTrack();
        } else if (this.editMode == PlayerEditMode.DELETE) {
            // Delete tracks
            deleteTrack();
        } else if (this.editMode == PlayerEditMode.RAILS) {
            // Set rails block to the block clicked
            setRailBlock();
        } else if (this.editMode == PlayerEditMode.OBJECT) {
            // Create track objects on the track
            createTrackObject();
        } else {
            // Position / Orientation logic
            changePositionOrientation();
        }
    }

    /**
     * Deletes all selected tracks and selects the track that is connected
     * to the now-deleted tracks. This allows repeated deletion to 'walk' and delete.
     */
    public void deleteTrack() throws ChangeCancelledException {
        // Deselect nodes we cannot delete or modify
        this.deselectLockedNodes();

        // Track all changes
        HistoryChange changes = this.getHistory().addChangeGroup();

        // Defensive copy
        HashSet<TrackNode> toDelete = new HashSet<TrackNode>(this.getEditedNodes());

        // Disconnect nodes when adjacent nodes are selected
        boolean disconnectedNodes = false;
        for (TrackNode node : toDelete) {
            for (TrackConnection connection : node.getConnections()) {
                TrackNode neigh = connection.getOtherNode(node);
                if (toDelete.contains(neigh)) {
                    changes.addChangeDisconnect(this.player, connection);
                    getWorld().getTracks().disconnect(node, neigh);
                    removeConnectionForAnimationStates(node, neigh);
                    removeConnectionForAnimationStates(neigh, node);
                    disconnectedNodes = true;
                }
            }
        }
        if (disconnectedNodes) {
            // Clean up empty nodes
            for (TrackNode node : toDelete) {
                if (node.getConnections().isEmpty()) {
                    this.setEditing(node, false);
                    changes.addChangeDeleteNode(this.player, node);
                    node.remove();
                }
            }
            return;
        }

        // Backup all nodes to delete, then select all unselected neighbours of those nodes
        this.clearEditedNodes();
        for (TrackNode node : toDelete) {
            for (TrackNode neighbour : node.getNeighbours()) {
                if (!toDelete.contains(neighbour)) {
                    this.selectNode(neighbour);
                }
            }
        }

        // Delete all nodes to be deleted
        for (TrackNode node : toDelete) {
            changes.addChangeDeleteNode(this.player, node);
            node.remove();
        }
    }

    /**
     * Resets the rails blocks of all selected nodes, causing them to be
     * the same as the position of the rails node itself.
     */
    public void resetRailsBlocks() throws ChangeCancelledException {
        setRailBlock(null);
    }

    /**
     * Sets the rails block to what the player last right-clicked,
     * or otherwise to where the player is looking.
     */
    public void setRailBlock() throws ChangeCancelledException {
        // New rails block to use
        IntVector3 new_rail;
        if (this.targetedBlock != null) {
            new_rail = new IntVector3(this.targetedBlock);
        } else {
            Location eyeLoc = this.player.getEyeLocation();
            Vector pos = eyeLoc.toVector().add(eyeLoc.getDirection().multiply(1.5));
            new_rail = new IntVector3(pos.getBlockX(), pos.getBlockY(), pos.getBlockZ());
        }
        setRailBlock(new_rail, this.player.isSneaking());
    }

    /**
     * Sets the rails block to the rail block coordinates specified
     * 
     * @param new_rail to set to, null to reset
     */
    public void setRailBlock(IntVector3 new_rail) throws ChangeCancelledException {
        setRailBlock(new_rail, false);
    }

    /**
     * Sets the rails block to the rail block coordinates specified
     * 
     * @param new_rail to set to, null to reset
     * @param relative whether the rail blocks of all nodes should maintain the same relative positions
     */
    public void setRailBlock(IntVector3 new_rail, boolean relative) throws ChangeCancelledException {
        if (new_rail == null) {
            // Reset
            this.transformRailBlock((rail) -> { return null; });
        } else if (relative && this.editedNodes.size() > 1) {
            // Just in case, check for NPE
            if (lastEdited == null) {
                return;
            }

            // Use last selected node as a basis for the movement
            IntVector3 diff = new_rail.subtract(lastEdited.getRailBlock(true));
            if (diff.equals(IntVector3.ZERO)) {
                return; // No changes
            }

            // Offset all edited nodes by diff
            this.transformRailBlock((rail) -> { return rail.add(diff); });
        } else {
            // Absolute set
            this.transformRailBlock((rail) -> { return new_rail; });
        }
    }

    private void setRailForNode(HistoryChangeCollection changes, TrackNode node, IntVector3 new_rail) throws ChangeCancelledException {
        IntVector3 old_rail = node.getRailBlock(false);
        if (LogicUtil.bothNullOrEqual(old_rail, new_rail)) {
            return;
        }

        changes.addChangeBeforeSetRail(this.player, node, null);
        node.setRailBlock(new_rail);
        try {
            changes.handleChangeAfterSetRail(this.player, node, old_rail);
        } catch (ChangeCancelledException ex) {
            node.setRailBlock(old_rail);
            throw ex;
        }

        TrackNodeAnimationState animState = node.findAnimationState(this.selectedAnimation);
        if (animState != null) {
            // Refresh selected animation state of node too, if one is selected for this node
            // Leave other animation states alone
            node.setAnimationState(animState.name, animState.state.changeRail(new_rail), animState.connections);
        } else {
            // No animation is selected for this node, presume the rail block should be updated for all animation states
            for (TrackNodeAnimationState state : node.getAnimationStates()) {
                node.setAnimationState(state.name, state.state.changeRail(new_rail), state.connections);
            }
        }
    }

    /**
     * Transforms the rail blocks of all selected nodes using a transformation function
     * 
     * @param manipulator The function that transforms the input block coordinates
     * @throws ChangeCancelledException
     */
    public void transformRailBlock(Function<IntVector3, IntVector3> manipulator) throws ChangeCancelledException {
        // Deselect nodes we cannot edit
        this.deselectLockedNodes();

        if (this.editedNodes.size() == 1) {
            // Simplified
            TrackNode node = this.editedNodes.keySet().iterator().next();
            setRailForNode(this.getHistory(), node, manipulator.apply(node.getRailBlock(true)));
        } else if (!this.editedNodes.isEmpty()) {
            // Set the same rails block for all selected nodes and save as a single change
            HistoryChange changes = this.getHistory().addChangeGroup();
            for (TrackNode node : this.getEditedNodes()) {
                setRailForNode(changes, node, manipulator.apply(node.getRailBlock(true)));
            }
        }
    }

    /**
     * Transforms the position of all selected nodes using a transformation function
     * 
     * @param manipulator The function that transforms the input Vector
     * @throws ChangeCancelledException
     */
    public void transformPosition(Consumer<Vector> manipulator) throws ChangeCancelledException {
        // Deselect locked nodes that we cannot edit
        this.deselectLockedNodes();

        HistoryChange changes = null;
        for (TrackNode node : this.getEditedNodes()) {
            if (changes == null) {
                changes = this.getHistory().addChangeGroup();
            }

            changes.handleChangeBefore(this.player, node);
            TrackNodeState startState = node.getState();
            Vector pos = node.getPosition().clone();
            manipulator.accept(pos);
            node.setPosition(pos);
            changes.addChangeAfterChangingNode(this.player, node, startState);

            // Refresh selected animation state of node too, if one is selected for this node
            TrackNodeAnimationState animState = node.findAnimationState(this.selectedAnimation);
            if (animState != null) {
                node.setAnimationState(animState.name, animState.state.changePosition(pos), animState.connections);
            }
        }
    }

    /**
     * Sets the orientation for all selected nodes
     * 
     * @param orientation vector to set to
     */
    public void setOrientation(Vector orientation) throws ChangeCancelledException {
        // Deselect locked nodes that we cannot edit
        this.deselectLockedNodes();

        // Apply to all nodes
        HistoryChange changes = null;
        for (TrackNode node : this.getEditedNodes()) {
            if (changes == null) {
                changes = this.getHistory().addChangeGroup();
            }

            changes.handleChangeBefore(this.player, node);
            TrackNodeState startState = node.getState();
            node.setOrientation(orientation);
            changes.addChangeAfterChangingNode(this.player, node, startState);

            // Refresh selected animation state of node too, if one is selected for this node
            TrackNodeAnimationState animState = node.findAnimationState(this.selectedAnimation);
            if (animState != null) {
                node.setAnimationState(animState.name, animState.state.changeOrientation(orientation), animState.connections);
            }
        }
    }

    /**
     * Creates a new track node, connecting with already selected track nodes.
     * The created track node is selected to allow chaining create node calls.
     */
    public void createTrack() throws ChangeCancelledException {
        // For the first click, check if the player is looking exactly at a particular connection or node
        // If so, create a node on this connection or node and switch mode to position adjustment
        // This allows for a kind of click-drag creation design
        // TODO: Find connection
        TrackWorld tracks = getWorld().getTracks();
        Location eyeLoc = getPlayer().getEyeLocation();
        boolean dragAfterCreate = false;
        Vector pos = null;
        Vector ori = null;
        if (this.heldDownTicks == 0) {
            TrackNode lookingAt = findLookingAt();
            if (lookingAt != null) {
                this.clearEditedNodes();
                this.selectNode(lookingAt);
                pos = lookingAt.getPosition().clone();

                // Node: introduce a tiny offset to pos, otherwise
                // the node can not be properly looked up again during undo/redo
                pos.setY(pos.getY() + 1e-5);

                dragAfterCreate = true;
            }
        }

        // If targeting a block face, create a node on top of this face
        if (this.targetedBlock != null) {
            // If the targeted block is a rails block, connect to one end of it
            RailType clickedRailType = RailType.getType(this.targetedBlock);
            if (clickedRailType != RailType.NONE) {
                // Load the path for the rails clicked
                RailState state = new RailState();
                state.setRailPiece(RailPiece.create(clickedRailType, this.targetedBlock));
                state.position().setLocation(clickedRailType.getSpawnLocation(this.targetedBlock, this.targetedBlockFace));
                state.position().setMotion(this.targetedBlockFace);
                state.initEnterDirection();
                RailPath path = state.loadRailLogic().getPath();

                // Select the best possible path end position
                RailPath.Position p1 = path.getStartPosition();
                RailPath.Position p2 = path.getEndPosition();
                p1.makeAbsolute(this.targetedBlock);
                p2.makeAbsolute(this.targetedBlock);
                RailPath.Position closest = (p1.distance(eyeLoc) < p2.distance(eyeLoc)) ? p1 : p2;
                pos = new Vector(closest.posX, closest.posY, closest.posZ);
            } else {
                // Find the spot on the block that was actually clicked
                TargetedBlockInfo info = TCCoastersUtil.rayTrace(this.player);
                if (info != null) {
                    // Find position (and correct clicked face if needed)
                    ori = FaceUtil.faceToVector(info.face);
                    pos = new Vector(info.block.getX() + info.position.getX(),
                                     info.block.getY() + info.position.getY(),
                                     info.block.getZ() + info.position.getZ());
                } else {
                    // Fallback
                    double f = 0.5 + TCCoastersUtil.OFFSET_TO_SIDE;
                    ori = FaceUtil.faceToVector(this.targetedBlockFace);
                    pos = new Vector(this.targetedBlock.getX() + 0.5 + f * this.targetedBlockFace.getModX(),
                                     this.targetedBlock.getY() + 0.5 + f * this.targetedBlockFace.getModY(),
                                     this.targetedBlock.getZ() + 0.5 + f * this.targetedBlockFace.getModZ());
                }
            }
        }

        // Create in front of the player by default
        boolean inAir = false;
        if (pos == null) {
            pos = eyeLoc.toVector().add(eyeLoc.getDirection().multiply(0.5));
            inAir = true;
        }

        // First check no track already exists at this position
        if (!dragAfterCreate && !tracks.findNodesNear(new ArrayList<TrackNode>(2), pos, 0.1).isEmpty()) {
            return;
        }

        // Create the node and set as editing
        createNewNode(pos, ori, inAir);

        // If drag mode, switch to POSITION mode and initiate the drag
        if (dragAfterCreate) {
            this.setMode(PlayerEditMode.POSITION);
            this.editStartTransform = this.input.get().clone();
            this.editRotInfo = this.editStartTransform.toVector();
            this.setAfterEditMode(PlayerEditMode.CREATE);
        }
    }

    /**
     * Creates a new track objects where the player is looking, or drags existing track objects around on the track
     * 
     * @throws ChangeCancelledException
     */
    public void createTrackObject() throws ChangeCancelledException {
        // Find point looked at
        TrackConnection.PointOnPath point = this.getWorld().getTracks().findPointOnPath(this.input.get());
        if (point == null) {
            return;
        }
        if (point.connection.isLocked()) {
            TCCoastersLocalization.LOCKED.message(this.player);
            return;
        }

        // Create new objects when none are selected
        if (this.editedTrackObjects.isEmpty()) {
            point.connection.addObject(new TrackObject(point.distance, this.getObjectState().getSelectedItem()));
            return;
        }

        // Deselect objects we cannot edit before proceeding
        this.deselectLockedTrackObjects();

        if (this.heldDownTicks == 0) {
            // First click: calculate the positions of the objects relative to the clicked point
            // If objects aren't accessible from the point, then their dragDistance is set to NaN

            // Reset all objects to NaN
            for (PlayerEditTrackObject editObject : this.editedTrackObjects.values()) {
                editObject.dragDistance = Double.NaN;
            }

            // First do the objects on the clicked connection itself
            Map<TrackObject, PlayerEditTrackObject> pending = new HashMap<TrackObject, PlayerEditTrackObject>(this.editedTrackObjects);
            for (TrackObject object : point.connection.getObjects()) {
                PlayerEditTrackObject editObject = pending.remove(object);
                if (editObject != null) {
                    editObject.dragDistance = object.getDistance() - point.distance;
                    editObject.dragDirection = (editObject.dragDistance >= 0.0);
                    if (!editObject.dragDirection) {
                        editObject.dragDistance = -editObject.dragDistance;
                    }
                }
            }

            // If there's more, perform discovery in the two directions from the point's connection
            // Do this stepwise, so in a loop we find the nearest distance ideally
            if (!pending.isEmpty()) {
                Set<TrackConnection> visited = new HashSet<TrackConnection>();
                TrackObjectDiscoverer left  = new TrackObjectDiscoverer(pending, visited, point.connection, false, point.distance);
                TrackObjectDiscoverer right = new TrackObjectDiscoverer(pending, visited, point.connection, true,  point.distance);
                while (!pending.isEmpty() && (left.next() || right.next()));
            }
        } else {
            // Successive clicks: move the objects to the point, making use of the relative dragDistance to do so
            moveTrackObjects(point, false);
            moveTrackObjects(point, true);
        }
    }

    // Helper class for createTrackObject() drag start logic
    private static class TrackObjectDiscoverer {
        public final Map<TrackObject, PlayerEditTrackObject> pending;
        public final Set<TrackConnection> visited;
        public final WalkingConnection connection;
        public boolean initialDirection;
        public double distance;

        public TrackObjectDiscoverer(Map<TrackObject, PlayerEditTrackObject> pending, Set<TrackConnection> visited, TrackConnection connection, boolean direction, double pointDistance) {
            this.pending = pending;
            this.visited = visited;
            this.connection = new WalkingConnection(connection, direction);
            this.initialDirection = direction;
            this.distance = direction ? (connection.getFullDistance() - pointDistance) : pointDistance;
        }

        public boolean next() {
            // Next connection
            if (!this.connection.next()) {
                return false;
            }

            // If already visited, abort
            if (!this.visited.add(this.connection.connection)) {
                this.connection.connection = null;
                return false;
            }

            // Check all objects on connection
            for (TrackObject object : this.connection.connection.getObjects()) {
                PlayerEditTrackObject editObject = this.pending.remove(object);
                if (editObject != null) {
                    double objectDistance = object.getDistance();
                    if (!this.connection.direction) {
                        objectDistance = connection.getFullDistance() - objectDistance;
                    }
                    editObject.dragDirection = this.initialDirection;
                    editObject.dragDistance = this.distance + objectDistance;
                }
            }
            this.distance += this.connection.getFullDistance();
            return true;
        }
    }

    /// Moves selected track objects. Direction defines whether to walk to nodeA (false) or nodeB (true).
    private void moveTrackObjects(TrackConnection.PointOnPath point, boolean initialDirection) {
        // Create a sorted list of objects to move, with drag distance increasing
        // Only add objects with the same direction
        SortedSet<PlayerEditTrackObject> objects = new TreeSet<PlayerEditTrackObject>(
            (a, b) -> Double.compare(a.dragDistance, b.dragDistance)
        );
        for (PlayerEditTrackObject editObject : this.editedTrackObjects.values()) {
            if (editObject.dragDirection == initialDirection && !Double.isNaN(editObject.dragDistance)) {
                objects.add(editObject);
            }
        }
        if (objects.isEmpty()) {
            return; // none in this category
        }

        // Distance offset based on the point position on the clicked connection
        // This makes the maths easier, as we can just look from the start of the connection
        // This value is initially always a negative number (or 0)
        double distanceOffset = -(initialDirection ? point.distance : (point.connection.getFullDistance() - point.distance));

        // Proceed to walk down the connections relative to the point
        WalkingConnection connection = new WalkingConnection(point.connection, initialDirection);
        for (PlayerEditTrackObject object : objects) {
            while (true) {
                // Check if the object can fit within the remaining distance on the current connection
                double objectDistance = object.dragDistance - distanceOffset;
                if (objectDistance < connection.getFullDistance()) {
                    if (!connection.direction) {
                        objectDistance = connection.getFullDistance() - objectDistance;
                    }
                    object.connection.moveObject(object.object, connection.connection, objectDistance);
                    object.connection = connection.connection;
                    break; // done!
                }

                distanceOffset += connection.getFullDistance();
                if (!connection.next()) {
                    return; // end reached
                }
            }
        }
    }

    /**
     * Tracks a Connection that can walk in either direction towards the next connection
     * of the chain.
     */
    private static class WalkingConnection {
        public TrackConnection connection;
        public boolean direction;

        public WalkingConnection(TrackConnection connection, boolean direction) {
            this.connection = connection;
            this.direction = direction;
        }

        public double getFullDistance() {
            return this.connection.getFullDistance();
        }

        public boolean next() {
            if (this.connection == null) {
                return false;
            }

            // Pick next connection. We don't really support junctions that well.
            TrackNode next = this.direction ? this.connection.getNodeB() : this.connection.getNodeA();
            List<TrackConnection> nextConnections = next.getConnections();
            if (nextConnections.size() <= 1) {
                this.connection = null;
                return false;
            } else {
                this.connection = (nextConnections.get(0) == this.connection) ? nextConnections.get(1) : nextConnections.get(0);
                this.direction = (this.connection.getNodeA() == next);
                return true;
            }
        }
    }

    /**
     * Deletes all selected track objects
     * 
     * @throws ChangeCancelledException
     */
    public void deleteTrackObjects() throws ChangeCancelledException {
        if (this.editedTrackObjects.isEmpty()) {
            return;
        }

        List<PlayerEditTrackObject> objects = new ArrayList<PlayerEditTrackObject>(this.editedTrackObjects.values());
        this.editedTrackObjects.clear();
        for (PlayerEditTrackObject object : objects) {
            object.connection.removeObject(object.object);
        }
    }

    private Vector getNewNodePos() {
        Location eyeLoc = getPlayer().getEyeLocation();
        return eyeLoc.toVector().add(eyeLoc.getDirection().multiply(0.5));
    }

    private void createNewNode(Vector pos, Vector ori, boolean inAir) throws ChangeCancelledException {
        TrackWorld tracks = getWorld().getTracks();

        // Deselect nodes that are locked, to prevent modification
        this.deselectLockedNodes();

        // Not editing any new nodes, create a completely new coaster and node
        if (!this.hasEditedNodes()) {
            TrackNode newNode = tracks.createNew(pos).getNodes().get(0);
            if (ori != null) {
                newNode.setOrientation(ori);
            }
            this.getHistory().addChangeCreateNode(this.player, newNode);
            this.selectNode(newNode);
            return;
        }

        // Track all changes
        HistoryChange changes = this.getHistory().addChangeGroup();

        // First, find all track connections that have been selected thanks
        // to both nodes being selected for editing right now.
        HashSet<TrackConnection> selectedConnections = new HashSet<TrackConnection>();
        for (TrackNode node : getEditedNodes()) {
            for (TrackConnection conn : node.getConnections()) {
                if (this.getEditedNodes().contains(conn.getOtherNode(node))) {
                    selectedConnections.add(conn);
                }
            }
        }

        // Insert a new node in the middle of all selected connections
        // Select these created nodes
        List<TrackNode> createdConnNodes = new ArrayList<TrackNode>();
        for (TrackConnection conn : selectedConnections) {
            Vector newNodePos = conn.getPath().getPosition(0.5);
            Vector newNodeOri = conn.getOrientation(0.5);

            this.setEditing(conn.getNodeA(), false);
            this.setEditing(conn.getNodeB(), false);

            changes.addChangeDisconnect(this.player, conn);
            conn.remove();

            TrackNode newNode = conn.getNodeA().getCoaster().createNewNode(newNodePos, newNodeOri);
            changes.addChangeCreateNode(this.player, newNode);

            changes.addChangeConnect(this.player, tracks.connect(conn.getNodeA(), newNode));
            changes.addChangeConnect(this.player, tracks.connect(newNode, conn.getNodeB()));
            createdConnNodes.add(newNode);
        }

        // When clicking on a block or when only having one node selected, create a new node
        // When more than one node is selected and the air is clicked, simply connect the selected nodes together
        if (this.editedNodes.size() >= 2 && inAir) {
            // Connect all remaining edited nodes together in the most logical fashion
            // From previous logic we already know these nodes do not share connections among them
            // This means a single 'line' must be created that connects all nodes together
            // While we can figure this out using a clever algorithm, the nodes are already sorted in the
            // order the player clicked them. Let's give the player some control back, and connect them in the
            // same order.
            TrackNode prevNode = null;
            for (TrackNode node : getEditedNodes()) {
                if (prevNode != null) {
                    changes.addChangeConnect(this.player, tracks.connect(prevNode, node));
                    addConnectionForAnimationStates(prevNode, node);
                    addConnectionForAnimationStates(node, prevNode);
                }
                prevNode = node;
            }
        } else {
            // Add a new node connecting to existing selected nodes
            TrackNode newNode = null;
            for (TrackNode node : getEditedNodes()) {
                if (newNode == null) {
                    newNode = tracks.addNode(node, pos);
                    if (ori != null) {
                        newNode.setOrientation(ori);
                    }
                    changes.addChangeCreateNode(this.player, newNode);
                    changes.addChangeConnect(this.player, newNode, node);
                } else {
                    changes.addChangeConnect(this.player, tracks.connect(node, newNode));
                }
                addConnectionForAnimationStates(node, newNode);
            }
            clearEditedNodes();
            if (newNode != null) {
                setEditing(newNode, true);
            }
        }

        // Set all new nodes we created as editing, too.
        for (TrackNode node : createdConnNodes) {
            setEditing(node, true);
        }
    }

    public void changePositionOrientation() {
        // Deselect locked nodes that we cannot edit
        this.deselectLockedNodes();

        if (!this.hasEditedNodes()) {
            return;
        }

        // Get current input
        Matrix4x4 current = this.input.get();

        // First click?
        if (this.editStartTransform == null || this.heldDownTicks == 0) {
            this.editStartTransform = current.clone();
            this.editRotInfo = this.editStartTransform.toVector();

            // Store initial positions
            for (PlayerEditNode editNode : this.editedNodes.values()) {
                editNode.dragPosition = editNode.node.getPosition().clone();
            }

            // Is used to properly alter the orientation of a node looked at
            TrackNode lookingAt = this.findLookingAt();
            if (lookingAt != null) {
                Vector forward = this.editStartTransform.getRotation().forwardVector();
                double distanceTo = lookingAt.getPosition().distance(this.editRotInfo);
                this.editRotInfo.add(forward.multiply(distanceTo));
            }
        }

        // Ensure moveBegin() is called once
        List<TrackNode> cancelled = Collections.emptyList();
        for (PlayerEditNode editNode : this.editedNodes.values()) {
            if (!editNode.moveBegin(this.player)) {
                if (cancelled.isEmpty()) {
                    cancelled = new ArrayList<TrackNode>();
                }
                cancelled.add(editNode.node);
            }
        }
        for (TrackNode cancelledNode : cancelled) {
            this.setEditing(cancelledNode, false);
        }
        if (!this.hasEditedNodes()) {
            return;
        }

        // Calculate the transformation performed as a result of the player 'looking'
        Matrix4x4 changes = new Matrix4x4();
        changes.multiply(current);

        { // Divide
            Matrix4x4 m = this.editStartTransform.clone();
            m.invert();
            changes.multiply(m);
        }

        if (this.getMode() == PlayerEditMode.ORIENTATION) {
            changes.transformPoint(this.editRotInfo);
            for (PlayerEditNode editNode : this.editedNodes.values()) {
                editNode.node.setOrientation(this.editRotInfo.clone().subtract(editNode.node.getPosition()));
            }
        } else {
            Vector eyePos = this.player.getEyeLocation().toVector();
            for (PlayerEditNode editNode : this.editedNodes.values()) {
                // Recover null
                if (editNode.dragPosition == null) {
                    editNode.dragPosition = editNode.node.getPosition().clone();
                }

                // Transform position and compute direction using player view position relative to the node
                changes.transformPoint(editNode.dragPosition);
                Vector position = editNode.dragPosition.clone();
                Vector orientation = editNode.startState.orientation.clone();
                Vector direction = position.clone().subtract(this.player.getEyeLocation().toVector()).normalize();
                if (Double.isNaN(direction.getX())) {
                    direction = this.player.getEyeLocation().getDirection();
                }

                // Snap position against the side of a block
                // Then, look for other rails blocks and attach to it
                // When sneaking, disable this functionality
                // When more than 1 node is selected, only do this for nodes with 1 or less connections
                // This is to avoid severe performance problems when moving a lot of track at once
                if (!this.isSneaking() && (this.editedNodes.size() == 1 || editNode.node.getConnections().size() <= 1)) {
                    TCCoastersUtil.snapToBlock(getBukkitWorld(), eyePos, position, orientation);

                    if (TCCoastersUtil.snapToCoasterRails(editNode.node, position, orientation)) {
                        // Play particle effects to indicate we are snapping to the coaster rails
                        PlayerUtil.spawnDustParticles(this.player, position, Color.RED);
                    } else if (TCCoastersUtil.snapToRails(getBukkitWorld(), editNode.node.getRailBlock(true), position, direction, orientation)) {
                        // Play particle effects to indicate we are snapping to the rails
                        PlayerUtil.spawnDustParticles(this.player, position, Color.PURPLE);
                    }
                }

                // Apply to node
                editNode.node.setPosition(position);
                editNode.node.setOrientation(orientation);
            }
        }

        this.editStartTransform = current.clone();
    }

    // when the list of animations in selected nodes changes
    private void onEditedAnimationNamedChanged() {
        for (TCCoastersDisplay display : MapDisplay.getAllDisplays(TCCoastersDisplay.class)) {
            display.sendStatusChange("PlayerEditState::EditedAnimationNamesChanged");
        }
    }

    // when player releases the right-click mouse button
    private void onEditingFinished() throws ChangeCancelledException {
        // Deselect locked nodes that we cannot edit
        this.deselectLockedNodes();

        // When drag-dropping a node onto a node, 'merge' the two
        // Do so by connecting all other neighbours of the dragged node to the node
        if (this.getMode() == PlayerEditMode.POSITION && this.getEditedNodes().size() == 1) {
            TrackWorld tracks = getWorld().getTracks();
            PlayerEditNode draggedNode = this.editedNodes.values().iterator().next();
            TrackNode droppedNode = null;

            // If we never even began moving this node, do nothing
            if (!draggedNode.hasMoveBegun()) {
                return;
            }

            // Get all nodes nearby the position, sorted from close to far
            final Vector pos = draggedNode.node.getPosition();
            List<TrackNode> nearby = tracks.findNodesNear(new ArrayList<TrackNode>(), pos, 0.3);
            Collections.sort(nearby, new Comparator<TrackNode>() {
                @Override
                public int compare(TrackNode o1, TrackNode o2) {
                    return Double.compare(o1.getPosition().distanceSquared(pos),
                                          o2.getPosition().distanceSquared(pos));
                }
            });

            // Pick first (closest) node that is not the node dragged
            for (TrackNode nearNode : nearby) {
                if (nearNode != draggedNode.node) {
                    droppedNode = nearNode;
                    break;
                }
            }

            // Merge if found
            if (droppedNode != null) {
                try {
                    List<TrackNode> connectedNodes = draggedNode.node.getNeighbours();

                    // Track all the changes we are doing down below.
                    HistoryChange changes = this.getHistory().addChangeAfterChangingNode(
                            this.player, draggedNode.node, draggedNode.startState);

                    // Delete dragged node
                    changes.addChangeDeleteNode(this.player, draggedNode.node);
                    draggedNode.node.remove();

                    // Connect all that was connected to it, with the one dropped on
                    for (TrackNode connected : connectedNodes) {
                        if (connected != droppedNode) {
                            changes.addChangeConnect(this.player, tracks.connect(droppedNode, connected));
                            addConnectionForAnimationStates(droppedNode, connected);
                            addConnectionForAnimationStates(connected, droppedNode);
                        }
                    }

                    // Do not do the standard position/orientation change saving down below
                    return;
                } finally {
                    draggedNode.moveEnd();
                }
            }
        }

        // For position/orientation, store the changes
        if (this.isMode(PlayerEditMode.POSITION, PlayerEditMode.ORIENTATION)) {
            HistoryChange changes = this.getHistory().addChangeGroup();
            try {
                for (PlayerEditNode editNode : this.editedNodes.values()) {
                    if (editNode.hasMoveBegun()) {
                        changes.addChangeAfterChangingNode(this.player, editNode.node, editNode.startState);

                        // Update position and orientation of animation state, if one is selected
                        TrackNodeAnimationState animState = editNode.node.findAnimationState(this.selectedAnimation);
                        if (animState != null) {
                            editNode.node.setAnimationState(animState.name,
                                                            editNode.node.getState().changeRail(animState.state.railBlock),
                                                            animState.connections);
                        }
                    }
                }
            } finally {
                for (PlayerEditNode editNode : this.editedNodes.values()) {
                    editNode.moveEnd();
                }
            }
        }
    }

    /**
     * Adds connections to the target node for all animation states defined in the node
     * 
     * @param node
     * @param target
     */
    private void addConnectionForAnimationStates(TrackNode node, TrackNode target) {
        TrackNodeAnimationState animState = node.findAnimationState(this.selectedAnimation);
        if (animState != null) {
            // Only add connection for this one animation state
            node.addAnimationStateConnection(animState.name, new TrackPendingConnection(target));
        } else {
            // Add connection to all animation states
            node.addAnimationStateConnection(null, new TrackPendingConnection(target));
        }
    }

    /**
     * Removes connections to the target node for all animation states defined in the node
     * 
     * @param node
     * @param target
     */
    private void removeConnectionForAnimationStates(TrackNode node, TrackNode target) {
        TrackNodeAnimationState animState = node.findAnimationState(this.selectedAnimation);
        if (animState != null) {
            // Only add connection for this one animation state
            node.removeAnimationStateConnection(animState.name, new TrackPendingConnection(target));
        } else {
            // Add connection to all animation states
            node.removeAnimationStateConnection(null, new TrackPendingConnection(target));
        }
    }
}
