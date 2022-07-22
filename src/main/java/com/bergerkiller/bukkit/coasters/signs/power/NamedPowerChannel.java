package com.bergerkiller.bukkit.coasters.signs.power;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeSign;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;

/**
 * Power state of a sign, mapped to a name so that power state
 * can be shared with multiple signs.
 */
public class NamedPowerChannel implements Cloneable {
    public static final NamedPowerChannel[] NO_POWER_STATES = new NamedPowerChannel[0];
    private NamedPowerState state;
    private final BlockFace face;

    NamedPowerChannel(NamedPowerState state, BlockFace face) {
        this.state = state;
        this.face = face;
    }

    /**
     * Creates a named power channel. It can later be registered with a sign,
     * after which it's powered state is shared with other signs on the same
     * world.
     *
     * @param name State name
     * @param powered State powered
     * @param face Face direction of the sign being powered
     * @return SignNamedPowerState
     */
    public static NamedPowerChannel of(String name, boolean powered, BlockFace face) {
        return new NamedPowerChannel(new NamedPowerState(name, powered), face);
    }

    /**
     * Combines multiple sign named power channel values into a single addressable state.
     * Reading powered state results in OR-ing the state power states together. Setting
     * a new power state updates all underlying power states.
     *
     * @param values Values to combine
     * @return combined power states. If values list is empty, returns null.
     */
    public static NamedPowerChannel multiple(Collection<NamedPowerChannel> values) {
        if (values.isEmpty()) {
            // No values, assume null. Typically shouldn't happen.
            return null;
        }

        NamedPowerChannel first = values.iterator().next();
        if (values.size() == 1) {
            // Singleton result
            return first;
        }

        List<NamedPowerState> states = new ArrayList<>(values.size());
        for (NamedPowerChannel value : values) {
            states.add(value.state);
        }

        return new NamedPowerChannel(new NamedPowerStateMultiple(first.getName(), states), first.getFace());
    }

    /**
     * Gets the name of the power state
     *
     * @return Power state name
     */
    public String getName() {
        return state.getName();
    }

    /**
     * Gets the current power state
     *
     * @return Power state
     */
    public boolean isPowered() {
        return state.isPowered();
    }

    /**
     * Gets the BlockFace face of the sign being powered by this state.
     * If SELF, then all faces of the sign are powered.
     * For power states retrieved from the registry, the face will always
     * be SELF, as it is not relevant.
     *
     * @return Face being powered
     */
    public BlockFace getFace() {
        return this.face;
    }

    /**
     * Changes what BlockFace this power channel powers. Does not change
     * the underlying registration state.
     *
     * @param face
     * @return updated channel
     * @see #getFace()
     */
    public NamedPowerChannel changeFace(BlockFace face) {
        return new NamedPowerChannel(this.state, face);
    }

    /**
     * Sets the current power state. If this named state was registered,
     * it changes the power state of (all) these registered signs.
     *
     * @param powered New power state
     */
    public void setPowered(boolean powered) {
        state.setPowered(powered);
    }

    /**
     * Sets the current power states, and after the delay, sets it back
     * to the opposite power state. This method is only supported for named
     * power channels that have signs associated with them.
     *
     * @param powered Power state to set right now
     * @param delay Tick delay until powered state is set back to the opposite of
     *              powered
     */
    public void pulsePowered(boolean powered, int delay) {
        state.pulsePowered(powered, delay);
    }

    /**
     * Gets the number of remaining ticks until the power state is inverted
     * automatically, because of using {@link #pulsePowered(boolean, int)}.
     * If -1, there is no ongoing pulse.
     *
     * @return Pulse delay in ticks, or -1 if there is no pulse
     */
    public int getPulseDelay() {
        return state.getPulseDelay();
    }

    /**
     * Gets whether a power pulse is scheduled
     *
     * @return True if a pulse is scheduled
     * @see #getPulseDelay()
     */
    public boolean hasPulse() {
        return state.hasPulse();
    }

    /**
     * Registers a recipient of this named power channel.
     * If this state had no prior recipients, the named state becomes globally
     * registered.
     *
     * @param power Power registry to register inside of
     * @param recipient Recipient to register
     */
    public void addRecipient(NamedPowerChannelRegistry power, Recipient recipient) {
        state = state.addRecipient(power, recipient);
    }

    /**
     * Un-registers a recipient previously registered using {@link #addRecipient(Recipient)}.
     * If this was the last recipient, then the power channel is globally
     * un-registered and the power state information is lost.
     *
     * @param power Power registry to un-register inside of
     * @param recipient Recipient to un-register
     */
    public void removeRecipient(NamedPowerChannelRegistry power, Recipient recipient) {
        state.removeRecipient(power, recipient);
    }

    /**
     * Gets the text displayed on the tooltip sign particle for this power channel as an input
     *
     * @return tooltip text
     */
    public String getInputTooltipText() {
        StringBuilder str = new StringBuilder(64);
        if (isPowered()) {
            str.append(ChatColor.RED).append('\u2726');
            str.append(ChatColor.BLUE);
        } else {
            str.append(ChatColor.GRAY).append('\u2727');
        }
        str.append(this.getFace() == BlockFace.SELF
                ? '*' : this.getFace().name().charAt(0));
        str.append(' ').append(ChatColor.WHITE).append(getName());
        if (hasPulse()) {
            str.append(ChatColor.RED).append(" \u231B");
        }
        return str.toString();
    }

    /**
     * Gets the text displayed on the tooltip sign particle for this power channel as an output
     *
     * @return tooltip text
     */
    public String getOutputTooltipText() {
        StringBuilder str = new StringBuilder(64);
        if (isPowered()) {
            str.append(ChatColor.YELLOW).append('\u25A0');
        } else {
            str.append(ChatColor.GRAY).append('\u25A1');
        }
        str.append(' ').append(ChatColor.WHITE).append(getName());
        return str.toString();
    }

    @Override
    public NamedPowerChannel clone() {
        return new NamedPowerChannel(state, face);
    }

    @Override
    public String toString() {
        return "Channel{name=" + getName() + ", powered=" + isPowered() + "}";
    }

    /**
     * Receives notifications about power state changes of a named power channel
     */
    public static interface Recipient {
        /**
         * Called right before a powered state change occurs
         */
        void onPreChange();

        /**
         * Called right after a new powered state change completes
         *
         * @param powered New powered state
         */
        void onPostChange(boolean powered);

        /**
         * Called when the named channel changes power state, or starts or stops
         * a pulse.
         */
        void onChanged();

        public static Recipient ofSignInput(TrackNodeSign sign) {
            return new TrackNodeSignRecipientInput(sign);
        }

        public static Recipient ofSignOutput(TrackNodeSign sign) {
            return new TrackNodeSignRecipientOutput(sign);
        }
    }

    private static class TrackNodeSignRecipientInput implements Recipient {
        public final TrackNodeSign sign;
        public boolean wasPowered; // used for event handling

        private TrackNodeSignRecipientInput(TrackNodeSign sign) {
            this.sign = sign;
        }

        @Override
        public void onPreChange() {
            wasPowered = sign.isPowered();
        }

        @Override
        public void onChanged() {
            sign.onPowerChanged();
        }

        @Override
        public void onPostChange(boolean powered) {
            TrackNode node = sign.getNode();
            if (node == null) {
                return;
            }
            node.markChanged(); // Power state changed, important to re-save CSV
            if (sign.isAddedAsAnimation()) {
                return;
            }

            boolean isPowered = sign.isPowered();
            if (isPowered != wasPowered) {
                // Fire REDSTONE_ON or REDSTONE_OFF event depending on type of transition
                sign.fireRedstoneEvent(isPowered);
            }

            // Fire REDSTONE_CHANGE event
            sign.fireActionEvent(SignActionType.REDSTONE_CHANGE);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof TrackNodeSignRecipientInput && ((TrackNodeSignRecipientInput) o).sign == this.sign;
        }
    }

    // Purely keeps the power state around/listable. Doesn't respond.
    private static class TrackNodeSignRecipientOutput implements Recipient {
        public final TrackNodeSign sign;

        private TrackNodeSignRecipientOutput(TrackNodeSign sign) {
            this.sign = sign;
        }

        @Override
        public void onPreChange() {
        }

        @Override
        public void onPostChange(boolean powered) {
            TrackNode node = sign.getNode();
            if (node == null) {
                return;
            }
            node.markChanged(); // Power state changed, important to re-save CSV
        }

        @Override
        public void onChanged() {
            sign.onPowerChanged();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof TrackNodeSignRecipientOutput && ((TrackNodeSignRecipientOutput) o).sign == this.sign;
        }
    }

    static class NamedPowerState {
        private final String name;
        private boolean powered;

        public NamedPowerState(String name, boolean powered) {
            this.name = name;
            this.powered = powered;
        }

        public String getName() {
            return this.name;
        }

        public boolean isPowered() {
            return this.powered;
        }

        public void setPowered(boolean powered) {
            this.powered = powered;
        }

        public void pulsePowered(boolean powered, int delay) {
            throw new UnsupportedOperationException("Only registered named power channels support pulsing");
        }

        public int getPulseDelay() {
            return -1;
        }

        public boolean hasPulse() {
            return false;
        }

        public NamedPowerState addRecipient(NamedPowerChannelRegistry power, Recipient recipient) {
            return power.createState(getName(), isPowered(), recipient);
        }

        public void removeRecipient(NamedPowerChannelRegistry power, Recipient recipient) {
            // Not registered - no-op
        }
    }

    static class NamedPowerStateMultiple extends NamedPowerState {
        private final Collection<NamedPowerState> states;

        public NamedPowerStateMultiple(String name, Collection<NamedPowerState> states) {
            super(name, true);
            this.states = states;
        }

        @Override
        public boolean isPowered() {
            for (NamedPowerState state : states) {
                if (state.isPowered()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void setPowered(boolean powered) {
            for (NamedPowerState state : states) {
                state.setPowered(powered);
            }
        }

        @Override
        public void pulsePowered(boolean powered, int delay) {
            for (NamedPowerState state : states) {
                state.pulsePowered(powered, delay);
            }
        }

        @Override
        public int getPulseDelay() {
            int min = -1;
            for (NamedPowerState state : states) {
                int delay = state.getPulseDelay();
                if (delay != -1 && (min == -1 || delay < min)) {
                    min = delay;
                }
            }
            return min;
        }

        @Override
        public boolean hasPulse() {
            for (NamedPowerState state : states) {
                if (state.hasPulse()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public NamedPowerState addRecipient(NamedPowerChannelRegistry power, Recipient recipient) {
            throw new UnsupportedOperationException("Can't register recipients to multiple power states");
        }
    }
}
