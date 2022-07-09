package com.bergerkiller.bukkit.coasters.editor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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
import com.bergerkiller.bukkit.coasters.tracks.TrackConnectionState;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeAnimationState;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeReference;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeSearchPath;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeSign;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeState;
import com.bergerkiller.bukkit.coasters.tracks.TrackWorld;
import com.bergerkiller.bukkit.coasters.world.CoasterWorld;
import com.bergerkiller.bukkit.coasters.world.CoasterWorldComponent;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.block.SignEditDialog;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.common.wrappers.ChatText;
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
    private CoasterWorld cachedCoasterWorld = null;
    private TrackNode lastEdited = null;
    private long lastEditTime = System.currentTimeMillis();
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
    private HistoryChange draggingCreateNewNodeChange = null; // used when player left-clicks while dragging a node

    public PlayerEditState(TCCoasters plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.input = new PlayerEditInput(player);
        this.history = new PlayerEditHistory(player);
        this.clipboard = new PlayerEditClipboard(this);
        this.objectState = new ObjectEditState(this);
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
            this.getObjects().load(config);
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
        this.getObjects().save(config);
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

    public PlayerEditInput getInput() {
        return this.input;
    }

    public ObjectEditState getObjects() {
        return this.objectState;
    }

    public long getLastEditTime(TrackNode node) {
        if (this.lastEdited != node) {
            return Long.MAX_VALUE;
        } else {
            return System.currentTimeMillis() - this.lastEditTime;
        }
    }

    public int getHeldDownTicks() {
        return this.heldDownTicks;
    }

    public void markChanged() {
        this.changed = true;
    }

    private void onEditedNodesChanged() {
        markChanged();

        // Tell the display the player has open, if applicable
        TCCoastersDisplay display = TCCoastersDisplay.getHeldDisplay(this.player, TCCoastersDisplay.class);
        if (display != null) {
            display.sendStatusChange("Editor::EditedNodes::Changed");
        }
    }

    public void clearEditedNodes() {
        if (!this.editedNodes.isEmpty()) {
            ArrayList<TrackNode> oldNodes = new ArrayList<TrackNode>(this.getEditedNodes());
            this.editedNodes.clear();
            this.editedAnimationNamesChanged |= !this.editedNodesByAnimationName.isEmpty();
            this.editedNodesByAnimationName.clear();
            this.lastEdited = null;
            this.onEditedNodesChanged();
            for (TrackNode oldNode : oldNodes) {
                oldNode.onStateUpdated(this.player);
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

    /**
     * Gets whether any of the nodes selected by the player are curved.
     *
     * @return True if any of the selected nodes have curved connections
     */
    public boolean hasCurvedConnectionTrackNodes() {
        return hasEditedNodes() && getEditedNodes().stream()
            .filter(n -> n.getConnections().size() == 2 && n.getZeroDistanceNeighbour() == null)
            .findAny().isPresent();
    }

    /**
     * Gets whether any of the nodes selected by the player are straight.
     *
     * @return True if any of the selected nodes have straight connections
     */
    public boolean hasStraightConnectionTrackNodes() {
        return hasEditedNodes() && getEditedNodes().stream()
            .filter(n -> n.getZeroDistanceNeighbour() != null)
            .findAny().isPresent();
    }

    /**
     * Gets a set of all nodes selected by the player that are being edited
     *
     * @return set of all edited nodes
     */
    public Set<TrackNode> getEditedNodes() {
        return this.editedNodes.keySet();
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

    /**
     * Deselects all nodes that we cannot actually edit, because the coaster they
     * are part of is locked.
     * 
     * @return True if nodes were deselected
     */
    public boolean deselectLockedNodes() {
        if (this.editedNodes.isEmpty()) {
            return false;
        }

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
        onEditedNodesChanged();

        if (hadLockedNodes) {
            this.lastEditTime = System.currentTimeMillis();
            this.markChanged();
            TCCoastersLocalization.LOCKED.message(this.player);
        }
        return hadLockedNodes;
    }

    public void setMode(PlayerEditMode mode) {
        this.afterEditMode = null;
        if (this.editMode != mode) {
            this.editMode = mode;
            this.markChanged();

            // Mode change may have changed what particles are visible
            getWorld().getParticles().scheduleViewerUpdate(this.player);
            getWorld().getParticles().update(this.player);

            // Refresh the nodes and their particles based on the mode
            for (TrackNode node : this.getEditedNodes()) {
                node.onStateUpdated(this.player);
            }

            // Tell object state too
            this.getObjects().onModeChanged();
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
            this.onEditedNodesChanged();
        }
    }

    public boolean isEditing(TrackNode node) {
        return this.editedNodes.containsKey(node);
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

    public void onSneakingChanged(boolean sneaking) {
        this.objectState.onSneakingChanged(sneaking);
    }

    public boolean onLeftClick() {
        if (this.getMode() == PlayerEditMode.OBJECT) {
            return this.objectState.onLeftClick();
        }

        // When holding right-click and clicking left-click in position mode, create a new node and drag that
        if (this.getMode() == PlayerEditMode.POSITION && this.isHoldingRightClick()) {
            // Complete moving the original selected node(s), call onEditingFinished
            try {
                this.onEditingFinished();
                this.heldDownTicks = 0;
            } catch (ChangeCancelledException e) {
                // Couldn't place the node(s) here, the change was aborted
                // This also aborts creating the new node to keep things sane
                this.clearEditedNodes();
                return true;
            }

            Vector pos;
            if (this.getEditedNodes().size() == 1) {
                pos = this.getEditedNodes().iterator().next().getPosition().clone();

                // We must make minor changes to the position so the node position is 'unique'
                // The findNodeExact function has an accuracy of 1e-6, increments of 1e-5 will be sufficient
                // Move the node closer/further from the player perspective, so at least it doesn't translate visibly
                // Use a poor man's randomness to either bring the point closer or further from the player
                // That way over the course of many clicks, the position shouldn't drift too badly
                Vector dir = pos.clone().subtract(this.player.getEyeLocation().toVector());
                {
                    double dir_lsq = dir.lengthSquared();
                    if (dir_lsq < 1e-5) {
                        dir = this.player.getEyeLocation().getDirection();
                    } else {
                        dir.multiply(MathUtil.getNormalizationFactorLS(dir_lsq));
                    }
                }
                if (Math.random() >= 0.5) {
                    dir.multiply(1e-5);
                } else {
                    dir.multiply(-1e-5);
                }
                pos.add(dir);
                while (this.getWorld().getTracks().findNodeExact(pos) != null) {
                    pos.add(dir);
                }
            } else {
                pos = this.getNewNodePos();
            }
            try {
                this.createNewNode(pos, null, false);
                this.draggingCreateNewNodeChange = this.getHistory().getLastChange();
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
        Set<TrackNode> bestNodes = new HashSet<TrackNode>();
        TrackConnection bestJunction = null;
        double bestDistance = Double.MAX_VALUE;

        // When in rail mode, look up using the rail block of the nodes as well
        // Ignore nodes that don't have a rail block set
        if (this.getMode() == PlayerEditMode.RAILS) {
            for (TrackCoaster coaster : getWorld().getTracks().getCoasters()) {
                for (TrackNode node : coaster.getNodes()) {
                    if (node.getRailBlock(false) != null) {
                        double distance = node.getRailBlockViewDistance(cameraTransform);
                        if (distance <= bestDistance && distance != Double.MAX_VALUE) {
                            // Multi-select the nodes when they match the same rail block
                            if (distance < bestDistance) {
                                bestNodes.clear();
                            }
                            bestNodes.add(node);
                            bestDistance = distance;
                            bestJunction = null;
                        }
                    }
                }
            }
        }

        // Clicking on a node, or a junction floating block particle
        for (TrackCoaster coaster : getWorld().getTracks().getCoasters()) {
            for (TrackNode node : coaster.getNodes()) {
                // Node itself
                {
                    double distance = node.getViewDistance(cameraTransform);
                    if (distance != Double.MAX_VALUE) {
                        double diff = (bestDistance - distance);
                        if (diff > 0.0 || (diff >= -1e-10 && !bestNodes.contains(node.getZeroDistanceNeighbour()))) {
                            bestNodes.clear();
                        }
                        if (diff >= -1e-10) {
                            bestNodes.add(node);
                            bestDistance = distance;
                            bestJunction = null;
                        }
                    }
                }

                // If node has multiple junctions, check if the player is clicking on any of the junction nodes
                // This follows very precise rules
                if (node.getConnections().size() > 2) {
                    for (TrackConnection conn : node.getConnections()) {
                        double distance = node.getJunctionViewDistance(cameraTransform, conn);
                        if (distance < bestDistance) {
                            bestNodes.clear();
                            bestNodes.add(node);
                            bestDistance = distance;
                            bestJunction = conn;
                        }
                    }
                }
            }
        }

        if (bestNodes.isEmpty()) {
            if (!this.isSneaking()) {
                clearEditedNodes();
            }
            return false;
        }

        // Switch junction when clicking on one
        if (bestJunction != null) {
            bestNodes.iterator().next().switchJunction(bestJunction);
            return true;
        }

        long currentTime = System.currentTimeMillis();
        long timeSinceLastEdit = bestNodes.contains(this.lastEdited) ?
                (currentTime - this.lastEditTime) : Long.MAX_VALUE;
        this.lastEditTime = currentTime;
        if (timeSinceLastEdit > 300) {
            // Single node selection mode
            if (!this.isSneaking()) {
                clearEditedNodes();
            }

            // Toggle edit state
            for (TrackNode bestNode : bestNodes) {
                if (this.getEditedNodes().containsAll(bestNodes)) {
                    setEditing(bestNode, false);
                } else {
                    selectNode(bestNode);
                }
            }

        } else if (this.getEditedNodes().size() > 1 && this.isSneaking()) {
            // Select all nodes between the clicked node and the nearest other selected node
            for (TrackNode bestNode : bestNodes) {
                this.floodSelectNearest(bestNode);
            }

        } else if (this.getEditedNodes().equals(bestNodes)) {
            // Flood-fill select all nodes connected from bestNode
            this.floodSelect(bestNodes);

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
        floodSelect(Collections.singleton(startNode));
    }

    /**
     * Selects nodes and all nodes connected to it, recursively.
     * This will select an entire coaster or piece of track, for example.
     * Any previous selection is cleared.
     * 
     * @param startNodes to flood select from
     */
    public void floodSelect(Collection<TrackNode> startNodes) {
        this.clearEditedNodes();

        List<TrackNode> pending = new ArrayList<TrackNode>(2);
        pending.addAll(startNodes);

        // Avoid infinite loops by never handling a node more than once
        Set<TrackNode> handled = new HashSet<TrackNode>(pending);

        while (!pending.isEmpty()) {
            TrackNode node = pending.remove(0);
            selectNode(node);
            for (TrackNode neighbour : node.getNeighbours()) {
                if (handled.add(neighbour) && !isEditing(neighbour)) {
                    pending.add(neighbour);
                }
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
        HashSet<TrackNode> selected = new HashSet<TrackNode>(this.getEditedNodes());
        selected.remove(startNode);
        TrackNodeSearchPath bestPath = TrackNodeSearchPath.findShortest(startNode, selected);

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
            this.markChanged();
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
        this.getObjects().update();
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
            this.objectState.createObject();
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
                    changes.addChangeBeforeDisconnect(this.player, connection);
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
                if (node.isUnconnectedNode()) {
                    this.setEditing(node, false);
                    changes.addChangeBeforeDeleteNode(this.player, node);
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
            changes.addChangeBeforeDeleteNode(this.player, node);
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
            this.transformRailBlock(rail -> null);
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
            this.transformRailBlock(rail -> rail.add(diff));
        } else {
            // Absolute set
            this.transformRailBlock(rail -> new_rail);
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

    public boolean onSignLeftClick(TrackNode node) {
        // If there are no signs, act as if the node wasn't clicked
        // This allows a player to still break the blocks behind a node
        TrackNodeSign[] signs = node.getSigns();
        if (signs.length == 0) {
            return false;
        }

        // Deny if blocked
        if (node.isLocked()) {
            TCCoastersLocalization.LOCKED.message(this.player);
            return false;
        }

        // Try to remove the last sign
        try {
            setSignsForNode(this.getHistory(), node, Arrays.copyOf(signs, signs.length - 1));
            updateSignInSelectedAnimationStates(node, signs[signs.length - 1], null);
        } catch (ChangeCancelledException e) {
            TCCoastersLocalization.SIGN_REMOVE_FAILED.message(player);
            return false;
        }

        TCCoastersLocalization.SIGN_REMOVE_SUCCESS.message(player);
        return true;
    }

    public boolean onSignRightClick(TrackNode node, ItemStack signItem) {
        // Deny if blocked
        if (node.isLocked()) {
            TCCoastersLocalization.LOCKED.message(this.player);
            return false;
        }

        final boolean appendToSign = player.isSneaking();
        new SignEditDialog() {
            @Override
            public void onClosed(Player player, String[] lines) {
                try {
                    if (appendToSign && node.getSigns().length > 0) {
                        // Append lines to last sign
                        TrackNodeSign[] new_signs = node.getSigns().clone();
                        TrackNodeSign old_sign = new_signs[new_signs.length - 1];
                        TrackNodeSign updated = old_sign.clone();
                        for (String line : lines) {
                            if (!line.isEmpty()) {
                                updated.appendLine(line);
                            }
                        }
                        new_signs[new_signs.length - 1] = updated;

                        // Update the signs
                        setSignsForNode(getHistory(), node, new_signs);;
                        updateSignInSelectedAnimationStates(node, old_sign, updated);
                        TCCoastersLocalization.SIGN_ADD_APPEND.message(player);
                    } else {
                        // Add a new sign to the node
                        addSignToNode(getHistory(), node, new TrackNodeSign(lines), true);
                        TCCoastersLocalization.SIGN_ADD_SUCCESS.message(player);
                    }
                } catch (ChangeCancelledException e) {
                    TCCoastersLocalization.SIGN_ADD_FAILED.message(player);
                }
            }
        }.open(player, findInitialLines(signItem));
        return true;
    }

    private static String[] findInitialLines(ItemStack signItem) {
        if (signItem != null) {
            try {
                CommonTagCompound nbt = ItemUtil.getMetaTag(signItem, false);
                if (nbt != null && nbt.containsKey("BlockEntityTag")) {
                    CommonTagCompound blockTag = nbt.createCompound("BlockEntityTag");
                    if (blockTag != null && "minecraft:sign".equals(blockTag.getValue("id", String.class))) {
                        return new String[] {
                                ChatText.fromJson(blockTag.getValue("Text1", "")).getMessage(),
                                ChatText.fromJson(blockTag.getValue("Text2", "")).getMessage(),
                                ChatText.fromJson(blockTag.getValue("Text3", "")).getMessage(),
                                ChatText.fromJson(blockTag.getValue("Text4", "")).getMessage()
                        };
                    }
                }
            } catch (Throwable t) {
                // Probably failed to decode something
                // Ignore. Corrupt item?
            }
        }

        return new String[] { "", "", "", "" };
    }

    public void clearSigns() throws ChangeCancelledException {
        // Deselect nodes we cannot edit
        this.deselectLockedNodes();

        HistoryChange changes = this.getHistory().addChangeGroup();
        for (TrackNode node : this.editedNodes.keySet()) {
            TrackNodeSign[] old_signs = node.getSigns();
            if (old_signs.length > 0) {
                setSignsForNode(changes, node, TrackNodeSign.EMPTY_ARR);
                for (TrackNodeSign sign : old_signs) {
                    updateSignInSelectedAnimationStates(node, sign, null);
                }
            }
        }
    }

    public void addSign(TrackNodeSign sign) throws ChangeCancelledException {
        // Deselect nodes we cannot edit
        this.deselectLockedNodes();

        // Failure if no nodes are selected
        if (this.editedNodes.isEmpty()) {
            return;
        }

        boolean firedEvent = false;
        HistoryChange changes = this.getHistory().addChangeGroup();
        for (TrackNode node : this.editedNodes.keySet()) {
            addSignToNode(changes, node, sign, !firedEvent);
            firedEvent = true;
        }
    }

    private void addSignToNode(HistoryChangeCollection changes, TrackNode node, TrackNodeSign sign, boolean fireBuildEvent) throws ChangeCancelledException {
        TrackNodeSign[] old_signs = node.getSigns();
        setSignsForNode(changes, node, TrackNodeSign.appendToArray(node.getSigns(), sign.clone()));
        if (fireBuildEvent) {
            // Fire a sign build event with the sign's custom sign
            if (!sign.fireBuildEvent(this.getPlayer(), node)) {
                node.setSigns(old_signs);
                throw new ChangeCancelledException();
            }
        }

        // All successful, now add this sign to an animation state if one was set to be edited
        updateSignInSelectedAnimationStates(node, null, sign);
    }

    private void setSignsForNode(HistoryChangeCollection changes, TrackNode node, TrackNodeSign[] new_signs) throws ChangeCancelledException {
        TrackNodeSign[] old_signs = node.getSigns();
        changes.addChangeBeforeSetSigns(this.player, node, new_signs);
        node.setSigns(new_signs);
        try {
            changes.handleChangeAfterSetSigns(this.player, node, old_signs);
        } catch (ChangeCancelledException ex) {
            node.setSigns(old_signs);
            throw ex;
        }
    }

    private void updateSignInSelectedAnimationStates(TrackNode node, TrackNodeSign old_sign, TrackNodeSign new_sign) {
        TrackNodeAnimationState animState = node.findAnimationState(this.selectedAnimation);
        if (animState != null) {
            // Refresh selected animation state of node too, if one is selected for this node
            // Leave other animation states alone
            node.setAnimationState(animState.name, updateSignInNodeState(animState.state, old_sign, new_sign), animState.connections);
        } else {
            // No animation is selected for this node, presume the sign should be added to all animation states
            for (TrackNodeAnimationState state : node.getAnimationStates()) {
                node.setAnimationState(state.name, updateSignInNodeState(state.state, old_sign, new_sign), state.connections);
            }
        }
    }

    private TrackNodeState updateSignInNodeState(TrackNodeState state, TrackNodeSign old_sign, TrackNodeSign new_sign) {
        if (old_sign == null) {
            return state.changeAddSign(new_sign.clone());
        } else if (new_sign == null) {
            return state.changeRemoveSign(old_sign);
        } else {
            return state.changeUpdateSign(old_sign, new_sign.clone());
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
        if (this.heldDownTicks == 0 && this.getEditedNodes().size() <= 1) {
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

        // Before doing anything, make sure all selected nodes do **not** use zero-length neighbours
        // Bad things happen if this is the case
        this.makeConnectionsCurved();

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
            Vector newNodePos = conn.getPosition(0.5);
            Vector newNodeOri = conn.getOrientation(0.5);

            this.setEditing(conn.getNodeA(), false);
            this.setEditing(conn.getNodeB(), false);

            changes.addChangeBeforeDisconnect(this.player, conn);
            conn.remove();

            TrackNode newNode = conn.getNodeA().getCoaster().createNewNode(newNodePos, newNodeOri);
            changes.addChangeCreateNode(this.player, newNode);

            changes.addChangeAfterConnect(this.player, tracks.connect(conn.getNodeA(), newNode));
            changes.addChangeAfterConnect(this.player, tracks.connect(newNode, conn.getNodeB()));
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
                    changes.addChangeAfterConnect(this.player, tracks.connect(prevNode, node));
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
                    changes.addChangeAfterConnect(this.player, newNode, node);
                } else {
                    changes.addChangeAfterConnect(this.player, tracks.connect(node, newNode));
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

    /**
     * Inserts additional nodes where needed to make the connections with
     * all selected nodes straight
     */
    public void makeConnectionsStraight() throws ChangeCancelledException {
        // Collect all nodes that need to be duplicated to make the connections straight
        List<TrackNode> nodes = this.getEditedNodes().stream()
            .filter(n -> n.getConnections().size() == 2 && n.getZeroDistanceNeighbour() == null)
            .collect(Collectors.toList());

        if (nodes.isEmpty()) {
            return; // Weird.
        }

        // Track all changes
        HistoryChange changes = this.getHistory().addChangeGroup();

        // Work on all nodes
        for (TrackNode node : nodes) {
            List<TrackConnection> connections = node.getConnections();
            if (connections.size() != 2) {
                continue; // Strange. Shouldn't happen.
            }

            // Disconnect the second connection, preserve objects!
            List<TrackObject> removedObjects = connections.get(1).getObjects();
            TrackNode disconnectedNode = connections.get(1).getOtherNode(node);

            HistoryChange nodeChanges = changes.addChangeGroup();
            nodeChanges.addChangeBeforeDisconnect(this.player, connections.get(1));
            connections.get(1).remove();

            // Create another node at the same position as this node
            TrackNode newNode = this.getWorld().getTracks().addNode(node, node.getPosition().clone());

            // Create new nodes at the same position as the node, for every neighbour
            nodeChanges.addChangeCreateNode(this.player, newNode);

            // Connect this new node with the previously disconnected node
            // Restore the track objects on this connection
            // Order of nodeA / nodeB might matter for track objects!
            TrackConnection newConnection;
            if (connections.get(1).getNodeA() == disconnectedNode) {
                newConnection = getWorld().getTracks().connect(disconnectedNode, newNode);
            } else {
                newConnection = getWorld().getTracks().connect(newNode, disconnectedNode);
            }
            newConnection.addAllObjects(removedObjects);
            nodeChanges.addChangeAfterConnect(this.player, newConnection);

            // Select the new node as well
            this.selectNode(newNode);
        }
    }

    /**
     * Removes additional nodes where needed to make the connections
     * with all selected nodes curved (again)
     */
    public void makeConnectionsCurved() throws ChangeCancelledException {
        // Collect all nodes that have a zero-length neighbour that needs erasing
        Set<TrackNode> nodes = this.getEditedNodes().stream()
            .filter(n -> n.getConnections().size() == 2 && n.getZeroDistanceNeighbour() != null)
            .collect(Collectors.toCollection(HashSet::new));

        // Make sure to erase nodes whose neighbour is also in this set (the one we're removing)
        {
            Iterator<TrackNode> iter = nodes.iterator();
            while (iter.hasNext()) {
                if (nodes.contains(iter.next().getZeroDistanceNeighbour())) {
                    iter.remove();
                }
            }
        }

        if (nodes.isEmpty()) {
            return; // Weird.
        }

        // Track all changes
        HistoryChange changes = this.getHistory().addChangeGroup();

        // Work on all nodes
        for (TrackNode node : nodes) {
            TrackNode zero = node.getZeroDistanceNeighbour();
            if (zero == null) {
                continue; // Strange. Shouldn't happen.
            }

            // Disconnect all nodes connected to the zero node. Preserve objects!
            List<TrackConnection> connections = zero.getConnections().stream()
                    .filter(c -> !c.isConnected(node))
                    .collect(Collectors.toList());
            List<List<TrackObject>> objects = connections.stream()
                    .map(conn -> conn.getObjects())
                    .collect(Collectors.toList());

            // Disconnect all previous connections
            for (TrackConnection connection : connections) {
                changes.addChangeBeforeDisconnect(this.player, connection);
                connection.remove();
            }

            // Delete zero
            changes.addChangeBeforeDeleteNode(this.player, zero);
            zero.remove();

            // Connect all nodes that were connected to zero with node instead
            for (int i = 0; i < connections.size(); i++) {
                TrackConnection connection = connections.get(i);
                List<TrackObject> connObjects = objects.get(i);

                TrackConnection newConnection;
                if (connection.getNodeA() == zero) {
                    newConnection = getWorld().getTracks().connect(node, connection.getNodeB());
                } else {
                    newConnection = getWorld().getTracks().connect(connection.getNodeA(), node);
                }
                newConnection.addAllObjects(connObjects);
                changes.addChangeAfterConnect(this.player, newConnection);
            }
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
            // Check whether the player is moving only a single node or not
            // Count two zero-connected nodes as one node
            final boolean isSingleNode;
            if (this.editedNodes.size() == 1) {
                isSingleNode = true;
            } else if (this.editedNodes.size() == 2) {
                Iterator<TrackNode> iter = this.editedNodes.keySet().iterator();
                isSingleNode = iter.next().getZeroDistanceNeighbour() == iter.next();
            } else {
                isSingleNode = false;
            }

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
                if (!this.isSneaking() && (isSingleNode || editNode.node.getConnections().size() <= 1)) {
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

        // When we left-clicked while right-click dragging earlier, we made some changes to split the node
        // We want the new position of this dragged node to be merged with those changes, so only one undo is needed
        // If this is not the case, then we just add a new change to the history itself
        HistoryChangeCollection dragParent = this.getHistory();
        if (this.draggingCreateNewNodeChange != null && this.draggingCreateNewNodeChange == this.getHistory().getLastChange()) {
            dragParent = this.draggingCreateNewNodeChange;
        }
        this.draggingCreateNewNodeChange = null;

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

                    // Undo the changes to the node position as a result of the drag
                    draggedNode.node.setState(draggedNode.startState);

                    // Track all the changes we are doing down below.
                    // Delete the original node, and connections to the node, the player was dragging
                    HistoryChange changes = dragParent.addChangeBeforeDeleteNode(this.player, draggedNode.node);
                    draggedNode.node.remove();

                    // Connect all that was connected to it, with the one dropped on
                    for (TrackNode connected : connectedNodes) {
                        if (connected != droppedNode) {
                            changes.addChangeAfterConnect(this.player, tracks.connect(droppedNode, connected));
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
            HistoryChange changes = null;
            try {
                for (PlayerEditNode editNode : this.editedNodes.values()) {
                    if (editNode.hasMoveBegun()) {
                        if (changes == null) {
                            changes = dragParent.addChangeGroup();
                        }
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

        // For moving track objects, store the changes / fire after change event
        if (this.isMode(PlayerEditMode.OBJECT)) {
            this.objectState.onEditingFinished();
        }
    }

    /**
     * Adds connections to the target node for all animation states defined in the node
     * 
     * @param node The node, first parameter of connect(a,b), to this node connections are added
     * @param target The target, second parameter of connect(a,b)
     */
    private void addConnectionForAnimationStates(TrackNode node, TrackNodeReference target) {
        node.addAnimationStateConnection(this.selectedAnimation, TrackConnectionState.create(node, target, Collections.emptyList()));
    }

    /**
     * Removes connections to the target node for all animation states defined in the node
     * 
     * @param node
     * @param target
     */
    private void removeConnectionForAnimationStates(TrackNode node, TrackNodeReference target) {
        node.removeAnimationStateConnection(this.selectedAnimation, target);
    }
}
