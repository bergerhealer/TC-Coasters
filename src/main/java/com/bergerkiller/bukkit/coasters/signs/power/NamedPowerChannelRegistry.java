package com.bergerkiller.bukkit.coasters.signs.power;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.coasters.signs.power.NamedPowerChannel.NamedPowerState;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeSign;
import com.bergerkiller.bukkit.coasters.world.CoasterWorld;
import com.bergerkiller.bukkit.coasters.world.CoasterWorldComponent;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;

/**
 * Tracks the named redstone power states for all signs on a
 * world.
 */
public class NamedPowerChannelRegistry implements CoasterWorldComponent {
    private final CoasterWorld world;
    private final Map<String, SignRegisteredNamedPowerState> byName = new HashMap<>();
    private final TreeSet<String> names = new TreeSet<String>();
    private List<String> namesCopy = null; // More efficient

    public NamedPowerChannelRegistry(CoasterWorld world) {
        this.world = world;
    }

    @Override
    public CoasterWorld getWorld() {
        return world;
    }

    /**
     * Gets a collection of all unique power channel names in use on this World
     *
     * @return power channel names
     */
    public List<String> getNames() {
        List<String> result = namesCopy;
        if (result == null) {
            namesCopy = result = new ArrayList<>(names);
        }
        return result;
    }

    /**
     * Gets a named power channel by name if any signs exist that use it
     *
     * @param name Power state name
     * @return Named power state, or null if not found
     */
    public NamedPowerChannel findIfExists(String name) {
        SignRegisteredNamedPowerState state = byName.get(name);
        return (state == null) ? null : new NamedPowerChannel(state, BlockFace.SELF);
    }

    /**
     * Looks up a named power channel in the cache by the name specified. If it exists,
     * registers the sign as a recipient, if not, creates a new state.
     *
     * @param name Name of the power state
     * @param powered Initial powered state.
     *                Used when creating a state for the first time.
     * @param sign Sign to assign as recipient to the state
     * @return NamedPowerState
     */
    NamedPowerChannel.NamedPowerState register(String name, boolean powered, TrackNodeSign sign) {
        SignRegisteredNamedPowerState state = byName.get(name);
        if (state != null) {
            return state.register(this, sign);
        } else {
            state = new SignRegisteredNamedPowerState(name, powered, sign);

            byName.put(name, state);
            names.add(name);
            namesCopy = null; // Invalidate

            return state;
        }
    }

    /**
     * If a PendingPowerPulses.yml file exists, loads it in and re-schedules the pulses contained
     * within. This should be called AFTER the relevant tracks are loaded in so that the named
     * channels exist. Pulses for channels that no longer exist are discarded.<br>
     * <br>
     * After loading, the file is deleted to prevent confusing problems if the server crashes
     * at runtime.
     */
    public void loadPulses() {
        File configFile = getPulsesConfigFile();
        FileConfiguration config = new FileConfiguration(configFile);
        if (config.exists()) {
            config.load();

            for (ConfigurationNode pendingConfig : config.getNodeList("pulses")) {
                ScheduledPulse pulse = ScheduledPulse.fromConfig(pendingConfig);
                if (pulse != null) {
                    SignRegisteredNamedPowerState state = byName.get(pulse.name);
                    if (state != null) {
                        state.pulsePowered(pulse.powered, pulse.delay);
                    }
                }
            }

            configFile.delete();
        }
    }

    /**
     * Saves all currently pending power pulses to file, if any. Then aborts all the
     * pending tasks for them.
     */
    public void saveAndAbortPulses() {
        // Serialize pending pulses
        List<ConfigurationNode> pendingPulseConfigs = byName.values().stream()
                .filter(SignRegisteredNamedPowerState::hasPulse)
                .map(ScheduledPulse::new)
                .map(ScheduledPulse::toConfig)
                .collect(Collectors.toList());

        // Abort all pulses and clean up tasks
        abortAllPulses();

        // Save to disk
        File configFile = getPulsesConfigFile();
        if (pendingPulseConfigs.isEmpty()) {
            configFile.delete(); // Just in case
        } else {
            FileConfiguration pulsesConfig = new FileConfiguration(configFile);
            pulsesConfig.setNodeList("pulses", pendingPulseConfigs);
            pulsesConfig.saveSync();
        }
    }

    /**
     * Aborts all scheduled power channel changes
     */
    public void abortAllPulses() {
        for (SignRegisteredNamedPowerState state : byName.values()) {
            state.abortPulse();
            state.pulseTask = null; //GC
        }
    }

    private File getPulsesConfigFile() {
        return new File(this.getWorld().getConfigFolder(), "PendingPowerPulses.yml");
    }

    private class SignRegisteredNamedPowerState extends NamedPowerChannel.NamedPowerState {
        private List<Recipient> recipients;
        private Task pulseTask = null;
        private int pulseTaskDoneTime = -1;

        public SignRegisteredNamedPowerState(String name, boolean powered, TrackNodeSign signRecipient) {
            super(name, powered);
            this.recipients = Collections.singletonList(Recipient.of(signRecipient));
        }

        @Override
        public void setPowered(boolean powered) {
            // Do nothing if unchanged. Do notify a pulse being disabled.
            if (this.isPowered() == powered) {
                if (hasPulse()) {
                    abortPulse();
                    notifyRecipientsOfChange();
                }
                return;
            }

            // Change it
            abortPulse();
            changePowered(powered);
        }

        @Override
        public void pulsePowered(boolean powered, int delay) {
            // Check not removed
            if (this.recipients.isEmpty()) {
                throw new UnsupportedOperationException("Only registered named power channels support pulsing");
            }

            // Initialize task for the first time. We re-use it (performance)
            // Start a new pulse period
            // Must do this before changing powered state so the notifications work right
            boolean hadPulse = hasPulse();
            if (pulseTask == null) {
                pulseTask = new Task(world.getPlugin()) {
                    @Override
                    public void run() {
                        pulseTaskDoneTime = -1;
                        changePowered(!isPowered());
                    }
                };
            }
            pulseTask.stop().start(delay);
            pulseTaskDoneTime = CommonUtil.getServerTicks() + delay;

            // Change powered state
            if (powered != isPowered()) {
                changePowered(powered);
            } else if (!hadPulse) {
                notifyRecipientsOfChange();
            }
        }

        public void changePowered(boolean powered) {
            // Store previous 'is powered' state
            List<Recipient> recipients = this.recipients;
            for (Recipient recipient : recipients) {
                recipient.wasPowered = recipient.sign.isPowered();
            }

            // Update
            super.setPowered(powered);

            // Notify to update particles
            notifyRecipientsOfChange();

            // Fire events for all recipients
            // One sign action might alter the recipients, causing it no
            // be deleted from the node. Catch that.
            for (Recipient recipient : recipients) {
                TrackNode node = recipient.sign.getNode();
                if (node == null) {
                    continue;
                }
                node.markChanged(); // Power state changed, important to re-save CSV
                if (recipient.sign.isAddedAsAnimation()) {
                    continue;
                }

                boolean isPowered = recipient.sign.isPowered();
                if (isPowered != recipient.wasPowered) {
                    // Fire REDSTONE_ON or REDSTONE_OFF event depending on type of transition
                    recipient.sign.fireRedstoneEvent(isPowered);
                }

                // Fire REDSTONE_CHANGE event
                recipient.sign.fireActionEvent(SignActionType.REDSTONE_CHANGE);
            }
        }

        @Override
        public int getPulseDelay() {
            int tickTimeDone = pulseTaskDoneTime;
            if (tickTimeDone == -1) {
                return -1;
            } else {
                return Math.max(0, tickTimeDone - CommonUtil.getServerTicks());
            }
        }

        @Override
        public boolean hasPulse() {
            return pulseTaskDoneTime != -1;
        }

        public void abortPulse() {
            if (pulseTask != null) {
                pulseTask.stop();
                pulseTaskDoneTime = -1;
            }
        }

        public void notifyRecipientsOfChange() {
            recipients.forEach(r -> r.sign.onPowerChanged());
        }

        @Override
        public NamedPowerState register(NamedPowerChannelRegistry power, TrackNodeSign sign) {
            List<Recipient> recipients = this.recipients;

            // If not registered anymore, or a different world, defer
            if (recipients.isEmpty() || NamedPowerChannelRegistry.this != power) {
                return super.register(power, sign);
            }

            // Check not already registered
            int size = recipients.size();
            for (int i = 0; i < size; i++) {
                if (recipients.get(i).sign == sign) {
                    return this;
                }
            }

            // Clone the list. Avoids trouble when iterating.
            recipients = new ArrayList<>(recipients);
            recipients.add(Recipient.of(sign));
            this.recipients = recipients;
            return this;
        }

        @Override
        public void unregister(NamedPowerChannelRegistry power, TrackNodeSign sign) {
            int size = recipients.size();
            for (int i = 0; i < size; i++) {
                if (recipients.get(i).sign == sign) {
                    if (recipients.size() == 1) {
                        // No more recipients. De-register this named state.
                        recipients = Collections.emptyList();
                        byName.remove(getName());
                        names.remove(getName());
                        namesCopy = null; // Invalidate
                        if (pulseTask != null) {
                            pulseTask.stop();
                            pulseTask = null;
                        }
                    } else {
                        // Remove it
                        recipients.remove(i);
                        break;
                    }
                }
            }
        }
    }

    private static class Recipient {
        public final TrackNodeSign sign;
        public boolean wasPowered; // used for event handling

        private Recipient(TrackNodeSign sign) {
            this.sign = sign;
        }

        public static Recipient of(TrackNodeSign sign) {
            return new Recipient(sign);
        }
    }

    private static final class ScheduledPulse {
        public final String name;
        public final boolean powered;
        public final int delay;

        public ScheduledPulse(NamedPowerChannel.NamedPowerState channel) {
            this(channel.getName(), channel.isPowered(), channel.getPulseDelay());
        }

        public ScheduledPulse(String name, boolean powered, int delay) {
            this.name = name;
            this.powered = powered;
            this.delay = delay;
        }

        public ConfigurationNode toConfig() {
            ConfigurationNode config = new ConfigurationNode();
            config.set("name", this.name);
            config.set("powered", this.powered);
            config.set("delay", this.delay);
            return config;
        }

        public static ScheduledPulse fromConfig(ConfigurationNode config) {
            String name = config.get("name", String.class, null);
            Boolean powered = config.get("powered", Boolean.class, null);
            Integer delay = config.get("delay", Integer.class, null);
            if (name != null && powered != null && delay != null) {
                return new ScheduledPulse(name, powered.booleanValue(), delay.intValue());
            } else {
                return null;
            }
        }
    }
}