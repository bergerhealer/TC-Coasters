package com.bergerkiller.bukkit.coasters.tracks;

import java.util.Arrays;
import java.util.function.Predicate;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.coasters.signs.power.NamedPowerChannel;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.PowerState;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.rails.RailLookup.TrackedFakeSign;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;

/**
 * A single fake virtual sign bound to a single node.
 */
public class TrackNodeSign implements Cloneable {
    public static final TrackNodeSign[] EMPTY_ARR = new TrackNodeSign[0];

    private final Object key;
    private String[] lines;
    private NamedPowerChannel[] powerStates = NamedPowerChannel.NO_POWER_STATES;
    private TrackedFakeSign cachedFakeSign = null;

    // Registration state
    private TrackNode nodeOwner;
    private boolean addedAsAnimation;

    public TrackNodeSign() {
        this(new Object());
    }

    private TrackNodeSign(Object key) {
        this.key = key;
        this.lines = new String[] { "", "", "", "" };
    }

    public TrackNodeSign(String[] lines) {
        this.key = new Object();
        setLines(lines);
    }

    void updateOwner(TrackNode node, boolean addedAsAnimation) {
        if (node == null || addedAsAnimation) {
            this.cachedFakeSign = null; // Forces re-validation
        }

        // Register/un-register the power states of this sign
        {
            NamedPowerChannel[] states = this.powerStates;
            if (states.length > 0 && this.nodeOwner != node) {
                if (node != null) {
                    for (NamedPowerChannel state : states) {
                        state.register(node.getWorld().getNamedPowerChannels(), this);
                    }
                }
                if (this.nodeOwner != null) {
                    for (NamedPowerChannel state : states) {
                        state.unregister(this.nodeOwner.getWorld().getNamedPowerChannels(), this);
                    }
                }
            }
        }

        this.nodeOwner = node;
        this.addedAsAnimation = addedAsAnimation;
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
     * Gets the track node owning this sign, if it was added to a node or an animation
     * state of a node.
     *
     * @return node owner
     */
    public TrackNode getNode() {
        return this.nodeOwner;
    }

    /**
     * Gets whether this sign is part of an animation state of a node, and not the
     * node itself. If that's the case, changing the power state will have no effect.
     *
     * @return True if this sign is added to an animation state of {@link #getNode()},
     *         False otherwise.
     */
    public boolean isAddedAsAnimation() {
        return this.addedAsAnimation;
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
        int len = this.lines.length;
        this.lines = Arrays.copyOf(this.lines, len + 1);
        this.lines[len] = line;
        notifyOwningNode();
    }

    /**
     * Gets whether this sign is currently receiving redstone power at all.
     *
     * @return True if powered by redstone
     */
    public boolean isPowered() {
        //TODO: Inverted mode/etc.
        for (NamedPowerChannel state : this.powerStates) {
            if (state.isPowered()) {
                return true;
            }
        }
        return false;
    }

    public NamedPowerChannel[] getPowerChannels() {
        return this.powerStates;
    }

    /**
     * Assigns a new named power channel to this sign. The sign will receive power information
     * from this state, and if the sign is added to a node, the sign will be activated.
     *
     * @param name Named power state name
     * @param powered Named power state powered
     * @param face Named power state face
     */
    public void addPowerChannel(String name, boolean powered, BlockFace face) {
        addPowerChannel(NamedPowerChannel.of(name, powered, face));
    }

    /**
     * Assigns a new named power channel to this sign. The sign will receive power information
     * from this state, and if the sign is added to a node, the sign will be activated.
     *
     * @param powerState SignNamedPowerState to add
     */
    public void addPowerChannel(NamedPowerChannel powerState) {
        NamedPowerChannel[] states = this.powerStates;
        int numStates = states.length;

        // Avoid adding if this exact power state already exists.
        // But we do allow adding the same name for multiple faces and such.
        for (NamedPowerChannel existingState : states) {
            if (existingState.getName().equals(powerState.getName()) &&
                existingState.getFace() == powerState.getFace()
            ) {
                return;
            }
        }

        states = Arrays.copyOf(states, numStates + 1);
        states[numStates] = powerState;
        this.powerStates = states;

        if (this.nodeOwner != null) {
            powerState.register(this.nodeOwner.getWorld().getNamedPowerChannels(), this);
            notifyOwningNode();
        }
    }

    /**
     * Removes all power states of the given name from this sign
     *
     * @param name Name of the power state to remove
     * @return True if one or more power states were found and removed
     */
    public boolean removePowerChannels(final String name) {
        return removePowerChannels(existingState -> existingState.getName().equals(name));
    }

    /**
     * Removes a SignNamedPowerState previously added using
     * {@link #addPowerChannel(NamedPowerChannel)}. Both the
     * power state name and face direction must match.
     *
     * @param powerState Power state to remove
     * @return True if found and removed
     */
    public boolean removePowerChannel(final NamedPowerChannel powerState) {
        return removePowerChannels(existingState -> {
            return existingState.getName().equals(powerState.getName()) &&
                   existingState.getFace() == powerState.getFace();
        });
    }

    /**
     * Removes all named power channels added to this sign
     */
    public void clearPowerChannels() {
        removePowerChannels(LogicUtil.alwaysTruePredicate());
    }

    /**
     * Rotates the faces of all powered channels by 90 degrees. SELF isn't rotated.
     *
     * @return True if a power channel was changed
     */
    public boolean rotatePowerChannels() {
        boolean changed = false;
        NamedPowerChannel[] states = this.powerStates;
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
        NamedPowerChannel[] states = this.powerStates;
        for (int i = 0; i < states.length; i++) {
            NamedPowerChannel state = states[i];
            if (state.getName().equals(channelName) && state.getFace().getModX() != 0 || state.getFace().getModZ() != 0) {
                changed = true;
                states[i] = state.changeFace(FaceUtil.rotate(state.getFace(), 2));
            }
        }
        return changed;
    }

    private boolean removePowerChannels(Predicate<NamedPowerChannel> filter) {
        NamedPowerChannel[] states = this.powerStates;
        int numStates = states.length;

        // Find it
        boolean found = false;
        for (int i = numStates-1; i >= 0; i--) {
            NamedPowerChannel existingState = states[i];
            if (filter.test(existingState)) {
                numStates--;
                if (numStates == 0) {
                    this.powerStates = NamedPowerChannel.NO_POWER_STATES;
                } else {
                    this.powerStates = LogicUtil.removeArrayElement(states, i);
                }
                if (this.nodeOwner != null) {
                    existingState.unregister(this.nodeOwner.getWorld().getNamedPowerChannels(), this);
                    notifyOwningNode();
                }
                found = true;
            }
        }
        return found;
    }

    private void notifyOwningNode() {
        if (this.nodeOwner != null) {
            this.nodeOwner.updateSignParticle();
            this.nodeOwner.markChanged();
        }
    }

    /**
     * Gets or calculates a TrackedSign that represents the information on this fake sign.
     * This sign must be added to a node first.
     *
     * @return Tracked fake sign
     */
    public TrackedFakeSign getTrackedSign() {
        final TrackNode node = this.getNode();
        if (node == null || addedAsAnimation) {
            throw new IllegalStateException("This sign is not associated with a track node");
        }

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
        final TrackNode node = this.getNode();
        if (node == null || addedAsAnimation) {
            throw new IllegalStateException("This sign is not associated with a track node");
        }

        TrackedFakeSign cached = cachedFakeSign;
        if (cached == null || !cached.rail.isSameBlock(rail)) {
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
                           rail.blockPosition().equals(node.getRailBlock(true));
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
                    return this.rail.block();
                }

                @Override
                public String[] getExtraLines() {
                    String[] l = lines;
                    return l.length <= 4 ? StringUtil.EMPTY_ARRAY : Arrays.copyOfRange(l, 4, l.length - 4);
                }

                @Override
                public PowerState getPower(BlockFace from) {
                    boolean facePowered = false;
                    boolean selfPowered = false;
                    boolean foundFace = false;
                    boolean foundSelf = false;

                    for (NamedPowerChannel state : powerStates) {
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
     * Fires a sign build event. Displays the type of sign places to the player.
     * If the player lacks permissions to place this type of sign, returns false.
     * This sign must have been added to a track node prior.
     *
     * @param player
     * @return True if building was/is permitted
     */
    public boolean fireBuildEvent(Player player) {
        // Fire a sign build event with the sign's custom sign
        TrackedFakeSign trackedSign = getTrackedSign();
        SignChangeActionEvent event = new SignChangeActionEvent(player, trackedSign);
        SignAction.handleBuild(event);
        return !event.isCancelled();
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

        {
            NamedPowerChannel[] states = this.powerStates;
            int numStates = states.length;
            if (numStates > 0) {
                clone.powerStates = new NamedPowerChannel[numStates];
                for (int i = 0; i < numStates; i++) {
                    clone.powerStates[i] = states[i].clone();
                }
            }
        }

        return clone;
    }

    public static TrackNodeSign[] appendToArray(TrackNodeSign[] arr, TrackNodeSign sign) {
        int len = arr.length;
        TrackNodeSign[] new_arr = Arrays.copyOf(arr, len + 1);
        new_arr[len] = sign;
        return new_arr;
    }
}
