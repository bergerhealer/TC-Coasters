package com.bergerkiller.bukkit.coasters;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.bergerkiller.bukkit.common.wrappers.HumanHandRole;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ServerboundAttackPacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ServerboundPunchPacketHandle;
import com.bergerkiller.generated.net.minecraft.world.phys.BlockHitResultHandle;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.TCCoastersUtil.TargetedBlockInfo;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.events.PacketReceiveEvent;
import com.bergerkiller.bukkit.common.events.PacketSendEvent;
import com.bergerkiller.bukkit.common.protocol.PacketListener;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.resources.ResourceKey;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ServerboundSwingPacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ServerboundPlayerActionPacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ServerboundUseItemPacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ServerboundInteractPacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ServerboundUseItemOnPacketHandle;

/**
 * This packet listener detects when players click on the invisible entities for virtual rails,
 * and translates those into appropriate block (or air) interaction events.
 * This allows players to build or destroy blocks where they otherwise couldn't, as well as
 * simplifying the left/right click handling logic.
 */
public class TCCoastersInteractionListener implements PacketListener, Listener {
    private static final long MIN_ARM_SWING_INTERVAL_MS = 100;
    private static final long MIN_BLOCK_BREAK_EFFECT_TIME_MS = 100;
    public static final PacketType[] PACKET_TYPES = {
            PacketType.IN_INTERACT,
            PacketType.IN_ATTACK,
            PacketType.IN_USE_ITEM,
            PacketType.IN_USE_ITEM_ON,
            PacketType.IN_SWING,
            PacketType.IN_PUNCH
    };

    private final TCCoasters plugin;
    private final Map<Player, Metadata> trackedMeta = new HashMap<Player, Metadata>();
    private final Metadata nullMeta = new Metadata();
    private Task metadataCleanupTimer = null;
    private boolean ignoreInteractPacket = false;

    public TCCoastersInteractionListener(TCCoasters plugin) {
        this.plugin = plugin;
        this.metadataCleanupTimer = new MetadataCleanupTask();
    }

    public void enable() {
        this.metadataCleanupTimer = new MetadataCleanupTask().start(10, 10);
        PacketUtil.addPacketListener(this.plugin, this, PACKET_TYPES);
        Bukkit.getServer().getPluginManager().registerEvents(this, this.plugin);
    }

    public void disable() {
        Task.stop(this.metadataCleanupTimer);
        this.metadataCleanupTimer = null;
        this.trackedMeta.clear();
        PacketUtil.removePacketListener(this);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Metadata meta = getMeta(event.getPlayer());
        if (!meta.isBlockBreakActive(System.currentTimeMillis())) {
            return;
        }

        meta.blockBreakTime = null;

        // Play sound SFX for the client, because normally nothing plays
        ResourceKey<SoundEffect> breakSound = WorldUtil.getBlockData(event.getBlock()).getPlaceSound();
        Location loc = event.getBlock().getLocation().add(new Vector(0.5, 0.5, 0.5));
        PlayerUtil.playSound(event.getPlayer(), loc, breakSound, 1.0f, 1.0f);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Metadata meta = getMeta(event.getPlayer());
        if (!meta.isBlockPlaceActive(System.currentTimeMillis())) {
            return;
        }

        meta.blockPlaceTime = null;

        // Play sound SFX for the client, because normally nothing plays
        // A bit iffy how this should be done, as this depends largely on the type of item held by the player
        BlockData placedData = BlockData.fromItemStack(event.getItemInHand());
        if (placedData != BlockData.AIR) {
            ResourceKey<SoundEffect> breakSound = placedData.getPlaceSound();
            Location loc = event.getBlock().getLocation().add(new Vector(0.5, 0.5, 0.5));
            PlayerUtil.playSound(event.getPlayer(), loc, breakSound, 1.0f, 1.0f);
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        PlayerEditState state = this.plugin.getEditState(event.getPlayer());

        // Limit arm swing animations to a certain amount per time frame
        if (event.getType() == PacketType.IN_SWING || event.getType() == PacketType.IN_PUNCH) {
            long time_new = System.currentTimeMillis();
            Metadata meta = createMeta(event.getPlayer());
            if (meta.isArmSwingActive(time_new)) {
                event.setCancelled(true);
                return;
            }
            meta.armSwingTime = Long.valueOf(time_new);
        }

        // When the player attempts placing down a block where he otherwise can't, Minecraft decides
        // to switch to the other hand, or outright fire a BlockPlace instead. Detect when this happens,
        // and verify placement truly is impossible before letting it slip. Basically, we must correct
        // for the 'this spot is occupied' check on the client side before it happens.
        if (event.getType() == PacketType.IN_USE_ITEM || event.getType() == PacketType.IN_USE_ITEM_ON) {
            // Avoids expensive rayTrace call for no reason
            if (ignoreInteractPacket) {
                return;
            }

            boolean needsCheck = false;
            BlockHitResultHandle blockHitResult = null;
            HumanHandRole suggestedHandRole = HumanHandRole.OFF;
            if (event.getType() == PacketType.IN_USE_ITEM) {
                // Block place is used when we cannot place with either hand - always check
                // We don't know the specifics so perform some ray tracing
                needsCheck = true;
                blockHitResult = TCCoastersUtil.bkclRayTrace(event.getPlayer());

                ServerboundUseItemPacketHandle packet = ServerboundUseItemPacketHandle.createHandle(event.getPacket().getHandle());

                suggestedHandRole = packet.getHandRole();
                if (!ItemUtil.isEmpty(suggestedHandRole.getHeldItem(event.getPlayer()))) {
                    return; // Player holds an item, ignore
                }

                HumanHandRole otherHandRole = suggestedHandRole.opposite();
                if (!ItemUtil.isEmpty(otherHandRole.getHeldItem(event.getPlayer()))) {
                    suggestedHandRole = otherHandRole;
                }
            } else {
                ServerboundUseItemOnPacketHandle packet = ServerboundUseItemOnPacketHandle.createHandle(event.getPacket().getHandle());

                // Use item is used - is the item we interact with null (empty?)
                // And is the item in the other hand not empty? This is a strong indicator.
                HumanHandRole handRole = packet.getHandRole();
                if (ItemUtil.isEmpty(handRole.getHeldItem(event.getPlayer()))) {
                    HumanHandRole otherRole = handRole.opposite();
                    if (!ItemUtil.isEmpty(otherRole.getHeldItem(event.getPlayer()))) {
                        needsCheck = true;
                        suggestedHandRole = otherRole;
                    }
                }

                // Turn packet data into ClickInfo
                if (needsCheck) {
                    // Before we do anything, validate the data in the Use Item packet
                    // Hacked clients might send ridiculous block position coordinates, which could break the server
                    IntVector3 blockPos = packet.getBlockPos();
                    Vector playerPosDiff = event.getPlayer().getEyeLocation().toVector();
                    playerPosDiff.setX(playerPosDiff.getX() - blockPos.x);
                    playerPosDiff.setY(playerPosDiff.getY() - blockPos.y);
                    playerPosDiff.setZ(playerPosDiff.getZ() - blockPos.z);
                    if (playerPosDiff.lengthSquared() > (10.0*10.0)) {
                        needsCheck = false;
                    } else {
                        blockHitResult = packet.getHitResult();
                    }
                }
            }
            if (!needsCheck) {
                return; // All is well.
            }

            // Check whether a particle is reasonably nearby
            // This acts as an extra safeguard against weird bugs happening elsewhere on the world
            if (!state.getWorld().getParticles().isParticleNearby(event.getPlayer())) {
                return;
            }

            // Fix it
            if (event.getType() == PacketType.IN_USE_ITEM) {
                if (blockHitResult == null) {
                    // Switch hand as needed
                    ServerboundUseItemPacketHandle packet = ServerboundUseItemPacketHandle.createHandle(event.getPacket().getHandle());
                    event.setPacket(packet.withHandRole(suggestedHandRole));
                } else {
                    // Cancel old event and fire item placement instead
                    event.setCancelled(true);
                    fakeItemPlacement(event.getPlayer(), blockHitResult, suggestedHandRole);
                }
            } else {
                // Attempt using the other hand instead that has an item
                ServerboundUseItemOnPacketHandle packet = ServerboundUseItemOnPacketHandle.createHandle(event.getPacket().getHandle());
                event.setPacket(packet.withHandRole(suggestedHandRole));
            }
        }

        if (event.getType() == PacketType.IN_ATTACK) {
            ServerboundAttackPacketHandle packet = ServerboundAttackPacketHandle.createHandle(event.getPacket().getHandle());

            int entityId = packet.getEntityId();
            if (!state.getWorld().getParticles().isParticle(event.getPlayer(), entityId)) {
                return; // Not one of our own entities
            }

            // This is ours, cancel it.
            event.setCancelled(true);

            // Find the block interacted with
            // When the player is in edit mode, skip this expensive lookup and always fire an interaction with block air
            // Due to a bug we can only do this for 'right click' (interact) actions
            TargetedBlockInfo clickInfo = TCCoastersUtil.rayTrace(event.getPlayer());

            // Fake the interaction with the blocks
            fakeBlockDestroy(event.getPlayer(), clickInfo, HumanHand.getMainHand(event.getPlayer()));
        } else if (event.getType() == PacketType.IN_INTERACT) {
            ServerboundInteractPacketHandle packet = ServerboundInteractPacketHandle.createHandle(event.getPacket().getHandle());

            int entityId = packet.getUsedEntityId();
            if (!state.getWorld().getParticles().isParticle(event.getPlayer(), entityId)) {
                return; // Not one of our own entities
            }

            // This is ours, cancel it.
            event.setCancelled(true);

            // Make sure interaction is done as the same hand role
            final HumanHandRole handRole = packet.getHandRole();

            // Find the block interacted with
            // When the player is in edit mode, skip this expensive lookup and always fire an interaction with block air
            // Due to a bug we can only do this for 'right click' (interact) actions
            BlockHitResultHandle blockHitResult = null;
            if (!this.plugin.getHeldTool(event.getPlayer()).isNodeSelector()) {
                blockHitResult = TCCoastersUtil.bkclRayTrace(event.getPlayer());
            }

            fakeItemPlacement(event.getPlayer(), blockHitResult, handRole);
        }
    }

    private Metadata getMeta(Player player) {
        return LogicUtil.fixNull(this.trackedMeta.get(player), this.nullMeta);
    }

    private Metadata createMeta(Player player) {
        Metadata meta = this.trackedMeta.get(player);
        if (meta == null) {
            meta = new Metadata();
            this.trackedMeta.put(player, meta);
        }
        return meta;
    }

    private void fakeItemPlacement(Player player, BlockHitResultHandle blockHitResult, HumanHandRole handRole) {
        boolean ignoreInteractPacket_old = this.ignoreInteractPacket;
        try {
            if (blockHitResult == null) {
                this.ignoreInteractPacket = true;

                // Block Place is used when not clicking on any block
                Location eye = player.getEyeLocation();
                PacketUtil.receivePacket(player, ServerboundUseItemPacketHandle.createNew(
                        fixHandRole(player, handRole),
                        System.currentTimeMillis(),
                        0, /* Sequence */
                        eye.getYaw(),
                        eye.getPitch()
                ));
            } else {
                createMeta(player).blockPlaceTime = Long.valueOf(System.currentTimeMillis());

                // Send the actual packet
                PacketUtil.receivePacket(player, ServerboundUseItemOnPacketHandle.createNew(
                        handRole,
                        blockHitResult,
                        0, /* Sequence */
                        System.currentTimeMillis() /* Timestamp */
                ));
            }
        } finally {
            this.ignoreInteractPacket = ignoreInteractPacket_old;
        }
    }

    private void fakeBlockDestroy(Player player, TargetedBlockInfo clickInfo, HumanHand hand) {
        if (clickInfo == null) {
            if (ServerboundPunchPacketHandle.T.isAvailable()) {
                // Player punch is used to left-click the air (26.3+)
                ServerboundPunchPacketHandle packet = ServerboundPunchPacketHandle.T.newHandleNull();
                PacketUtil.receivePacket(player, packet);
            } else {
                // Player arm animation is used to left-click the air
                ServerboundSwingPacketHandle packet = ServerboundSwingPacketHandle.T.newHandleNull();
                packet.setHand(player, hand);
                PacketUtil.receivePacket(player, packet);
            }
        } else {
            createMeta(player).blockBreakTime = Long.valueOf(System.currentTimeMillis());

            // Block dig is used for left-click interaction
            ServerboundPlayerActionPacketHandle packet = ServerboundPlayerActionPacketHandle.T.newHandleNull();
            packet.setDirection(clickInfo.face);
            packet.setPosition(new IntVector3(clickInfo.block));
            packet.setDigType(ServerboundPlayerActionPacketHandle.ActionHandle.START_DESTROY_BLOCK);
            PacketUtil.receivePacket(player, packet);
        }
    }

    private static HumanHandRole fixHandRole(Player player, HumanHandRole handRole) {
        // Right-click air is broken when using a hand that is not holding an item
        // Work around this issue first
        if (ItemUtil.isEmpty(handRole.getHeldItem(player))) {
            HumanHandRole otherHandRole = handRole.opposite();
            if (!ItemUtil.isEmpty(otherHandRole.getHeldItem(player))) {
                return otherHandRole;
            }
        }
        return handRole;
    }

    private static class Metadata {
        public Long armSwingTime = null;
        public Long blockBreakTime = null;
        public Long blockPlaceTime = null;

        public boolean isArmSwingActive(long currentTime) {
            return armSwingTime != null && (currentTime - armSwingTime.longValue()) < MIN_ARM_SWING_INTERVAL_MS;
        }

        public boolean isBlockBreakActive(long currentTime) {
            return blockBreakTime != null && (currentTime - blockBreakTime.longValue()) < MIN_BLOCK_BREAK_EFFECT_TIME_MS;
        }

        public boolean isBlockPlaceActive(long currentTime) {
            return blockPlaceTime != null && (currentTime - blockPlaceTime.longValue()) < MIN_BLOCK_BREAK_EFFECT_TIME_MS;
        }

        public boolean isExpired(long currentTime) {
            return !isArmSwingActive(currentTime) && !isBlockBreakActive(currentTime) && !isBlockPlaceActive(currentTime);
        }
    }

    private class MetadataCleanupTask extends Task {

        public MetadataCleanupTask() {
            super(plugin);
        }

        @Override
        public void run() {
            if (!trackedMeta.isEmpty()) {
                long time = System.currentTimeMillis();
                Iterator<Metadata> iter = trackedMeta.values().iterator();
                while (iter.hasNext()) {
                    if (iter.next().isExpired(time)) {
                        iter.remove();
                    }
                }
            }
        }
    }
}
