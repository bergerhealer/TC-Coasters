package com.bergerkiller.bukkit.coasters.tracks;

import java.util.Arrays;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.PowerState;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.rails.RailLookup.TrackedFakeSign;
import com.bergerkiller.bukkit.tc.signactions.SignAction;

/**
 * A single fake virtual sign bound to a single node.
 */
public class TrackNodeSign implements Cloneable {
    public static final TrackNodeSign[] EMPTY_ARR = new TrackNodeSign[0];

    private String[] lines;
    private SignPowerState[] powerStates = SignPowerState.NO_STATES;
    private TrackedFakeSign cachedFakeSign = null;

    public TrackNodeSign() {
        this.lines = new String[] { "", "", "", "" };
    }

    public TrackNodeSign(String[] lines) {
        setLines(lines);
    }

    public void onAdded() {
        
    }

    public void onRemoved() {
        this.cachedFakeSign = null; // Forces re-validation
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
    }

    /**
     * Gets whether this sign is currently receiving redstone power at all.
     *
     * @return True if powered by redstone
     */
    public boolean isPowered() {
        //TODO: Inverted mode/etc.
        for (SignPowerState state : this.powerStates) {
            if (state.power.hasPower()) {
                return true;
            }
        }
        return false;
    }

    public SignPowerState[] getPowerStates() {
        return this.powerStates;
    }

    /**
     * Sets a new power state for/from a particular face of this Sign.
     * Returns true when the face power state (powered) changed between on and off.
     * If true, a REDSTONE_CHANGE event should be fired.
     *
     * @param face Face of the sign
     * @param power New power state
     * @return True if the power state of this face changed
     */
    public boolean setPowerState(BlockFace face, PowerState power) {
        SignPowerState[] states = this.powerStates;
        int numStates = states.length;
        for (int i = 0; i < numStates; i++) {
            SignPowerState state = states[i];
            if (state.face == face) {
                boolean changed = state.power.hasPower() != power.hasPower();
                states[i] = new SignPowerState(face, power);
                return changed;
            }
        }
        this.powerStates = states = Arrays.copyOf(states, numStates + 1);
        states[numStates] = new SignPowerState(face, power);
        return power.hasPower(); // NONE -> power?
    }

    /**
     * Gets or calculates a TrackedSign that represents the information on this fake sign
     *
     * @param rail Rail Piece
     * @param node Node this fake sign is tied to currently
     * @return Tracked fake sign
     */
    public TrackedFakeSign getTrackedSign(final RailPiece rail, final TrackNode node) {
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
                    node.markChanged();
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
                    for (SignPowerState state : powerStates) {
                        if (state.face == from) {
                            return state.power;
                        }
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
     *
     * @param player
     * @param node
     * @return True if building was/is permitted
     */
    public boolean fireBuildEvent(Player player, TrackNode node) {
        // Fire a sign build event with the sign's custom sign
        TrackedFakeSign oldCached = this.cachedFakeSign;
        TrackedFakeSign trackedSign = getTrackedSign(
                RailPiece.create(node.getPlugin().getRailType(),
                                 BlockUtil.getBlock(node.getBukkitWorld(), node.getRailBlock(true))),
                node);
        this.cachedFakeSign = oldCached; // Restore
        SignChangeActionEvent event = new SignChangeActionEvent(player, trackedSign);
        SignAction.handleBuild(event);
        return !event.isCancelled();
    }

    @Override
    public TrackNodeSign clone() {
        TrackNodeSign clone = new TrackNodeSign();
        clone.lines = this.lines.clone();
        clone.powerStates = this.powerStates.clone();
        clone.cachedFakeSign = null;
        return clone;
    }

    public static TrackNodeSign[] appendToArray(TrackNodeSign[] arr, TrackNodeSign sign) {
        int len = arr.length;
        TrackNodeSign[] new_arr = Arrays.copyOf(arr, len + 1);
        new_arr[len] = sign;
        return new_arr;
    }

    public static class SignPowerState {
        private static final SignPowerState[] NO_STATES = new SignPowerState[0];
        public final BlockFace face;
        public final PowerState power;

        public SignPowerState(BlockFace face, PowerState power) {
            this.face = face;
            this.power = power;
        }
    }
}
