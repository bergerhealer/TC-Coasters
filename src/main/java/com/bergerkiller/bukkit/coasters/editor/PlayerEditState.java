package com.bergerkiller.bukkit.coasters.editor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.bergerkiller.bukkit.coasters.editor.history.HistoryChangeConnect;
import com.bergerkiller.bukkit.coasters.editor.manipulation.DraggedTrackNode;
import com.bergerkiller.bukkit.coasters.editor.manipulation.NodeDragHandler;
import com.bergerkiller.bukkit.coasters.editor.manipulation.NodeDragManipulator;
import com.bergerkiller.bukkit.coasters.editor.manipulation.NodeManipulationMode;
import com.bergerkiller.bukkit.coasters.editor.manipulation.modes.circle.NodeDragManipulatorCircleFit;
import com.bergerkiller.bukkit.coasters.editor.manipulation.modes.NodeDragManipulatorPosition;
import com.bergerkiller.bukkit.coasters.editor.object.ui.BlockSelectMenu;
import com.bergerkiller.bukkit.coasters.events.CoasterCreateConnectionEvent;
import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
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
import com.bergerkiller.bukkit.coasters.editor.signs.SignEditState;
import com.bergerkiller.bukkit.coasters.events.CoasterSelectNodeEvent;
import com.bergerkiller.bukkit.coasters.objects.TrackObject;
import com.bergerkiller.bukkit.coasters.tracks.TrackCoaster;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnectionState;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeAnimationState;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeReference;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeSearchPath;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeState;
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
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.Util;
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
    private final NodeDragHandler dragHandler;
    private final PlayerEditHistory history;
    private final PlayerEditClipboard clipboard;
    private final ObjectEditState objectState;
    private final SignEditState signState;
    protected final Set<TrackNode> editedNodes = new LinkedHashSet<TrackNode>();
    private final TreeMultimap<String, TrackNode> editedNodesByAnimationName = TreeMultimap.create(Ordering.natural(), Ordering.arbitrary());
    private CoasterWorld cachedCoasterWorld = null;
    private TrackNode lastEdited = null;
    private long lastEditTime = System.currentTimeMillis();
    private PlayerEditMode editMode = PlayerEditMode.DISABLED;
    private PlayerEditMode afterEditMode = null;
    private NodeManipulationMode nodeManipulationMode = NodeManipulationMode.NONE;
    private int heldDownTicks = 0;
    private boolean changed = false;
    private boolean editedAnimationNamesChanged = false;
    private Block targetedBlock = null;
    private BlockFace targetedBlockFace = BlockFace.UP;
    private String selectedAnimation = null;
    private HistoryChange draggingCreateNewNodeChange = null; // used when player left-clicks while dragging a node
    private Integer particleViewRangeOverride = null; // Player initiated override
    private Integer particleViewRangeMaximum = null; // WorldGuard Region max view tracking

    public PlayerEditState(TCCoasters plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.input = new PlayerEditInput(player);
        this.dragHandler = new NodeDragHandler(input);
        this.history = new PlayerEditHistory(player);
        this.clipboard = new PlayerEditClipboard(this);
        this.objectState = new ObjectEditState(this);
        this.signState = new SignEditState(this);

        if (Common.hasCapability("Module:RegionFlagTracker")) {
            enableRegionMaxViewTracking();
        }
    }

    private void enableRegionMaxViewTracking() {
        this.particleViewRangeMaximum = PlayerRegionViewRange.track(this);
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
            this.particleViewRangeOverride = Util.getConfigOptional(config, "particleViewRange", Integer.class).orElse(null);
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
                                this.editedNodes.add(node);
                                for (TrackNodeAnimationState animation : node.getAnimationStates()) {
                                    this.editedNodesByAnimationName.put(animation.name, node);
                                }

                                TrackNode zeroDistNeighbour = node.getZeroDistanceNeighbour();
                                if (zeroDistNeighbour != null) {
                                    this.editedNodes.add(zeroDistNeighbour);
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
        Util.setConfigOptional(config, "particleViewRange", Optional.ofNullable(this.particleViewRangeOverride));
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

    public SignEditState getSigns() {
        return this.signState;
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
                if (!oldNode.isRemoved()) {
                    oldNode.onStateUpdated(this.player);
                }
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

    /**
     * Finds the track node the player is currently looking at exactly, up to a distance
     * away. Also checks that the view is unobstructed - there is no solid block in the line
     * of sight.
     *
     * @param maxDistance Maximum distance
     * @return node looking at
     */
    public TrackNode findLookingAtIfUnobstructed(double maxDistance) {
        Location eyeLocation = getPlayer().getEyeLocation();
        TrackNode lookingAt = getWorld().getTracks().findNodeLookingAt(
                eyeLocation, 1.0, maxDistance);
        if (lookingAt != null) {
            double distance = lookingAt.getPosition().distance(eyeLocation.toVector());
            if (WorldUtil.rayTraceBlock(eyeLocation, distance) == null) {
                return lookingAt;
            }
        }
        return null;
    }

    /**
     * Checks whether player is looking to find any rail blocks of nodes in the general
     * position.
     *
     * @return LookingAtRailInfo for the rail block the player is looking at, or null
     *         if the player isn't looking at any rails.
     */
    public LookingAtRailInfo findLookingAtRailBlock() {
        return findLookingAtRailBlock(false);
    }

    /**
     * Checks whether player is looking to find any rail blocks of nodes in the general
     * position.
     *
     * @param ignoreDefaultRailBlocks Whether to ignore rail blocks of nodes that are
     *        set to the default (reset)
     * @return LookingAtRailInfo for the rail block the player is looking at, or null
     *         if the player isn't looking at any rails.
     */
    public LookingAtRailInfo findLookingAtRailBlock(boolean ignoreDefaultRailBlocks) {
        // Create an inverted camera transformation of the player's view direction
        Matrix4x4 cameraTransform = new Matrix4x4();
        cameraTransform.translateRotate(this.player.getEyeLocation());
        cameraTransform.invert();

        return findLookingAtRailBlockWithCamera(cameraTransform, ignoreDefaultRailBlocks);
    }

    /**
     * Checks whether player is looking to find any rail blocks of nodes in the general
     * position.
     *
     * @param invertedCameraTransform Inverted camera transform
     * @param ignoreDefaultRailBlocks Whether to ignore rail blocks of nodes that are
     *        set to the default (reset)
     * @return LookingAtRailInfo for the rail block the player is looking at, or null
     *         if the player isn't looking at any rails.
     */
    private LookingAtRailInfo findLookingAtRailBlockWithCamera(Matrix4x4 invertedCameraTransform, boolean ignoreDefaultRailBlocks) {
        // Try to find any nodes that have a rail block where the player looks
        double bestDistance = Double.MAX_VALUE; // getRailBlockViewDistance already limits
        IntVector3 bestRail = null;
        List<TrackNode> bestNodes = new ArrayList<>(5);
        for (TrackCoaster coaster : getWorld().getTracks().getCoasters()) {
            for (TrackNode node : coaster.getNodes()) {
                if (!ignoreDefaultRailBlocks || node.getRailBlock(false) != null) {
                    double distance = node.getRailBlockViewDistance(invertedCameraTransform);
                    if (distance <= bestDistance && distance != Double.MAX_VALUE) {
                        // Multi-select the nodes when they match the same rail block
                        if (distance < bestDistance) {
                            bestNodes.clear();
                            bestRail = node.getRailBlock(true);
                        }
                        bestNodes.add(node);
                        bestDistance = distance;
                    }
                }
            }
        }

        return bestRail == null ? null : new LookingAtRailInfo(bestRail, bestNodes, bestDistance);
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
            .anyMatch(n -> n.getConnections().size() == 2 && n.getZeroDistanceNeighbour() == null);
    }

    /**
     * Gets whether any of the nodes selected by the player are straight.
     *
     * @return True if any of the selected nodes have straight connections
     */
    public boolean hasStraightConnectionTrackNodes() {
        return hasEditedNodes() && getEditedNodes().stream()
            .anyMatch(n -> n.getZeroDistanceNeighbour() != null);
    }

    /**
     * Gets a set of all nodes selected by the player that are being edited
     *
     * @return set of all edited nodes
     */
    public Set<TrackNode> getEditedNodes() {
        return this.editedNodes;
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
        for (TrackNode node : this.editedNodes) {
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
        Iterator<TrackNode> iter = this.editedNodes.iterator();
        while (iter.hasNext()) {
            TrackNode node = iter.next();
            if (node.isRemoved()) {
                iter.remove();
            } else if (node.isLocked()) {
                iter.remove();
                hadLockedNodes = true;
                if (!node.isRemoved()) {
                    node.onStateUpdated(this.player);
                    this.lastEdited = node;
                    this.editedAnimationNamesChanged |= node.hasAnimationStates();
                }
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
                if (!node.isRemoved()) {
                    node.onStateUpdated(this.player);
                }
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
        if (this.editedNodes.contains(node)) {
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
        if (editing && node.isRemoved()) {
            throw new IllegalArgumentException("Cannot edit a removed node");
        }
        boolean changed;
        if (editing) {
            if (this.editedNodes.contains(node)) {
                changed = false;
            } else {
                changed = true;
                this.editedNodes.add(node);
            }
        } else {
            changed = this.editedNodes.remove(node);
        }
        if (changed) {
            // Can be caused by the node being removed, handle that here
            if (!node.isRemoved()) {
                node.onStateUpdated(this.player);
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
            } else if (this.lastEdited == node) {
                this.lastEdited = null;
            }
            this.onEditedNodesChanged();
        }
    }

    public boolean isEditing(TrackNode node) {
        return this.editedNodes.contains(node);
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
        try {
            if (nodeManipulationMode.leftClick(this)) {
                return true;
            }
        } catch (ChangeCancelledException ex) {
            clearEditedNodes();
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
        double bestDistance = Double.MAX_VALUE;

        // When in rail mode, look up using the rail block of the nodes as well
        // Ignore nodes that don't have a rail block set
        if (this.getMode() == PlayerEditMode.RAILS) {
            LookingAtRailInfo info = findLookingAtRailBlockWithCamera(cameraTransform, true);
            if (info != null) {
                bestNodes.addAll(info.nodes);
                bestDistance = info.distance;
            }
        }

        // Clicking on a node, or a junction floating block particle
        TrackConnection bestJunction = null;
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
            TrackNode node = bestNodes.iterator().next();
            node.switchJunction(bestJunction);
            node.getWorld().getTracks().updateAllWithPriority();
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

    public boolean onClickBlock(boolean isLeftClick, boolean isRightClick, Block clickedBlock) {
        // When right clicking blocks select the block in the block select menu
        if (isRightClick) {
            TCCoastersDisplay display = MapDisplay.getHeldDisplay(player, TCCoastersDisplay.class);
            if (display != null) {
                for (MapWidget w = display.getActivatedWidget(); w != null; w = w.getParent()) {
                    if (w instanceof BlockSelectMenu) {
                        ((BlockSelectMenu) w).setSelectedBlock(WorldUtil.getBlockData(clickedBlock));
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public boolean onRightClick() {
        this.input.click();
        return true;
    }

    public boolean isHoldingRightClick() {
        return this.input.hasInput() &&  this.plugin.getHeldTool(this.player).isNodeSelector();
    }

    public void update() {
        if (this.isHoldingRightClick()) {
            this.input.update();
            this.markChanged();
            if (this.input.heldDuration() >= EDIT_AUTO_TIMEOUT) {
                try {
                    boolean runNow;
                    if (this.heldDownTicks == 0) {
                        runNow = true;
                        this.nodeManipulationMode = this.editMode.getManipulationMode().select(this);
                    } else {
                        int tick = this.heldDownTicks - this.nodeManipulationMode.getAutoActivateDelay();
                        int interval = this.nodeManipulationMode.getAutoActivateInterval();
                        runNow = tick >= 0 && (interval <= 0 || (tick % interval) == 0);
                    }
                    if (runNow) {
                        this.nodeManipulationMode.rightClick(this);
                    }
                    this.heldDownTicks++;
                } catch (ChangeCancelledException ex) {
                    this.clearEditedNodes();
                }
            }
        } else {
            if (this.heldDownTicks > 0) {
                try {
                    this.dragManipulationFinish();
                } catch (ChangeCancelledException e) {
                    this.clearEditedNodes();
                }
                this.heldDownTicks = 0;
                this.targetedBlock = null;
                this.nodeManipulationMode = NodeManipulationMode.NONE;
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

    /**
     * Deletes all selected tracks and selects the track that is connected
     * to the now-deleted tracks. This allows repeated deletion to 'walk' and delete.
     */
    public void deleteTrack() throws ChangeCancelledException {
        createTrackDeletePlan().execute(this);
    }

    /**
     * Creates a plan to delete the selected track nodes, without actually deleting them yet.
     * This is used to show a preview of what would be deleted, and to select the nodes that would be affected by the deletion.
     * Call execute(state) to actually delete them.
     *
     * @return TrackDeletionPlan
     */
    public TrackDeletionPlan createTrackDeletePlan() {
        // Defensive copy of only those nodes we can delete (not locked coaster)
        HashSet<TrackNode> toDelete = new HashSet<TrackNode>(this.getEditedNodes().size());
        for (TrackNode node : this.getEditedNodes()) {
            if (!node.isLocked()) {
                toDelete.add(node);
            }
        }

        TrackDeletionPlan plan = new TrackDeletionPlan();
        if (toDelete.isEmpty()) {
            return plan; // No-op
        }

        // Delete connections when both adjacent nodes are selected
        for (TrackNode node : toDelete) {
            for (TrackConnection connection : node.getConnections()) {
                if (connection.isZeroLength()) {
                    continue;
                }

                if (toDelete.contains(connection.getOtherNode(node))) {
                    plan.connectionsToDelete.add(connection); // Note: set behavior
                }
            }
        }

        // Eliminate zero-distance neighbours to avoid duplicates
        boolean hadZDNeighbour;
        do {
            hadZDNeighbour = false;
            for (TrackNode node : toDelete) {
                TrackNode node_zd = node.getZeroDistanceNeighbour();
                if (node_zd != null && toDelete.remove(node_zd)) {
                    hadZDNeighbour = true;
                    break;
                }
            }
        } while (hadZDNeighbour);

        if (plan.connectionsToDelete.isEmpty()) {
            // All these nodes must be deleted
            // Nodes with a zero-distance neighbour need to be made curved first
            for (TrackNode node : toDelete) {
                if (node.getZeroDistanceNeighbour() != null) {
                    plan.nodesToMakeCurved.add(node);
                }
                plan.nodesToDelete.add(node);

                // Select the nodes connected to this node (chain deletion)
                // We found no connections earlier, so we know they are not being deleted.
                List<TrackConnection> connections = node.getConnections();
                TrackNode node_zd = node.getZeroDistanceNeighbour();
                if (node_zd != null) {
                    connections = new ArrayList<>(connections);
                    connections.addAll(node_zd.getConnections());
                    connections.removeIf(TrackConnection::isZeroLength);
                }
                for (TrackConnection conn : connections) {
                    TrackNode neigh = conn.getOtherNode(node);
                    if (!toDelete.contains(neigh)) {
                        plan.nodesToSelect.add(neigh);
                        TrackNode neigh_zd = neigh.getZeroDistanceNeighbour();
                        if (neigh_zd != null) {
                            plan.nodesToSelect.add(neigh_zd);
                        }
                    }
                }
            }
        } else {
            // If there are connections to delete, also delete all nodes that have no connections after
            // Straightened nodes with only one connection only need to be made curved to delete the ZD neighbour
            // Preserve nodes that have connections in animation states
            for (TrackNode node : toDelete) {
                TrackNode node_zd = node.getZeroDistanceNeighbour();

                int remainingConnections = 0;
                for (TrackConnection conn : node.getConnections()) {
                    if (!plan.connectionsToDelete.contains(conn) && !conn.isZeroLength()) {
                        remainingConnections++;
                    }
                }

                int remainingConnectionZD = 0;
                if (node_zd != null) {
                    for (TrackConnection conn : node_zd.getConnections()) {
                        if (!plan.connectionsToDelete.contains(conn) && !conn.isZeroLength()) {
                            remainingConnectionZD++;
                        }
                    }
                }

                // Orphan ZD.
                if (node_zd != null && remainingConnections == 0 || remainingConnectionZD == 0) {
                    plan.nodesToMakeCurved.add(node);
                }

                // Keep if there's remaining connections
                if ((remainingConnections + remainingConnectionZD) > 0) {
                    plan.nodesToSelect.add(node);
                    if (node_zd != null) {
                        plan.nodesToSelect.add(node_zd);
                    }
                    continue;
                }

                // Before we delete the node, do check if this node has animation states with connections
                // Don't just nuke the node in that case as we'd lose all that then.
                if (node.hasAnimationStatesWithConnections() || (node_zd != null && node_zd.hasAnimationStatesWithConnections())) {
                    plan.nodesToSelect.add(node);
                    if (node_zd != null) {
                        plan.nodesToSelect.add(node_zd);
                    }
                    continue;
                }

                plan.nodesToDelete.add(node);
            }
        }

        // Count the connections of which either node they are connected to are not deleted
        plan.connectionsDeletedWithoutNodeDeletion = 0;
        for (TrackConnection conn : plan.connectionsToDelete) {
            if (!plan.isDeletedOrZD(conn.getNodeA()) || !plan.isDeletedOrZD(conn.getNodeB())) {
                plan.connectionsDeletedWithoutNodeDeletion++;
            }
        }

        return plan;
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

        changes.addChangeBeforeSetRail(this.player, node, new_rail);
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
            TrackNode node = this.editedNodes.iterator().next();
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
        calcOrientation(node -> orientation);
    }

    /**
     * Sets the orientation for all selected nodes
     * 
     * @param orientationFunc Function called to compute a new orientation up vector
     */
    public void calcOrientation(Function<TrackNode, Vector> orientationFunc) throws ChangeCancelledException {
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
            Vector orientation = orientationFunc.apply(node);
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
     * Handles the left-click action while the player is dragging nodes around. Places a new node down
     * where the player is currently looking/dragging.
     *
     * @return True if a node was created, or False if not possible (dragging multiple nodes for example)
     * @throws ChangeCancelledException If the change was cancelled (permissions), in which case all selected nodes are deselected.
     */
    public boolean insertNodeWhileDragging() throws ChangeCancelledException {
        // Complete moving the original selected node(s), call onEditingFinished
        // Note: can be cancelled!
        this.dragManipulationFinish();

        // Guarantee
        this.deselectLockedNodes();

        // See if we are dragging around exactly one node.
        // Do take into account that we could have a zero-distance neighbour (straightened)
        TrackNode singleEditedNode = null;
        for (TrackNode node : getEditedNodes()) {
            if (singleEditedNode == null) {
                singleEditedNode = node;
            } else if (singleEditedNode == node.getZeroDistanceNeighbour()) {
                continue; // Allow
            } else {
                // Multiple nodes selected
                singleEditedNode = null;
                break;
            }
        }

        if (singleEditedNode == null) {
            return false;
        }

        Vector pos = singleEditedNode.getPosition().clone();

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

        // Reuse the builder plan logic for placing the new node
        TrackBuilderPlan plan = this.createTrackBuildPlan(pos, null, true);
        if (plan.spaceOccupied) {
            return false;
        }
        plan.execute(this);
        this.draggingCreateNewNodeChange = this.getHistory().getLastChange();
        return true;
    }

    /**
     * Creates a new track node, connecting with already selected track nodes.
     * The created track node is selected to allow chaining create node calls.
     */
    public void createTrack() throws ChangeCancelledException {
        createTrackBuildPlan().execute(this);
    }

    /**
     * Creates a plan for creating a new track node, based on where the player is looking and what they are looking at.
     * The plan can be executed or used for GUI state display.<br>
     * <br>
     * Note: this plan is not long-lived. Execute right after creating the plan, do not store it.
     * Later Player selection changes will not be reflected.
     *
     * @return TrackBuilderPlan
     */
    public TrackBuilderPlan createTrackBuildPlan() {
        Location eyeLoc = getPlayer().getEyeLocation();
        Vector pos = null;
        Vector ori = null;
        boolean inAir = false;

        // If a previous interact event did not already set one, see if the player is looking at a particular block
        // If so, create the node on that block
        Block targetedBlock = this.targetedBlock;
        BlockFace targetedBlockFace = this.targetedBlockFace;
        if (targetedBlock == null) {
            TargetedBlockInfo info = TCCoastersUtil.rayTrace(this.player);
            if (info != null) {
                targetedBlock = info.block;
                targetedBlockFace = info.face;
            }
        }

        // If targeting a block face, create a node on top of this face
        if (targetedBlock != null) {
            // If the targeted block is a rails block, connect to one end of it
            RailType clickedRailType = RailType.getType(targetedBlock);
            if (clickedRailType != RailType.NONE) {
                // Load the path for the rails clicked
                RailState state = new RailState();
                state.setRailPiece(RailPiece.create(clickedRailType, targetedBlock));
                state.position().setLocation(clickedRailType.getSpawnLocation(targetedBlock, targetedBlockFace));
                state.position().setMotion(targetedBlockFace);
                state.initEnterDirection();
                RailPath path = state.loadRailLogic().getPath();

                // Select the best possible path end position
                RailPath.Position p1 = path.getStartPosition();
                RailPath.Position p2 = path.getEndPosition();
                p1.makeAbsolute(targetedBlock);
                p2.makeAbsolute(targetedBlock);
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
                    ori = FaceUtil.faceToVector(targetedBlockFace);
                    pos = new Vector(targetedBlock.getX() + 0.5 + f * targetedBlockFace.getModX(),
                            targetedBlock.getY() + 0.5 + f * targetedBlockFace.getModY(),
                            targetedBlock.getZ() + 0.5 + f * targetedBlockFace.getModZ());
                }
            }
        }

        // Create in front of the player by default
        if (pos == null) {
            pos = eyeLoc.toVector().add(eyeLoc.getDirection().multiply(0.5));
            inAir = true;
        }

        // First check no track already exists at this position
        if (!getWorld().getTracks().findNodesNear(new ArrayList<TrackNode>(2), pos, 0.1).isEmpty()) {
            return TrackBuilderPlan.SPACE_OCCUPIED;
        }

        return createTrackBuildPlan(pos, ori, inAir);
    }

    /**
     * Creates a plan for creating a new track node, using the new node position and orientation specified.
     * The plan can be executed or used for GUI state display.
     * Does not check a node already existing at the new node position.<br>
     * <br>
     * Note: this plan is not long-lived. Execute right after creating the plan, do not store it.
     * Later Player selection changes will not be reflected.
     *
     * @param newNodePosition The position to create the new node at
     * @param newNodeOrientation The orientation (up) of the new node, or null to leave it default
     * @param isInAir Whether the new node is placed in the Air. This subtly changes build behavior.
     * @return TrackBuilderPlan
     */
    public TrackBuilderPlan createTrackBuildPlan(Vector newNodePosition, Vector newNodeOrientation, boolean isInAir) {
        // Get all selected nodes, except those that are for a locked coaster
        // Those will be de-selected later when the plan is executed.
        final Set<TrackNode> selectedNodes = new LinkedHashSet<>(editedNodes.size());
        for (TrackNode node : getEditedNodes()) {
            if (!node.isLocked()) {
                selectedNodes.add(node);
            }
        }

        // This plan will be populated
        TrackBuilderPlan plan = new TrackBuilderPlan(newNodePosition, newNodeOrientation, isInAir);
        plan.nodesToConnect.addAll(selectedNodes);

        // Find all track connections that have been selected thanks
        // to both nodes being selected for editing right now.
        // These will be split with a new node inserted in the middle when the plan is executed.
        for (TrackNode node : selectedNodes) {
            for (TrackConnection conn : node.getConnections()) {
                if (!conn.isZeroLength() && selectedNodes.contains(conn.getOtherNode(node))) {
                    plan.connectionsToSplit.add(conn);

                    // Do not connect with the nodes of this connection when executing the plan
                    // The nodes are already forming a new connection, no need to do more
                    plan.nodesToConnect.remove(conn.getNodeA());
                    plan.nodesToConnect.remove(conn.getNodeB());
                    TrackNode zd = conn.getNodeA().getZeroDistanceNeighbour();
                    if (zd != null) {
                        plan.nodesToConnect.remove(zd);
                    }
                    zd = conn.getNodeB().getZeroDistanceNeighbour();
                    if (zd != null) {
                        plan.nodesToConnect.remove(zd);
                    }
                }
            }
        }

        // For all remaining nodes, eliminate zero-distance neighbours to avoid glitches
        // Later while executing the plan, if this node turns into a junction this zero-distance
        // neighbour will be deleted. Or, it will connect to the other node if it happens
        // to be a zero-distance orphan.
        boolean foundZDNeighbour;
        do {
            foundZDNeighbour = false;
            for (TrackNode node : plan.nodesToConnect) {
                TrackNode zero = node.getZeroDistanceNeighbour();
                if (zero != null && plan.nodesToConnect.remove(zero)) {
                    foundZDNeighbour = true;
                    break;
                }
            }
        } while (foundZDNeighbour);

        return plan;
    }

    private Vector getNewNodePos() {
        Location eyeLoc = getPlayer().getEyeLocation();
        return eyeLoc.toVector().add(eyeLoc.getDirection().multiply(0.5));
    }

    /**
     * Splits a connection in two, inserting a new node in the middle.
     * The newly inserted node is automatically selected.
     *
     * @param connection TrackConnection to split
     * @param history History to record changes to
     * @return The new node created in the middle of the connection
     * @throws ChangeCancelledException If the change was cancelled
     */
    public TrackNode splitConnection(TrackConnection connection, HistoryChangeCollection history) throws ChangeCancelledException {
        Vector newNodePos = connection.getPosition(0.5);
        Vector newNodeOri = connection.getOrientation(0.5);
        return splitConnection(connection, newNodePos, newNodeOri, history);
    }

    /**
     * Splits a connection in two, inserting a new node in the middle.
     * The newly inserted node is automatically selected.
     *
     * @param connection TrackConnection to split
     * @param newNodePos Position to place the new node at
     * @param newNodeOri Orientation to place the new node at
     * @param history History to record changes to
     * @return The new node created in the middle of the connection
     * @throws ChangeCancelledException If the change was cancelled
     */
    public TrackNode splitConnection(TrackConnection connection, Vector newNodePos, Vector newNodeOri, HistoryChangeCollection history) throws ChangeCancelledException {
        TrackWorld tracks = getWorld().getTracks();

        // Save all track objects currently on this connection before removal
        TrackObject[] objectsToRestore = TrackObject.listToArray(connection.getObjects(), true);
        double prevTotalDistance = connection.getFullDistance();

        // Remove old connection
        history.addChangeBeforeDisconnect(this.player, connection);
        connection.remove();

        // Create the new middle node
        TrackNode newNode = connection.getNodeA().getCoaster().createNewNode(newNodePos, newNodeOri);
        history.addChangeCreateNode(this.player, newNode);

        // Create the new connections, can be cancelled
        TrackConnection newConnA = tracks.connect(connection.getNodeA(), newNode);
        history.handleEvent(new CoasterCreateConnectionEvent(this.player, newConnA), newConnA::remove);
        TrackConnection newConnB = tracks.connect(newNode, connection.getNodeB());
        history.handleEvent(new CoasterCreateConnectionEvent(this.player, newConnB), newConnB::remove);

        // Compute the full distances of the two newly created connections
        // They should be half of the full distance of the connection its replacing,
        // but I'm not sure if we can guarantee that...
        double newConnALength = newConnA.getFullDistance();
        double newConnBLength = newConnB.getFullDistance();

        // Place all track objects back onto the newly created two connections
        for (TrackObject obj : objectsToRestore) {
            // Normalize
            double distance = obj.getDistance();
            if (prevTotalDistance >= 1e-4) {
                distance *= (newConnALength + newConnBLength) / prevTotalDistance;
            }

            // Which one?
            if (distance >= newConnALength) {
                distance -= newConnALength;
                distance = Math.min(newConnBLength, distance);

                obj.setDistanceFlippedSilently(distance, obj.isFlipped());
                newConnB.addObject(obj);
            } else {
                obj.setDistanceFlippedSilently(distance, obj.isFlipped());
                newConnA.addObject(obj);
            }
        }

        // Add to history tracking now that the objects have been added
        history.addChange(new HistoryChangeConnect(newConnA));
        history.addChange(new HistoryChangeConnect(newConnB));

        // Select the new node
        this.setEditing(newNode, true);

        return newNode;
    }

    /**
     * Removes a node, and connects the connecting nodes with each other instead.
     * The track objects on the connections are preserved as well, and placed back onto the new
     * connection in the same relative positions.
     *
     * @param node Node to remove
     * @param history History to record changes to
     * @throws ChangeCancelledException If the change was cancelled
     */
    public void mergeRemoveNode(TrackNode node, HistoryChangeCollection history) throws ChangeCancelledException {
        TrackWorld tracks = getWorld().getTracks();

        // Validate connections. Keep track of a zero-distance neighbour as well.
        List<TrackConnection> connections = new ArrayList<>(node.getConnections());
        TrackNode nodeZD = node.getZeroDistanceNeighbour();
        if (nodeZD != null) {
            connections.addAll(nodeZD.getConnections());
            connections.removeIf(c -> c.isConnected(node) && c.isConnected(nodeZD));
        }
        if (connections.size() < 2) {
            // Remove trailing node
            history.addChangeBeforeDeleteNode(this.player, node);
            node.remove();
            return;
        }
        if (connections.size() > 2) {
            // Can't merge, too many connections
            return;
        }

        // The connections surrounding the node
        TrackConnection connA = connections.get(0);
        TrackConnection connB = connections.get(1);

        // The nodes connected to the node (or zero-distance neighbour if straightened)
        TrackNode neightNodeA = (connA.getNodeA() == node || connA.getNodeA() == nodeZD) ? connA.getNodeB() : connA.getNodeA();
        TrackNode neightNodeB = (connB.getNodeA() == node || connB.getNodeA() == nodeZD) ? connB.getNodeB() : connB.getNodeA();

        // Save all track objects that exist on the old two connections before removal
        // Adjust the distance value for the objects to be the full distance of the new replacement connection,
        // and also adjust it so that the distance saved is FROM neightNodeA to neightNodeB
        List<TrackObject> objectsToRestore;
        if (connA.hasObjects() || connB.hasObjects()) {
            objectsToRestore = new ArrayList<>();

            for (TrackObject obj : connA.getObjects()) {
                if (connA.getNodeA() == neightNodeA) {
                    objectsToRestore.add(obj.clone());
                } else {
                    objectsToRestore.add(obj.cloneFlipEnds(connA));
                }
            }

            for (TrackObject obj : connB.getObjects()) {
                TrackObject clonedObj;
                if (connB.getNodeA() == neightNodeB) {
                    clonedObj = obj.clone();
                } else {
                    clonedObj = obj.cloneFlipEnds(connB);
                }
                clonedObj.setDistanceFlippedSilently(clonedObj.getDistance() + connA.getFullDistance(), clonedObj.isFlipped());
                objectsToRestore.add(clonedObj);
            }
        } else {
            objectsToRestore = Collections.emptyList();
        }

        history.addChangeBeforeDisconnect(this.player, connA);
        history.addChangeBeforeDisconnect(this.player, connB);
        history.addChangeBeforeDeleteNode(this.player, node);

        connA.remove();
        connB.remove();
        node.remove();

        // Connect the nodes together again
        TrackConnection newConn = tracks.connect(neightNodeA, neightNodeB);
        history.handleEvent(new CoasterCreateConnectionEvent(this.player, newConn), newConn::remove);

        // Place all track objects back onto the newly created connection
        for (TrackObject obj : objectsToRestore) {
            if (newConn.getNodeA() != neightNodeA) {
                obj.setDistanceFlippedSilently(newConn.getFullDistance() - obj.getDistance(), !obj.isFlipped());
            }

            newConn.addObject(obj);
        }

        // Save history
        history.addChange(new HistoryChangeConnect(newConn));
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
            newNode.setRailBlock(node.getRailBlock(false));

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
        nodes.removeIf(n -> nodes.contains(n.getZeroDistanceNeighbour()));

        if (nodes.isEmpty()) {
            return; // Weird.
        }

        // Work on all nodes and record changes
        HistoryChange changes = this.getHistory().addChangeGroup();
        for (TrackNode node : nodes) {
            makeNodeConnectionsCurved(changes, node);
        }
    }

    public void makeNodeConnectionsCurved(HistoryChangeCollection changes, TrackNode node) throws ChangeCancelledException {
        TrackNode zero = node.getZeroDistanceNeighbour();
        if (zero == null) {
            return;
        }

        // If the zero node has signs and the node does not, delete node instead to preserve those
        if (zero.getSigns().length > 0 && node.getSigns().length == 0) {
            TrackNode tmp = node;
            node = zero;
            zero = tmp;
        }

        makeNodeConnectionsCurved(changes, node, zero);
    }

    public void makeNodeConnectionsCurved(final HistoryChangeCollection changes, final TrackNode node, final TrackNode zero) throws ChangeCancelledException {
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

        // Fire again to refresh selected nodes AFTER we've changed connections around
        onEditedNodesChanged();
    }

    /**
     * Gets whether a single node is being edited
     *
     * @return Takes into account zero-distance nodes
     */
    public boolean isEditingSingleNode() {
        if (this.editedNodes.size() == 1) {
            return true;
        } else if (this.editedNodes.size() == 2) {
            Iterator<TrackNode> iter = this.editedNodes.iterator();
            return iter.next().getZeroDistanceNeighbour() == iter.next();
        } else {
            return false;
        }
    }

    /**
     * Performs a manipulation action using the drag manipulator. This can perform batch operations
     * on nodes, like equalizing spacing or adding/removing nodes in a common selected 'shape'.
     * Returns false if the change could not be (fully) applied, or true if successful.<br>
     * <br>
     * The type of manipulator that runs the action depends on what menu the user has activated.
     * For example: if the circle shape mode is active, then the nodes will be fit to a circle shape.
     *
     * @param history History to record changes to
     * @param action Action to perform
     * @return True if successful, false if cancelled
     */
    public boolean performManipulation(HistoryChangeCollection history, ManipulatorAction action) {
        this.deselectLockedNodes();

        // Check if user was dragging nodes around before, in the middle of this manipulation being performed
        // In that case the previous drag operation must be finished (=committed) before running the manipulation.
        // After the manipulation is applied, the drag operation resumes like before.
        boolean wasDragging = dragHandler.isDragging();
        if (wasDragging) {
            try {
                dragManipulationFinish();
            } catch (ChangeCancelledException ex) {
                this.clearEditedNodes();
                return false;
            }
        }

        // Avoid no-selection manipulation
        if (!this.hasEditedNodes()) {
            return false;
        }

        // Initialize the right manipulator and perform the action
        NodeDragManipulator.Initializer initializer = getDragManipulatorInitializer();
        try {
            NodeDragManipulator manipulator = initializer.start(this, DraggedTrackNode.listOfNodes(this.getEditedNodes()));
            action.perform(manipulator, history);
        } catch (ChangeCancelledException ex) {
            this.clearEditedNodes();
            return false;
        }

        // If we were dragging before, resume the drag operation with the new node positions
        if (wasDragging) {
            dragManipulationUpdate();
        }

        return true;
    }

    public void dragManipulationUpdate() {
        // Deselect locked nodes that we cannot edit
        this.deselectLockedNodes();

        if (!this.hasEditedNodes()) {
            return;
        }

        try {
            // Initialize the right manipulator
            NodeDragManipulator.Initializer initializer = getDragManipulatorInitializer();

            // Manages dragging. If edited node selection changed, re-initializes
            dragHandler.drag(initializer, this);
        } catch (ChangeCancelledException ex) {
            this.clearEditedNodes();
        }
    }

    /**
     * When player releases the right-click mouse button or dragging is aborted for some other reason.
     * Records the dragged changes into history, and performs finishing logic.
     *
     * @throws ChangeCancelledException If the drag was cancelled
     */
    private void dragManipulationFinish() throws ChangeCancelledException {
        // Deselect locked nodes that we cannot edit
        this.deselectLockedNodes();

        // When we left-clicked while right-click dragging earlier, we made some changes to split the node
        // We want the new position of this dragged node to be merged with those changes, so only one undo is needed
        // If this is not the case, then we just add a new change to the history itself
        HistoryChangeCollection history = this.getHistory();
        if (this.draggingCreateNewNodeChange != null && this.draggingCreateNewNodeChange == this.getHistory().getLastChange()) {
            history = this.draggingCreateNewNodeChange;
        }
        this.draggingCreateNewNodeChange = null;

        // Perform finishing logic if we were manipulating before
        if (dragHandler.isDragging()) {
            dragHandler.dragFinish(history);
        }

        // For moving track objects, store the changes / fire after change event
        if (this.isMode(PlayerEditMode.OBJECT)) {
            this.objectState.onEditingFinished();
        }
    }

    /**
     * Gets the initializer for the drag manipulator to use, based on the current edit mode of the player.
     * If the player is in sort of shape manipulator mode, then that shape's specific manipulator is used.
     *
     * @return NodeDragManipulator Initializer to use for the current edit mode
     */
    private NodeDragManipulator.Initializer getDragManipulatorInitializer() {
        if (this.getMode() == PlayerEditMode.ORIENTATION) {
            return NodeDragManipulatorCircleFit.INITIALIZER;
            //return NodeDragManipulatorOrientation.INITIALIZER;
        } else {
            return NodeDragManipulatorPosition.INITIALIZER;
        }
    }

    // when the list of animations in selected nodes changes
    private void onEditedAnimationNamedChanged() {
        for (TCCoastersDisplay display : MapDisplay.getAllDisplays(TCCoastersDisplay.class)) {
            display.sendStatusChange("PlayerEditState::EditedAnimationNamesChanged");
        }
    }

    /**
     * Adds connections to the target node for all animation states defined in the node
     * 
     * @param node The node, first parameter of connect(a,b), to this node connections are added
     * @param target The target, second parameter of connect(a,b)
     */
    public void addConnectionForAnimationStates(TrackNode node, TrackNodeReference target) {
        node.addAnimationStateConnection(this.selectedAnimation, TrackConnectionState.create(node, target, Collections.emptyList()));
    }

    /**
     * Removes connections to the target node for all animation states defined in the node
     * 
     * @param node
     * @param target
     */
    public void removeConnectionForAnimationStates(TrackNode node, TrackNodeReference target) {
        node.removeAnimationStateConnection(this.selectedAnimation, target);
    }

    /**
     * Gets the player-specific particle view range
     * 
     * @return particle view range for this player
     */
    public int getParticleViewRange() {
        Integer viewRange = particleViewRangeOverride;
        if (viewRange == null) {
            viewRange = plugin.getParticleViewRange();
        }
        Integer max = particleViewRangeMaximum;
        if (max != null && viewRange > max) {
            viewRange = max;
        }
        return viewRange;
    }

    /**
     * Gets whether a custom particle view range is set
     *
     * @return True if set
     * @see #setParticleViewRangeOverride(Integer) 
     */
    public boolean isParticleViewRangeOverridden() {
        return particleViewRangeOverride != null;
    }

    /**
     * Sets the player-specific particle view range override
     * 
     * @param range particle view range to set, or null to use the global default
     */
    public void setParticleViewRangeOverride(Integer range) {
        if (!Objects.equals(range, particleViewRangeOverride)) {
            int oldRange = this.getParticleViewRange();
            this.particleViewRangeOverride = range;
            this.markChanged();

            // Update particles for this player when range changes
            if (this.getParticleViewRange() != oldRange) {
                getWorld().getParticles().scheduleViewerUpdate(this.player);
            }
        }
    }

    /**
     * Called from the region-flag based maximum tracking, to apply a maximum
     *
     * @param maximum Maximum, or null if not set
     */
    void updateParticleViewRangeMaximum(Integer maximum) {
        if (!Objects.equals(this.particleViewRangeMaximum, maximum)) {
            int oldRange = this.getParticleViewRange();
            this.particleViewRangeMaximum = maximum;

            // Update particles for this player when range changes
            if (this.getParticleViewRange() != oldRange) {
                getWorld().getParticles().scheduleViewerUpdate(this.player);
            }
        }
    }

    /**
     * Result of the looking-at-rail block information. Stores the rail block coordinates
     * the player is looking at, the distance to it and the nodes that use this
     * rail block.
     */
    public static final class LookingAtRailInfo {
        public final IntVector3 rail;
        public final List<TrackNode> nodes;
        public final double distance;

        public LookingAtRailInfo(IntVector3 rail, List<TrackNode> nodes, double distance) {
            this.rail = rail;
            this.nodes = nodes;
            this.distance = distance;
        }
    }

    /**
     * An action to be performed using the node-drag manipulator. Actions can include
     * equalizing the spacing between nodes or inserting/deleting nodes in the selected
     * 'shape'. Manipulator actions are effectively batch operations.
     */
    @FunctionalInterface
    public interface ManipulatorAction {
        void perform(NodeDragManipulator manipulator, HistoryChangeCollection history) throws ChangeCancelledException;
    }

    /**
     * Plan for building a track, used by the track builder tool. Stores all information about the track to be built,
     * such as what connections will be made or what nodes will be created. This builder plan can be executed,
     * or its information can be used to refresh GUI screens.
     */
    public static final class TrackBuilderPlan {
        public static final TrackBuilderPlan SPACE_OCCUPIED = new TrackBuilderPlan();

        /**
         * If true this is the SPACE_OCCUPIED constant, and no new node can be created there
         */
        public final boolean spaceOccupied;
        /**
         * New node position
         */
        public final Vector newNodePosition;
        /**
         * New node orientation. If null, leave default
         */
        public final Vector newNodeOrientation;
        /**
         * Whether the node was placed in the air versus on the ground
         */
        public final boolean isInAir;
        /**
         * Connections that will be split with a new node inserted in the middle
         */
        public final Set<TrackConnection> connectionsToSplit = new HashSet<>();
        /**
         * Remainder of nodes that will be connected together if in air, or to a new node if placed on ground
         */
        public final Set<TrackNode> nodesToConnect = new LinkedHashSet<>();

        // Space occupied, can't build here
        private TrackBuilderPlan() {
            this.spaceOccupied = true;
            this.isInAir = true;
            this.newNodePosition = null;
            this.newNodeOrientation = null;
        }

        public TrackBuilderPlan(Vector newNodePosition, Vector newNodeOrientation, boolean isInAir) {
            this.spaceOccupied = false;
            this.newNodePosition = newNodePosition;
            this.newNodeOrientation = newNodeOrientation;
            this.isInAir = isInAir;
        }

        public void execute(PlayerEditState state) throws ChangeCancelledException {
            if (spaceOccupied) {
                return; // No-op
            }

            TrackWorld tracks = state.getWorld().getTracks();

            // Deselect nodes that are locked (also tell player that they are locked)
            // The plan already filtered those before.
            state.deselectLockedNodes();

            // Not editing any new nodes, create a completely new coaster and node
            if (connectionsToSplit.isEmpty() && nodesToConnect.isEmpty()) {
                TrackNode newNode = tracks.createNew(newNodePosition).getNodes().get(0);
                if (newNodeOrientation != null) {
                    newNode.setOrientation(newNodeOrientation);
                }
                state.getHistory().addChangeCreateNode(state.getPlayer(), newNode);
                state.selectNode(newNode);
                return;
            }

            // Track all changes
            HistoryChange changes = state.getHistory().addChangeGroup();

            // Insert a new node in the middle of all selected connections
            // Select these created nodes
            List<TrackNode> createdConnNodes = new ArrayList<TrackNode>();
            for (TrackConnection conn : connectionsToSplit) {
                Vector newNodePos = conn.getPosition(0.5);
                Vector newNodeOri = conn.getOrientation(0.5);

                state.setEditing(conn.getNodeA(), false);
                state.setEditing(conn.getNodeB(), false);

                // Save all track objects currently on this connection before removal
                TrackObject[] objectsToRestore = TrackObject.listToArray(conn.getObjects(), true);
                double prevTotalDistance = conn.getFullDistance();

                // Remove old connection
                changes.addChangeBeforeDisconnect(state.getPlayer(), conn);
                conn.remove();

                // Create the new middle node
                TrackNode newNode = conn.getNodeA().getCoaster().createNewNode(newNodePos, newNodeOri);
                changes.addChangeCreateNode(state.getPlayer(), newNode);

                // Create the new connections, can be cancelled
                TrackConnection newConnA = tracks.connect(conn.getNodeA(), newNode);
                changes.handleEvent(new CoasterCreateConnectionEvent(state.getPlayer(), newConnA), newConnA::remove);
                TrackConnection newConnB = tracks.connect(newNode, conn.getNodeB());
                changes.handleEvent(new CoasterCreateConnectionEvent(state.getPlayer(), newConnB), newConnB::remove);

                // Compute the full distances of the two newly created connections
                // They should be half of the full distance of the connection its replacing,
                // but I'm not sure if we can guarantee that...
                double newConnALength = newConnA.getFullDistance();
                double newConnBLength = newConnB.getFullDistance();

                // Place all track objects back onto the newly created two connections
                for (TrackObject obj : objectsToRestore) {
                    // Normalize
                    double distance = obj.getDistance();
                    if (prevTotalDistance >= 1e-4) {
                        distance *= (newConnALength + newConnBLength) / prevTotalDistance;
                    }

                    // Which one?
                    if (distance >= newConnALength) {
                        distance -= newConnALength;
                        distance = Math.min(newConnBLength, distance);

                        obj.setDistanceFlippedSilently(distance, obj.isFlipped());
                        newConnB.addObject(obj);
                    } else {
                        obj.setDistanceFlippedSilently(distance, obj.isFlipped());
                        newConnA.addObject(obj);
                    }
                }

                // Add to history tracking now that the objects have been added
                changes.addChange(new HistoryChangeConnect(newConnA));
                changes.addChange(new HistoryChangeConnect(newConnB));
                createdConnNodes.add(newNode);
            }

            // For all nodes to connect, handle them having a zero-distance neighbour
            // If connected to two other nodes already, then it turns into a junction (remove the zd)
            // If not, select the zero-distance neighbour if it has no other connections (straight mode)
            List<TrackNode> nodesToConnectFixed = new ArrayList<>(nodesToConnect.size());
            for (TrackNode node : this.nodesToConnect) {
                TrackNode zd = node.getZeroDistanceNeighbour();
                if (zd == null || zd.getConnections().size() == 1) {
                    nodesToConnectFixed.add(node);
                } else if (node.getConnections().size() == 1) {
                    nodesToConnectFixed.add(zd);
                } else {
                    // Both have multiple connections, the node will turn into a junction.
                    // Make the node curved, which effectively removes the zero-distance neighbour.
                    state.makeNodeConnectionsCurved(changes, node);
                    if (!node.isRemoved()) {
                        nodesToConnectFixed.add(node);
                    } else if (!zd.isRemoved()) {
                        nodesToConnectFixed.add(zd);
                    }
                }
            }

            // When clicking on a block or when only having one node selected, create a new node
            // When more than one node is selected and the air is clicked, simply connect the selected nodes together
            if (nodesToConnectFixed.size() >= 2 && isInAir) {
                // Connect all remaining edited nodes together in the most logical fashion
                // From previous logic we already know these nodes do not share connections among them
                // This means a single 'line' must be created that connects all nodes together
                // While we can figure this out using a clever algorithm, the nodes are already sorted in the
                // order the player clicked them. Let's give the player some control back, and connect them in the
                // same order.
                TrackNode prevNode = null;
                for (TrackNode node : nodesToConnectFixed) {
                    if (prevNode != null) {
                        changes.addChangeAfterConnect(state.getPlayer(), tracks.connect(prevNode, node));
                        state.addConnectionForAnimationStates(prevNode, node);
                        state.addConnectionForAnimationStates(node, prevNode);
                    }
                    prevNode = node;
                }
            } else {
                // Add a new node connecting to existing selected nodes
                TrackNode newNode = null;
                for (TrackNode node : nodesToConnectFixed) {
                    if (newNode == null) {
                        newNode = tracks.addNode(node, newNodePosition);
                        if (newNodeOrientation != null) {
                            newNode.setOrientation(newNodeOrientation);
                        }
                        changes.addChangeCreateNode(state.getPlayer(), newNode);
                        changes.addChangeAfterConnect(state.getPlayer(), newNode, node);
                    } else {
                        changes.addChangeAfterConnect(state.getPlayer(), tracks.connect(node, newNode));
                    }
                    state.addConnectionForAnimationStates(node, newNode);
                }
                state.clearEditedNodes();
                if (newNode != null) {
                    state.setEditing(newNode, true);
                }
            }

            // Set all new split-connection nodes we created as editing, too.
            for (TrackNode node : createdConnNodes) {
                state.setEditing(node, true);
            }
        }
    }

    /**
     * Plan to execute when deleting track, or disconnecting nodes. What will be done is stored in
     * this plan, which can then be used for GUI display or execution.
     */
    public static final class TrackDeletionPlan {
        /** All connections to delete prior to actual node deletion occurs */
        public final Set<TrackConnection> connectionsToDelete = new LinkedHashSet<>();
        /** Number of connections that are deleted of which nodes are not deleted */
        public int connectionsDeletedWithoutNodeDeletion = 0;
        /** Nodes that had a zero-distance neighbour, but are now free-standing.
         * These need to be made curved again to avoid glitches.
         * Are made curved after the connections are removed, but before the nodes are deleted. */
        public final Set<TrackNode> nodesToMakeCurved = new LinkedHashSet<>();
        /** Nodes to delete. Are deleted after the connections are removed. */
        public final Set<TrackNode> nodesToDelete = new LinkedHashSet<>();
        /** Nodes to select after deletion completed */
        public final Set<TrackNode> nodesToSelect = new LinkedHashSet<>();

        public boolean isEmpty() {
            return connectionsToDelete.isEmpty() && nodesToMakeCurved.isEmpty() && nodesToDelete.isEmpty();
        }

        private boolean isDeletedOrZD(TrackNode node) {
            if (nodesToDelete.contains(node)) {
                return true;
            }
            if (nodesToMakeCurved.contains(node)) {
                TrackNode node_zd = node.getZeroDistanceNeighbour();
                if (node_zd != null && nodesToDelete.contains(node_zd)) {
                    return true;
                }
            }
            return false;
        }

        public void execute(PlayerEditState state) throws ChangeCancelledException {
            if (isEmpty()) {
                return; // No-op
            }

            // Deselect nodes we cannot delete or modify (message to player)
            // Already filtered out when plan was created.
            state.deselectLockedNodes();

            HistoryChange changes = state.getHistory().addChangeGroup();

            // Disconnect all connections of which both nodes were selected
            for (TrackConnection connection : connectionsToDelete) {
                TrackNode nodeA = connection.getNodeA();
                TrackNode nodeB = connection.getNodeB();
                changes.addChangeBeforeDisconnect(state.getPlayer(), connection);
                connection.remove();
                state.removeConnectionForAnimationStates(nodeA, nodeB);
                state.removeConnectionForAnimationStates(nodeB, nodeA);
            }

            // Make nodes that had a zero-distance neighbour curved again to avoid glitches
            for (TrackNode node : nodesToMakeCurved) {
                TrackNode node_zd = node.getZeroDistanceNeighbour();
                boolean removeAfter = nodesToDelete.contains(node) || nodesToDelete.contains(node_zd);
                state.makeNodeConnectionsCurved(changes, node);

                // This logic could remove the other node instead of this one
                nodesToDelete.remove(node);
                nodesToDelete.remove(node_zd);
                if (removeAfter) {
                    if (!node.isRemoved()) {
                        nodesToDelete.add(node);
                    }
                    if (node_zd != null && !node_zd.isRemoved()) {
                        nodesToDelete.add(node_zd);
                    }
                }
            }

            // Delete all nodes to be deleted
            for (TrackNode node : nodesToDelete) {
                changes.addChangeBeforeDeleteNode(state.getPlayer(), node);
                node.remove();
            }

            // Update selection
            state.clearEditedNodes();
            for (TrackNode node : nodesToSelect) {
                if (!node.isRemoved()) {
                    state.selectNode(node);
                }
            }
        }

        public void sendSuccessMessage(CommandSender sender) {
            if (connectionsDeletedWithoutNodeDeletion == 0) {
                sender.sendMessage("Deleted " + formatCount(nodesToDelete.size(), "track node") + "!");
            } else if (nodesToDelete.isEmpty()) {
                sender.sendMessage("Disconnected " + formatCount(connectionsDeletedWithoutNodeDeletion, "connection") + "!");
            } else {
                sender.sendMessage("Disconnected " + formatCount(connectionsDeletedWithoutNodeDeletion, "connection") +
                        " and deleted " + formatCount(nodesToDelete.size(),"track node") + "!");
            }
        }

        private static String formatCount(int count, String singular) {
            if (count == 1) {
                return count + " " + singular;
            } else {
                return count + " " + singular + "s";
            }
        }
    }
}
