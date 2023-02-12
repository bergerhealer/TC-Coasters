package com.bergerkiller.bukkit.coasters.signs.actions;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.block.Block;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.TCCoastersPermissions;
import com.bergerkiller.bukkit.coasters.commands.parsers.TimeTicksParser;
import com.bergerkiller.bukkit.coasters.signs.power.NamedPowerChannel;
import com.bergerkiller.bukkit.coasters.signs.power.NamedPowerChannelRegistry;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.SignActionHeader;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.offline.sign.OfflineSign;
import com.bergerkiller.bukkit.tc.offline.sign.OfflineSignMetadataHandler;
import com.bergerkiller.bukkit.tc.offline.sign.OfflineSignStore;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;

public class SignActionPower extends TCCSignAction {
    private final TCCoasters plugin;

    public SignActionPower(TCCoasters plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getPrefix() {
        return "power";
    }

    @Override
    public void execute(SignActionEvent info) {
        if (!info.isAction(SignActionType.REDSTONE_ON, SignActionType.REDSTONE_OFF)) {
            return;
        }

        NamedPowerChannel channel = plugin.getCoasterWorld(info.getWorld())
                .getNamedPowerChannels().findIfExists(info.getLine(2));
        if (channel == null) {
            playHiss(info.getBlock());
            return;
        }

        int delay = TimeTicksParser.parse(info.getLine(3));
        if (delay > 0) {
            if (info.isAction(SignActionType.REDSTONE_ON)) {
                channel.pulsePowered(!info.getHeader().isInverted(), delay);
            }
        } else {
            channel.setPowered(info.isAction(SignActionType.REDSTONE_ON));
        }
    }

    // Plays a smoke and sound effect at the sign to indicate the power channel doesn't exist
    private static void playHiss(Block block) {
        Location loc = block.getLocation().add(0.5, 0.5, 0.5);
        loc.getWorld().playEffect(loc, Effect.SMOKE, 0);
        WorldUtil.playSound(loc, SoundEffect.EXTINGUISH, 1.0f, 2.0f);
    }

    @Override
    public boolean canSupportFakeSign(SignActionEvent info) {
        return false;
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        String channelName = event.getLine(2);
        if (channelName.isEmpty()) {
            event.getPlayer().sendMessage(ChatColor.RED + "Must specify the channel name on the third line");
            return false;
        }

        if (!NamedPowerChannel.checkPermission(event.getPlayer(), channelName)) {
            return false;
        }

        return SignBuildOptions.create()
                .setPermission(TCCoastersPermissions.BUILD_POWER)
                .setName("power channel transceiver")
                .setDescription("update a power channel using an input redstone signal, and outputs using a lever")
                .handle(event.getPlayer());
    }

    @Override
    public void loadedChanged(SignActionEvent info, boolean loaded) {
        // Note: we ignore unloading, it stays active even while the sign chunk isn't loaded
        // Removal occurs when the offline sign metadata store signals the sign is gone
        if (loaded) {
            TrainCarts.plugin.getOfflineSigns().computeIfAbsent(info.getSign(), TCCPowerSignMetadata.class,
                    offline -> new TCCPowerSignMetadata(false, null));
        }
    }

    public void initPowerMeta() {
        TrainCarts.plugin.getOfflineSigns().registerHandler(TCCPowerSignMetadata.class, new OfflineSignMetadataHandler<TCCPowerSignMetadata>() {

            @Override
            public void onAdded(OfflineSignStore store, OfflineSign sign, TCCPowerSignMetadata metadata) {
                metadata.recipient = makeRecipient(store, sign, metadata.powered);
            }

            @Override
            public void onRemoved(OfflineSignStore store, OfflineSign sign, TCCPowerSignMetadata metadata) {
                if (metadata.recipient != null) {
                    metadata.recipient.remove();
                    metadata.recipient = null;
                }
            }

            @Override
            public TCCPowerSignMetadata onSignChanged(OfflineSignStore store, OfflineSign oldSign, OfflineSign newSign, TCCPowerSignMetadata metadata) {
                // Verify that the sign is still a tcc power sign after the changes
                if (!SignActionHeader.parse(newSign.getLine(0)).isValid()) {
                    return null;
                }
                if (!matchSecondLine(newSign.getLine(1))) {
                    return null;
                }

                TCCPowerSignRecipient newRecipient = makeRecipient(store, newSign, metadata.powered);

                // Remove previous recipient
                if (metadata.recipient != null) {
                    metadata.recipient.remove();
                }

                return new TCCPowerSignMetadata(metadata.powered, newRecipient);
            }

            private TCCPowerSignRecipient makeRecipient(OfflineSignStore store, OfflineSign sign, boolean wasPowered) {
                String channelName = sign.getLine(2);
                if (channelName.isEmpty()) {
                    return null;
                }

                Block signBlock = sign.getLoadedBlock();
                boolean inverted = SignActionHeader.parse(sign.getLine(0)).isInverted();

                TCCPowerSignRecipient recipient = new TCCPowerSignRecipient(plugin, signBlock, inverted,
                        store, channelName, wasPowered);

                // Refresh powered metadata if needed. Shouldn't be though...
                if (wasPowered != recipient.channel.isPowered()) {
                    recipient.refreshNextTick();
                }

                return recipient;
            }

            @Override
            public void onUpdated(OfflineSignStore store, OfflineSign sign, TCCPowerSignMetadata oldValue, TCCPowerSignMetadata newValue) {
                // No need to do anything here.
            }

            @Override
            public void onEncode(DataOutputStream stream, OfflineSign sign, TCCPowerSignMetadata value) throws IOException {
                stream.writeBoolean(value.powered);
            }

            @Override
            public TCCPowerSignMetadata onDecode(DataInputStream stream, OfflineSign sign) throws IOException {
                return new TCCPowerSignMetadata(stream.readBoolean(), null);
            }
        });
    }

    public void deinitPowerMeta() {
        TrainCarts.plugin.getOfflineSigns().unregisterHandler(TCCPowerSignMetadata.class);
    }

    public static class TCCPowerSignMetadata {
        public final boolean powered;
        public TCCPowerSignRecipient recipient;

        public TCCPowerSignMetadata(boolean powered, TCCPowerSignRecipient recipient) {
            this.powered = powered;
            this.recipient = recipient;
        }
    }

    private static class TCCPowerSignRecipient implements NamedPowerChannel.Recipient {
        private int lastTickChanged = -1;
        private Task nextTickTask = null;
        private final TCCoasters plugin;
        private final Block signBlock;
        private final boolean inverted;
        private final OfflineSignStore store;
        private final NamedPowerChannelRegistry channelRegistry;
        private final NamedPowerChannel channel;

        public TCCPowerSignRecipient(
                TCCoasters plugin,
                Block signBlock, boolean inverted,
                OfflineSignStore store,
                String name, boolean powered
        ) {
            this.plugin = plugin;
            this.signBlock = signBlock;
            this.inverted = inverted;
            this.store = store;
            this.channelRegistry = plugin.getCoasterWorld(signBlock.getWorld()).getNamedPowerChannels();
            this.channel = channelRegistry.create(name, powered, this);
        }

        public void remove() {
            if (nextTickTask != null) {
                nextTickTask.stop();
                nextTickTask = null;
            }
            channel.removeRecipient(channelRegistry, this);
        }

        @Override
        public void onPreChange() {
        }

        @Override
        public void onPostChange(boolean powered) {
            int ticks = CommonUtil.getServerTicks();

            // Avoid infinite loops crashing the server
            if (ticks == lastTickChanged) {
                refreshNextTick();
                return;
            } else {
                lastTickChanged = ticks;
            }

            // Abort this one
            if (nextTickTask != null) {
                nextTickTask.stop();
                nextTickTask = null;
            }

            BlockData blockData = WorldUtil.getBlockData(this.signBlock);
            if (!MaterialUtil.ISSIGN.get(blockData)) {
                // No longer exists?! Get rid of it. Might be stale metadata.
                // The store should really have caught it though...
                store.remove(this.signBlock, TCCPowerSignMetadata.class);
                return;
            }

            // Persistence of powered state if this is the only recipient
            store.put(this.signBlock, new TCCPowerSignMetadata(powered, this));
            // Update levers
            BlockUtil.setLeversAroundBlock(this.signBlock.getRelative(blockData.getAttachedFace()), powered != inverted);
        }

        private void refreshNextTick() {
            if (nextTickTask == null) {
                nextTickTask = new Task(plugin) {
                    @Override
                    public void run() {
                        if (nextTickTask != null) {
                            nextTickTask = null;
                            onPostChange(channel.isPowered());
                        }
                    }
                }.start(1);
            }
        }
        
        @Override
        public void onChanged() {
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof TCCPowerSignRecipient && ((TCCPowerSignRecipient) o).signBlock.equals(this.signBlock);
        }
    }
}
