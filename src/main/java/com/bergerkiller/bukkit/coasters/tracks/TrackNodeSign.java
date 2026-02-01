package com.bergerkiller.bukkit.coasters.tracks;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Predicate;
import java.util.logging.Level;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.tc.events.SignBuildEvent;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.signs.power.NamedPowerChannel;
import com.bergerkiller.bukkit.coasters.signs.power.NamedPowerChannel.Recipient;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.PowerState;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.rails.RailLookup.TrackedFakeSign;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;

/**
 * A single fake virtual sign bound to a single node.
 */
public class TrackNodeSign implements Cloneable {
    public static final TrackNodeSign[] EMPTY_ARR = new TrackNodeSign[0];

    private TrackNodeBinding binding = TrackNodeBinding.NONE;
    private TrackNodeSignKey key;
    private String[] lines;
    private NamedPowerChannel[] inputPowerChannels = NamedPowerChannel.NO_POWER_STATES;
    private NamedPowerChannel[] outputPowerChannels = NamedPowerChannel.NO_POWER_STATES;
    private TrackedFakeSign cachedFakeSign = null;

    public TrackNodeSign() {
        this(TrackNodeSignKey.random());
    }

    private TrackNodeSign(TrackNodeSignKey key) {
        this.key = key;
        this.lines = new String[] { "", "", "", "" };
    }

    public TrackNodeSign(String[] lines) {
        this.key = TrackNodeSignKey.random();
        setLines(lines);
    }

    void updateBinding(TCCoasters plugin, TrackNodeBinding newBinding) {
        if (!newBinding.isActive()) {
            this.cachedFakeSign = null; // Forces re-validation
            plugin.getSignLookup().remove(this); // Remove from lookup
        }

        if (this.binding.isActive() != newBinding.isActive()) {
            // Register/un-register the power states of this sign
            NamedPowerChannel[] inputChannels = this.inputPowerChannels;
            NamedPowerChannel[] outputChannels = this.outputPowerChannels;
            if (newBinding.isActive()) {
                for (NamedPowerChannel channel : inputChannels) {
                    channel.addRecipient(newBinding.world().getNamedPowerChannels(), Recipient.ofSignInput(this));
                }
                for (NamedPowerChannel channel : outputChannels) {
                    channel.addRecipient(newBinding.world().getNamedPowerChannels(), Recipient.ofSignOutput(this));
                }

                // Refresh rail cache when the sign is added
                invalidateCachedSignsOfNode(newBinding.node());
            }
            if (this.binding.isActive()) {
                for (NamedPowerChannel channel : inputChannels) {
                    channel.removeRecipient(this.binding.world().getNamedPowerChannels(), Recipient.ofSignInput(this));
                }
                for (NamedPowerChannel channel : outputChannels) {
                    channel.removeRecipient(this.binding.world().getNamedPowerChannels(), Recipient.ofSignOutput(this));
                }

                // Refresh rail cache when the sign is removed
                invalidateCachedSignsOfNode(this.binding.node());
            }

            // Fire destroy event when removed
            if (!newBinding.isActive()) {
                fireDestroyEvent();
            }
        }

        this.binding = newBinding;

        // Store in by-key mapping
        if (newBinding.isActive()) {
            plugin.getSignLookup().store(this);
        }
    }

    private static void invalidateCachedSignsOfNode(TrackNode node) {
        OfflineBlock railBlock = node.getOfflineWorld().getBlockAt(node.getRailBlock(true));
        RailLookup.CachedRailPiece piece = RailLookup.lookupCachedRailPieceIfCached(railBlock, node.getPlugin().getRailType());
        if (!piece.isNone()) {
            piece.forceCacheVerification();
        }
    }

    /**
     * Gets a unique key associated with this sign. This is used to preserve linkage
     * when signs transfer between animations. This is also important for persisting
     * the sign within TrainCarts own tracking system, to know what signs have
     * already been activated by trains.
     *
     * @return key
     */
    public TrackNodeSignKey getKey() {
        return this.key;
    }

    /**
     * Sets the key of this sign
     *
     * @param key
     * @see #getKey()
     */
    public void setKey(TrackNodeSignKey key) {
        this.key = key;
    }

    /**
     * Randomizes the sign key of this sign, making use of a key mapping
     * to make sure that the original keys of signs have the same random
     * new key.
     *
     * @param keyMapping Old to new key mapping
     */
    public void randomizeKey(Map<TrackNodeSignKey, TrackNodeSignKey> keyMapping) {
        setKey(keyMapping.computeIfAbsent(getKey(), u -> TrackNodeSignKey.random()));
    }

    /**
     * Gets all the lines of text of this sign
     *
     * @return lines
     */
    public String[] getLines() {
        return this.lines;
    }

    /**
     * Gets whether this sign and another sign have the exact same lines
     *
     * @param sign
     * @return True if they have the same lines
     */
    public boolean hasSameLines(TrackNodeSign sign) {
        return Arrays.equals(this.getLines(), sign.getLines());
    }

    /**
     * Gets the binding, which contains the node and if it is added as
     * an animation state.
     *
     * @return TrackNodeBinding
     */
    public TrackNodeBinding getBinding() {
        return this.binding;
    }

    /**
     * Gets the track node owning this sign, if it was added to a node or an animation
     * state of a node.
     *
     * @return node owner
     */
    public TrackNode getNode() {
        return this.binding.node();
    }

    /**
     * Gets whether this sign is part of an animation state of a node, and not the
     * node itself. If that's the case, changing the power state will have no effect.
     *
     * @return True if this sign is added to an animation state of {@link #getNode()},
     *         False otherwise.
     */
    public boolean isAddedAsAnimation() {
        return this.binding.isAddedAsAnimation();
    }

    /**
     * Sets new lines for this sign to use
     *
     * @param lines
     */
    public void setLines(String[] lines) {
        cachedFakeSign = null;
        if (lines.length < 4) {
            this.lines = Arrays.copyOf(lines, 4);
            for (int i = lines.length; i < 4; i++) {
                this.lines[i] = "";
            }
        } else {
            this.lines = lines;
        }
        notifyOwningNode();
    }

    public void appendLine(String line) {
        cachedFakeSign = null;
        this.lines = LogicUtil.appendArray(this.lines, line);
        notifyOwningNode();
    }

    /**
     * Gets whether this sign is currently receiving redstone power at all.
     *
     * @return True if powered by redstone
     */
    public boolean isPowered() {
        //TODO: Inverted mode/etc.
        for (NamedPowerChannel state : this.inputPowerChannels) {
            if (state.isPowered()) {
                return true;
            }
        }
        return false;
    }

    public NamedPowerChannel[] getInputPowerChannels() {
        return this.inputPowerChannels;
    }

    /**
     * Assigns a new named input power channel to this sign. The sign will receive power information
     * from this state, and if the sign is added to a node, the sign will be activated.
     *
     * @param name Named power state name
     * @param powered Named power state powered
     * @param face Named power state face
     */
    public void addInputPowerChannel(String name, boolean powered, BlockFace face) {
        addInputPowerChannel(NamedPowerChannel.of(name, powered, face));
    }

    /**
     * Assigns a new named input power channel to this sign. The sign will receive power information
     * from this state, and if the sign is added to a node, the sign will be activated.
     *
     * @param channel NamedPowerChannel to add
     */
    public void addInputPowerChannel(NamedPowerChannel channel) {
        NamedPowerChannel[] channels = this.inputPowerChannels;

        // Avoid adding if this exact power state already exists.
        // But we do allow adding the same name for multiple faces and such.
        for (NamedPowerChannel existingState : channels) {
            if (existingState.getName().equals(channel.getName()) &&
                existingState.getFace() == channel.getFace()
            ) {
                return;
            }
        }

        channels = LogicUtil.appendArray(channels, channel);
        this.inputPowerChannels = channels;

        if (this.binding.isActive()) {
            channel.addRecipient(this.binding.world().getNamedPowerChannels(), Recipient.ofSignInput(this));
            notifyOwningNode();
        }
    }

    /**
     * Removes all power channels of the given name from this sign
     *
     * @param name Name of the power state to remove
     * @return True if one or more power states were found and removed
     */
    public boolean removeInputPowerChannels(final String name) {
        return removeInputPowerChannels(existingState -> existingState.getName().equals(name));
    }

    /**
     * Removes a NamedPowerChannel previously added using
     * {@link #addInputPowerChannel(NamedPowerChannel)}. Both the
     * power channel name and face direction must match.
     *
     * @param channel Power channel to remove
     * @return True if found and removed
     */
    public boolean removeInputPowerChannel(final NamedPowerChannel channel) {
        return removeInputPowerChannels(existingState -> {
            return existingState.getName().equals(channel.getName()) &&
                   existingState.getFace() == channel.getFace();
        });
    }

    /**
     * Removes all named power channels added to this sign
     */
    public void clearInputPowerChannels() {
        removeInputPowerChannels(LogicUtil.alwaysTruePredicate());
    }

    /**
     * Rotates the faces of all powered channels by 90 degrees. SELF isn't rotated.
     *
     * @return True if a power channel was changed
     */
    public boolean rotatePowerChannels() {
        boolean changed = false;
        NamedPowerChannel[] states = this.inputPowerChannels;
        for (int i = 0; i < states.length; i++) {
            NamedPowerChannel state = states[i];
            if (state.getFace().getModX() != 0 || state.getFace().getModZ() != 0) {
                changed = true;
                states[i] = state.changeFace(FaceUtil.rotate(state.getFace(), 2));
            }
        }
        return changed;
    }

    /**
     * Rotates the faces of a powered channel by 90 degrees. SELF isn't rotated.
     *
     * @param channelName
     * @return True if a power channel was changed
     */
    public boolean rotatePowerChannel(String channelName) {
        boolean changed = false;
        NamedPowerChannel[] states = this.inputPowerChannels;
        for (int i = 0; i < states.length; i++) {
            NamedPowerChannel state = states[i];
            if (state.getName().equals(channelName) && state.getFace().getModX() != 0 || state.getFace().getModZ() != 0) {
                changed = true;
                states[i] = state.changeFace(FaceUtil.rotate(state.getFace(), 2));
            }
        }
        return changed;
    }

    private boolean removeInputPowerChannels(Predicate<NamedPowerChannel> filter) {
        NamedPowerChannel[] channels = this.inputPowerChannels;
        int numStates = channels.length;

        // Find it
        boolean found = false;
        for (int i = numStates-1; i >= 0; i--) {
            NamedPowerChannel existingState = channels[i];
            if (filter.test(existingState)) {
                numStates--;
                if (numStates == 0) {
                    this.inputPowerChannels = NamedPowerChannel.NO_POWER_STATES;
                } else {
                    this.inputPowerChannels = LogicUtil.removeArrayElement(channels, i);
                }
                if (this.binding.isActive()) {
                    existingState.removeRecipient(this.binding.world().getNamedPowerChannels(), Recipient.ofSignInput(this));
                    notifyOwningNode();
                }
                found = true;
            }
        }
        return found;
    }

    public NamedPowerChannel[] getOutputPowerChannels() {
        return this.outputPowerChannels;
    }

    public void addOutputPowerChannel(String name, boolean initialPowered) {
        addOutputPowerChannel(NamedPowerChannel.of(name, initialPowered, BlockFace.SELF));
    }

    public void addOutputPowerChannel(NamedPowerChannel output) {
        NamedPowerChannel[] channels = this.outputPowerChannels;

        // Check already exists
        for (NamedPowerChannel channel : channels) {
            if (channel.getName().equals(output.getName())) {
                return;
            }
        }

        // Add
        channels = LogicUtil.appendArray(channels, output);
        this.outputPowerChannels = channels;

        if (this.binding.isActive()) {
            output.addRecipient(this.binding.world().getNamedPowerChannels(), Recipient.ofSignOutput(this));
            notifyOwningNode();
        }
    }

    /**
     * Removes all named output power channels added to this sign
     */
    public void clearOutputPowerChannels() {
        removeOutputPowerChannels(LogicUtil.alwaysTruePredicate());
    }

    /**
     * Removes all output power channels of the given name from this sign
     *
     * @param name Name of the power state to remove
     * @return True if one or more power states were found and removed
     */
    public boolean removeOutputPowerChannel(final String name) {
        return removeOutputPowerChannels(existingState -> existingState.getName().equals(name));
    }

    /**
     * Removes a NamedPowerChannel previously added using
     * {@link #addOutputPowerChannel(NamedPowerChannel)}. The power channel
     * name must match.
     *
     * @param channel Power channel to remove
     * @return True if found and removed
     */
    public boolean removeOutputPowerChannel(final NamedPowerChannel channel) {
        return removeOutputPowerChannel(channel.getName());
    }

    private boolean removeOutputPowerChannels(Predicate<NamedPowerChannel> filter) {
        NamedPowerChannel[] channels = this.outputPowerChannels;
        int numStates = channels.length;

        // Find it
        boolean found = false;
        for (int i = numStates-1; i >= 0; i--) {
            NamedPowerChannel existingState = channels[i];
            if (filter.test(existingState)) {
                numStates--;
                if (numStates == 0) {
                    this.outputPowerChannels = NamedPowerChannel.NO_POWER_STATES;
                } else {
                    this.outputPowerChannels = LogicUtil.removeArrayElement(channels, i);
                }
                if (this.binding.isActive()) {
                    existingState.removeRecipient(this.binding.world().getNamedPowerChannels(), Recipient.ofSignOutput(this));
                    notifyOwningNode();
                }
                found = true;
            }
        }
        return found;
    }

    /**
     * If this sign contains output power states, checks that the given
     * sender has permission to change those power states.
     *
     * @param sender
     * @throws ChangeCancelledException If sender lacks permission
     */
    public void checkPowerPermissions(CommandSender sender) throws ChangeCancelledException {
        for (NamedPowerChannel channel : getOutputPowerChannels()) {
            if (!channel.checkPermission(sender)) {
                throw new ChangeCancelledException();
            }
        }
    }

    private void notifyOwningNode() {
        if (this.binding.node() != null) {
            this.binding.node().updateSignParticle();
            this.binding.node().markChanged();
        }
    }

    /**
     * Gets whether this node sign has a tracked sign. If this sign is not added to a node,
     * or is only stored as an animation state, returns false.
     *
     * @return True if this node sign has a tracked sign
     */
    public boolean hasTrackedSign() {
        return this.binding.isActive();
    }

    /**
     * Gets or calculates a TrackedSign that represents the information on this fake sign.
     * This sign must be added to a node first.
     *
     * @return Tracked fake sign
     */
    public TrackedFakeSign getTrackedSign() {
        binding.assertActive("sign");

        TrackNode node = binding.node();
        return getTrackedSign(RailPiece.create(node.getPlugin().getRailType(),
                BlockUtil.getBlock(node.getBukkitWorld(), node.getRailBlock(true))));
    }

    /**
     * Gets or calculates a TrackedSign that represents the information on this fake sign.
     * This sign must be added to a node first.
     *
     * @param rail Rail Piece
     * @return Tracked fake sign
     */
    public TrackedFakeSign getTrackedSign(final RailPiece rail) {
        binding.assertActive("sign");

        TrackedFakeSign cached = cachedFakeSign;
        if (cached == null || !cached.getRail().isSameBlock(rail)) {
            final TrackNode node = binding.node();
            cachedFakeSign = cached = new TrackedFakeSign(rail) {
                @Override
                public String getLine(int index) throws IndexOutOfBoundsException {
                    return lines[index];
                }

                @Override
                public void setLine(int index, String line) throws IndexOutOfBoundsException {
                    lines[index] = line;
                    node.updateSignParticle();
                    node.markChanged();
                }

                @Override
                public Object getUniqueKey() {
                    return key;
                }

                @Override
                public boolean verify() {
                    return cachedFakeSign == this &&
                           !node.isRemoved() &&
                           getRail().blockPosition().equals(node.getRailBlock(true));
                }

                @Override
                public boolean isRemoved() {
                    return cachedFakeSign != this || node.isRemoved();
                }

                @Override
                public BlockFace getFacing() {
                    return BlockFace.NORTH;
                }

                @Override
                public Block getAttachedBlock() {
                    return this.getRail().block();
                }

                @Override
                public void setOutput(boolean output) {
                    for (NamedPowerChannel channel : outputPowerChannels) {
                        channel.setPowered(output);
                    }
                }

                @Override
                public String[] getExtraLines() {
                    String[] l = lines;
                    return l.length <= 4 ? StringUtil.EMPTY_ARRAY : Arrays.copyOfRange(l, 4, l.length);
                }

                @Override
                public PowerState getPower(BlockFace from) {
                    boolean facePowered = false;
                    boolean selfPowered = false;
                    boolean foundFace = false;
                    boolean foundSelf = false;

                    for (NamedPowerChannel state : inputPowerChannels) {
                        if (state.getFace() == from) {
                            facePowered |= state.isPowered();
                            foundFace = true;
                        }
                        if (state.getFace() == BlockFace.SELF) {
                            selfPowered |= state.isPowered();
                            foundSelf = true;
                        }
                    }

                    if (foundFace) {
                        return facePowered ? PowerState.ON : PowerState.OFF;
                    }
                    if (foundSelf) {
                        return selfPowered ? PowerState.ON : PowerState.OFF;
                    }
                    return PowerState.NONE;
                }
            };
        }
        return cached;
    }

    /**
     * Fires a sign destroy event. This tells the sign action implementation that this
     * sign has been removed. Sign Actions can handle this event and do suitable cleanup.
     * This sign must have been added to a track node prior.
     */
    public void fireDestroyEvent() {
        RailLookup.TrackedSign trackedSign = getTrackedSign();
        TCCoasters plugin = getNode().getPlugin();

        try {
            SignAction.handleDestroy(new SignActionEvent(trackedSign));
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Failed to handle sign destroy event", t);
            plugin.getLogger().severe("Sign Lines: " + Arrays.asList(lines));
        }
    }

    /**
     * Fires a sign build event. Displays the type of sign placed to the player.
     * If the player lacks permissions to place this type of sign, returns false.
     * This sign must have been added to a track node prior.
     *
     * @param player Player that built it
     * @param interactive Whether a successful build message should be shown
     * @return True if building was/is permitted
     */
    public boolean fireBuildEvent(Player player, boolean interactive) {
        RailLookup.TrackedSign trackedSign = getTrackedSign();
        TCCoasters plugin = getNode().getPlugin();

        try {
            // Fire a sign build event with the sign's custom sign
            SignBuildEvent event = new SignBuildEvent(player, trackedSign, interactive);
            SignAction.handleBuild(event);
            return !event.isCancelled();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Failed to handle sign build event", t);
            plugin.getLogger().severe("Sign Lines: " + Arrays.asList(lines));
            player.sendMessage(ChatColor.RED + "An internal error occurred trying to place a sign on a node. "
                    + "See server log for more details.");
            return false;
        }
    }

    /**
     * Calls the SignAction loadedChanged callback.
     *
     * @param loaded Whether the sign is now loaded
     */
    public void handleLoadChange(boolean loaded) {
        SignAction.handleLoadChange(getTrackedSign(), loaded);
    }

    /**
     * Fires a generic action event for the sign. Typically used for redstone changes.
     * This sign must have been added to a track node prior.
     *
     * @param type Sign Action Type
     */
    public void fireActionEvent(SignActionType type) {
        TrackedFakeSign trackedSign = getTrackedSign();
        SignActionEvent event = trackedSign.createEvent(type);
        SignAction.executeOne(trackedSign.getAction(), event);
    }

    /**
     * Fires a REDSTONE_ON or REDSTONE_OFF event depending on the type of sign header
     * is used.
     *
     * @param newPowerState
     */
    public void fireRedstoneEvent(boolean newPowerState) {
        TrackedFakeSign trackedSign = getTrackedSign();
        SignActionType type = trackedSign.getHeader().getRedstoneAction(newPowerState);
        if (type != SignActionType.NONE) {
            SignActionEvent event = trackedSign.createEvent(type);
            SignAction.executeOne(trackedSign.getAction(), event);
        }
    }

    /**
     * Called when the powered state changes, before any redstone events
     * are fired. Also called when a pulsed delay is added or removed.
     */
    public void onPowerChanged() {
        notifyOwningNode();
    }

    @Override
    public TrackNodeSign clone() {
        TrackNodeSign clone = new TrackNodeSign(this.key);
        clone.lines = this.lines.clone();
        clone.inputPowerChannels = LogicUtil.cloneAll(this.inputPowerChannels, NamedPowerChannel::clone);
        clone.outputPowerChannels = LogicUtil.cloneAll(this.outputPowerChannels, NamedPowerChannel::clone);
        return clone;
    }

    /**
     * Active handler for the building new signs. Permission checks happen here.
     */
    @FunctionalInterface
    public interface SignBuildHandler {
        /**
         * Handles the building of the sign in an interactive fashion, showing a
         *
         * @param player Player that built the sign
         * @param sign Sign placed by the player
         * @param interactive Whether the build is interactive and positive build messages
         *                    with information should be displayed
         * @return True if the building was successful, False if not or permission issues occurred
         */
        boolean handleBuild(Player player, RailLookup.TrackedSign sign, boolean interactive);
    }
}
