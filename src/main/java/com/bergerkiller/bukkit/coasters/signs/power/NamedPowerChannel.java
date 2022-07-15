package com.bergerkiller.bukkit.coasters.signs.power;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.coasters.tracks.TrackNodeSign;

import net.md_5.bungee.api.ChatColor;

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
     * Registers a sign as a recipient of this named power channel.
     * If this state had no prior recipients, the named state becomes globally
     * registered.
     *
     * @param power Power registry to register inside of
     * @param sign Sign to register
     */
    public void register(NamedPowerChannelRegistry power, TrackNodeSign sign) {
        state = state.register(power, sign);
    }

    /**
     * Un-registers a sign previously registered using {@link #register(TrackNodeSign)}.
     * If this was the last sign that was a recipient, then the power state is globally
     * un-registered and the power state information is lost.
     *
     * @param power Power registry to un-register inside of
     * @param sign Sign to un-register
     */
    public void unregister(NamedPowerChannelRegistry power, TrackNodeSign sign) {
        state.unregister(power, sign);
    }

    /**
     * Gets the text displayed on the tooltip sign particle for this power channel
     *
     * @return tooltip text
     */
    public String getTooltipText() {
        StringBuilder str = new StringBuilder(64);
        if (hasPulse()) {
            str.append(ChatColor.WHITE).append('\u231B');
        }
        if (isPowered()) {
            str.append(ChatColor.RED).append('\u2726');
            str.append(ChatColor.BLUE);
        } else {
            str.append(ChatColor.GRAY).append('\u2727');
        }
        str.append(this.getFace() == BlockFace.SELF
                ? '*' : this.getFace().name().charAt(0));
        str.append(' ').append(ChatColor.WHITE).append(getName());
        return str.toString();
    }

    @Override
    public NamedPowerChannel clone() {
        return new NamedPowerChannel(state, face);
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

        public NamedPowerState register(NamedPowerChannelRegistry power, TrackNodeSign sign) {
            return power.register(getName(), isPowered(), sign);
        }

        public void unregister(NamedPowerChannelRegistry power, TrackNodeSign sign) {
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
        public NamedPowerState register(NamedPowerChannelRegistry power, TrackNodeSign sign) {
            throw new UnsupportedOperationException("Can't register signs to multiple power states");
        }
    }
}
