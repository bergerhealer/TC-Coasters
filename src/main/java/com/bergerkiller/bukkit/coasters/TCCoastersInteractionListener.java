package com.bergerkiller.bukkit.coasters;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.events.PacketReceiveEvent;
import com.bergerkiller.bukkit.common.events.PacketSendEvent;
import com.bergerkiller.bukkit.common.protocol.PacketListener;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.common.wrappers.ResourceKey;
import com.bergerkiller.bukkit.common.wrappers.UseAction;
import com.bergerkiller.generated.net.minecraft.server.MovingObjectPositionHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayInArmAnimationHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayInBlockDigHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayInBlockDigHandle.EnumPlayerDigTypeHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayInBlockPlaceHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayInUseItemHandle;
import com.bergerkiller.generated.net.minecraft.server.WorldHandle;

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
            PacketType.IN_USE_ENTITY,
            PacketType.IN_USE_ITEM,
            PacketType.IN_BLOCK_PLACE,
            PacketType.IN_ENTITY_ANIMATION
    };

    private final TCCoasters plugin;
    private final Map<Player, Metadata> trackedMeta = new HashMap<Player, Metadata>();
    private final Metadata nullMeta = new Metadata();
    private Task metadataCleanupTimer = null;

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
        ResourceKey breakSound = WorldUtil.getBlockData(event.getBlock()).getPlaceSound();
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
            ResourceKey breakSound = placedData.getPlaceSound();
            Location loc = event.getBlock().getLocation().add(new Vector(0.5, 0.5, 0.5));
            PlayerUtil.playSound(event.getPlayer(), loc, breakSound, 1.0f, 1.0f);
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        // If particles aren't at all shown to players, ignore all handling of this event
        // This is a performance enhancement
        PlayerEditState state = this.plugin.getEditState(event.getPlayer());
        if (state.getMode() == PlayerEditState.Mode.DISABLED) {
            return;
        }

        // Limit arm swing animations to a certain amount per time frame
        if (event.getType() == PacketType.IN_ENTITY_ANIMATION) {
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
        if (event.getType() == PacketType.IN_USE_ITEM || event.getType() == PacketType.IN_BLOCK_PLACE) {
            boolean needsCheck = false;
            ClickInfo clickInfo = null;
            HumanHand suggestedHand = HumanHand.LEFT;
            if (event.getType() == PacketType.IN_BLOCK_PLACE) {
                // Block place is used when we cannot place with either hand - always check
                // We don't know the specifics so perform some ray tracing
                needsCheck = true;
                clickInfo = rayTrace(event.getPlayer());
                if (HumanHand.getItemInMainHand(event.getPlayer()) != null) {
                    suggestedHand = HumanHand.getMainHand(event.getPlayer());
                } else {
                    suggestedHand = HumanHand.getOffHand(event.getPlayer());
                }
            } else if (PacketPlayInUseItemHandle.T.enumHand.isAvailable()) {
                PacketPlayInUseItemHandle packet = PacketPlayInUseItemHandle.createHandle(event.getPacket().getHandle());

                // Use item is used - is the item we interact with null (empty?)
                // And is the item in the other hand not empty? This is a strong indicator.
                HumanHand hand = packet.getHand(event.getPlayer());
                if (HumanHand.getHeldItem(event.getPlayer(), hand) == null) {
                    HumanHand other = hand.opposite();
                    if (HumanHand.getHeldItem(event.getPlayer(), other) != null) {
                        needsCheck = true;
                        suggestedHand = other;
                    }
                }

                // Turn packet data into ClickInfo
                if (needsCheck) {
                    // Before we do anything, validate the data in the Use Item packet
                    // Hacked clients might send ridiculous block position coordinates, which could break the server
                    IntVector3 pos = packet.getPosition();
                    Vector playerPosDiff = event.getPlayer().getEyeLocation().toVector();
                    playerPosDiff.setX(playerPosDiff.getX() - pos.x);
                    playerPosDiff.setY(playerPosDiff.getY() - pos.y);
                    playerPosDiff.setZ(playerPosDiff.getZ() - pos.z);
                    if (playerPosDiff.lengthSquared() > (10.0*10.0)) {
                        needsCheck = false;
                    } else {
                        clickInfo = new ClickInfo();
                        clickInfo.block = event.getPlayer().getWorld().getBlockAt(pos.x, pos.y, pos.z);
                        clickInfo.face = packet.getDirection();
                        clickInfo.position = new Vector(packet.getDeltaX(), packet.getDeltaY(), packet.getDeltaZ());
                    }
                }
            }
            if (!needsCheck) {
                return; // All is well.
            }

            // Check whether a particle is reasonably nearby
            // This acts as an extra safeguard against weird bugs happening elsewhere on the world
            if (!state.getParticles().isParticleNearby(event.getPlayer())) {
                return;
            }

            // Fix it
            if (event.getType() == PacketType.IN_BLOCK_PLACE) {
                event.setCancelled(true);

                // Handle as a normal click
                // Only when click info is available - otherwise is same as Block Place
                // This prevents stack overflow
                if (clickInfo != null) {
                    fakeItemPlacement(event.getPlayer(), clickInfo, suggestedHand);
                }
            } else {
                // Attempt using the other hand instead that has an item
                PacketPlayInUseItemHandle packet = PacketPlayInUseItemHandle.createHandle(event.getPacket().getHandle());
                packet.setHand(event.getPlayer(), suggestedHand);
            }
        }

        if (event.getType() == PacketType.IN_USE_ENTITY) {
            int entityId = event.getPacket().read(PacketType.IN_USE_ENTITY.clickedEntityId);
            if (!state.getParticles().isParticle(event.getPlayer(), entityId)) {
                return; // Not one of our own entities
            }

            // This is ours, cancel it.
            event.setCancelled(true);

            // Hand (handle, raw)
            HumanHand hand = PacketType.IN_USE_ENTITY.getHand(event.getPacket(), event.getPlayer());

            // Turn the event into a block interaction event
            boolean isInteractEvent = event.getPacket().read(PacketType.IN_USE_ENTITY.useAction) != UseAction.ATTACK;

            // Find the block interacted with
            // When the player is in edit mode, skip this expensive lookup and always fire an interaction with block air
            // Due to a bug we can only do this for 'right click' (interact) actions
            ClickInfo clickInfo = null;
            if (!this.plugin.isHoldingEditTool(event.getPlayer()) || !isInteractEvent) {
                clickInfo = rayTrace(event.getPlayer());
            }

            // Fake the interaction with the blocks
            if (isInteractEvent) {
                fakeItemPlacement(event.getPlayer(), clickInfo, hand);
            } else {
                fakeBlockDestroy(event.getPlayer(), clickInfo, hand);
            }
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

    private void fakeItemPlacement(Player player, ClickInfo clickInfo, HumanHand hand) {
        if (clickInfo == null) {
            // Block Place is used when not clicking on any block
            PacketPlayInBlockPlaceHandle packet = PacketPlayInBlockPlaceHandle.T.newHandleNull();
            packet.setTimestamp(System.currentTimeMillis());
            packet.setHand(player, hand);
            PacketUtil.receivePacket(player, packet);
        } else {
            createMeta(player).blockPlaceTime = Long.valueOf(System.currentTimeMillis());

            // Send the actual packet
            PacketPlayInUseItemHandle packet = PacketPlayInUseItemHandle.T.newHandleNull();
            packet.setTimestamp(System.currentTimeMillis());
            packet.setEnumHand(HumanHand.toNMSEnumHand(player, hand));
            packet.setDirection(clickInfo.face);
            packet.setPosition(new IntVector3(clickInfo.block));
            packet.setDeltaX((float) clickInfo.position.getX());
            packet.setDeltaY((float) clickInfo.position.getY());
            packet.setDeltaZ((float) clickInfo.position.getZ());
            PacketUtil.receivePacket(player, packet);
        }
    }

    private void fakeBlockDestroy(Player player, ClickInfo clickInfo, HumanHand hand) {
        if (clickInfo == null) {
            // Player arm animation is used to left-click the air
            PacketPlayInArmAnimationHandle packet = PacketPlayInArmAnimationHandle.T.newHandleNull();
            packet.setHand(player, hand);
            PacketUtil.receivePacket(player, packet);
        } else {
            createMeta(player).blockBreakTime = Long.valueOf(System.currentTimeMillis());

            // Block dig is used for left-click interaction
            PacketPlayInBlockDigHandle packet = PacketPlayInBlockDigHandle.T.newHandleNull();
            packet.setDirection(clickInfo.face);
            packet.setPosition(new IntVector3(clickInfo.block));
            packet.setDigType(EnumPlayerDigTypeHandle.START_DESTROY_BLOCK);
            PacketUtil.receivePacket(player, packet);
        }
    }

    private static ClickInfo rayTrace(Player player) {
        Location loc = player.getEyeLocation();
        Vector dir = loc.getDirection();
        Vector start = loc.toVector();
        Vector end = dir.clone().multiply(5.0).add(start);
        MovingObjectPositionHandle mop = WorldHandle.fromBukkit(loc.getWorld()).rayTrace(start, end, false);
        if (mop == null) {
            return null;
        }

        ClickInfo info = new ClickInfo();
        info.position = mop.getPos();
        info.face = mop.getDirection();
        Vector blockCoordPos = info.position.clone().add(dir.clone().multiply(1e-5));
        int x = blockCoordPos.getBlockX();
        int y = blockCoordPos.getBlockY();
        int z = blockCoordPos.getBlockZ();
        info.block = loc.getWorld().getBlockAt(x, y, z);
        info.position.setX(info.position.getX() - x);
        info.position.setY(info.position.getY() - y);
        info.position.setZ(info.position.getZ() - z);
        return info;
    }

    public static class ClickInfo {
        public Block block;
        public Vector position;
        public BlockFace face;
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
