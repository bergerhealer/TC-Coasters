package com.bergerkiller.bukkit.coasters.util;

import java.util.UUID;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.collections.octree.DoubleOctree;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.generated.net.minecraft.server.EntityArmorStandHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityBatHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityInsentientHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutAttachEntityHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityEquipmentHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityTeleportHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutMountHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutSpawnEntityLivingHandle;

/**
 * Helper class for spawning and updating a virtual armor stand head item.
 * This is an item held by an invisible armor stand, with head pointing
 * in a given orientation.
 */
public class VirtualArmorStandItem {
    private static final double ARMORSTAND_HEAD_OFFSET = 1.44;
    private static final double BAT_HOLDER_OFFSET_GLOWING = ARMORSTAND_HEAD_OFFSET + 0.675;
    private static final double BAT_HOLDER_OFFSET = BAT_HOLDER_OFFSET_GLOWING + 0.1;
    private static final boolean CAN_GLOW = Common.evaluateMCVersion(">=", "1.9");
    private static final boolean CAN_FIRE_LIT = Common.evaluateMCVersion(">", "1.8");
    private int holderEntityId;
    private int entityId;
    private double posX, posY, posZ;
    private Quaternion orientation;
    private ItemStack item;
    private boolean glowing = false;
    private boolean respawn = false;

    public VirtualArmorStandItem item(ItemStack item) {
        this.item = item;
        return this;
    }

    public VirtualArmorStandItem position(DoubleOctree.Entry<?> position) {
        this.posX = position.getX();
        this.posY = position.getY();
        this.posZ = position.getZ();
        return this;
    }

    public VirtualArmorStandItem position(Vector position) {
        this.posX = position.getX();
        this.posY = position.getY();
        this.posZ = position.getZ();
        return this;
    }

    public VirtualArmorStandItem orientation(Quaternion orientation) {
        this.orientation = orientation;
        return this;
    }

    public VirtualArmorStandItem glowing(boolean glowing) {
        this.glowing = glowing;
        return this;
    }

    public VirtualArmorStandItem respawn(boolean respawn) {
        this.respawn = respawn;
        return this;
    }

    private VirtualArmorStandItem(int holderEntityId, int entityId) {
        this.holderEntityId = holderEntityId;
        this.entityId = entityId;
    }

    public static VirtualArmorStandItem create(int holderEntityId, int entityId) {
        return new VirtualArmorStandItem(holderEntityId, entityId);
    }

    public int entityId() {
        return this.entityId;
    }

    public int holderEntityId() {
        return this.holderEntityId;
    }

    public VirtualArmorStandItem updateMetadata(Player viewer) {
        if (this.holderEntityId != -1) {
            PacketUtil.sendPacket(viewer, PacketType.OUT_ENTITY_DESTROY.newInstance(this.holderEntityId));
        }
        if (this.entityId != -1 && CAN_GLOW) {
            DataWatcher metadata = new DataWatcher();
            metadata.set(EntityHandle.DATA_FLAGS, (byte) EntityHandle.DATA_FLAG_INVISIBLE);
            metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_GLOWING, this.glowing);

            if (this.glowing) {
                metadata.setByte(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, EntityArmorStandHandle.DATA_FLAG_SET_MARKER);
                metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_ON_FIRE, CAN_FIRE_LIT);
            } else {
                metadata.setByte(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, 0);
            }

            PacketUtil.sendPacket(viewer, PacketPlayOutEntityMetadataHandle.createNew(this.entityId, metadata, true));
        }
        if (this.holderEntityId != -1) {
            this.spawnHolder(viewer);
        }
        return this;
    }

    public VirtualArmorStandItem updateItem(Iterable<Player> viewers) {
        if (this.entityId != -1) {
            PacketPlayOutEntityEquipmentHandle packet = PacketPlayOutEntityEquipmentHandle.createNew(this.entityId, EquipmentSlot.HEAD, this.item);
            for (Player viewer : viewers) {
                PacketUtil.sendPacket(viewer, packet);
            }
        }
        return this;
    }

    public VirtualArmorStandItem updateOrientation(Iterable<Player> viewers) {
        if (this.entityId != -1) {
            DataWatcher meta = new DataWatcher();
            meta.set(EntityArmorStandHandle.DATA_POSE_HEAD, Util.getArmorStandPose(orientation));
            PacketPlayOutEntityMetadataHandle packet = PacketPlayOutEntityMetadataHandle.createNew(this.entityId, meta, true);
            for (Player viewer : viewers) {
                PacketUtil.sendPacket(viewer, packet);
            }
        }
        return this;
    }

    public VirtualArmorStandItem updatePosition(Player viewer) {
        if (this.respawn) {
            if (this.holderEntityId == -1) {
                // Spawn a holder for the armorstand and mount it in it
                // This allows for super small movements
                this.spawnHolder(viewer);
            } else {
                // Respawn the armorstand holder
                PacketUtil.sendPacket(viewer, PacketType.OUT_ENTITY_DESTROY.newInstance(this.holderEntityId));
                this.spawnHolder(viewer);
            }
        } else {
            if (this.holderEntityId == -1) {
                // Teleport the armorstand itself
                PacketPlayOutEntityTeleportHandle tpPacket = PacketPlayOutEntityTeleportHandle.createNew(
                        this.entityId,
                        this.posX,
                        this.posY - ARMORSTAND_HEAD_OFFSET,
                        this.posZ,
                        0.0f, 0.0f, false);
                PacketUtil.sendPacket(viewer, tpPacket);
            } else {
                // Teleport the armorstand holder
                PacketPlayOutEntityTeleportHandle tpPacket = PacketPlayOutEntityTeleportHandle.createNew(
                        this.holderEntityId,
                        this.posX,
                        this.posY - (this.glowing ? BAT_HOLDER_OFFSET_GLOWING : BAT_HOLDER_OFFSET),
                        this.posZ,
                        0.0f, 0.0f, false);
                PacketUtil.sendPacket(viewer, tpPacket);
            }
        }

        return this;
    }

    public VirtualArmorStandItem destroy(Player viewer) {
        if (this.holderEntityId != -1 && this.entityId != -1) {
            PacketUtil.sendPacket(viewer, PacketType.OUT_ENTITY_DESTROY.newInstance(this.holderEntityId, this.entityId));
        } else if (this.entityId != -1) {
            PacketUtil.sendPacket(viewer, PacketType.OUT_ENTITY_DESTROY.newInstance(this.entityId));
        }
        return this;
    }

    public VirtualArmorStandItem spawn(Player viewer) {
        if (this.entityId == -1) {
            this.entityId = EntityUtil.getUniqueEntityId();
        }

        DataWatcher metadata = new DataWatcher();
        metadata.set(EntityArmorStandHandle.DATA_POSE_HEAD, Util.getArmorStandPose(orientation));
        metadata.set(EntityHandle.DATA_FLAGS, (byte) EntityHandle.DATA_FLAG_INVISIBLE);

        if (this.glowing && CAN_GLOW) {
            metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_GLOWING, true);
            metadata.setByte(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, EntityArmorStandHandle.DATA_FLAG_SET_MARKER);
            metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_ON_FIRE, CAN_FIRE_LIT);
        }

        PacketPlayOutSpawnEntityLivingHandle spawnPacket = PacketPlayOutSpawnEntityLivingHandle.createNew();
        spawnPacket.setEntityId(this.entityId);
        spawnPacket.setEntityUUID(UUID.randomUUID());
        spawnPacket.setEntityType(EntityType.ARMOR_STAND);
        spawnPacket.setPosX(this.posX);
        spawnPacket.setPosY(this.posY - ARMORSTAND_HEAD_OFFSET);
        spawnPacket.setPosZ(this.posZ);
        spawnPacket.setYaw(0.0f);
        spawnPacket.setPitch(0.0f);
        spawnPacket.setHeadYaw(0.0f);
        PacketUtil.sendEntityLivingSpawnPacket(viewer, spawnPacket, metadata);

        PacketUtil.sendPacket(viewer, PacketPlayOutEntityEquipmentHandle.createNew(this.entityId, EquipmentSlot.HEAD, this.item));

        // Put in a mount when we've allocated a holder entity ID
        // Not doing so can cause the object to not move when viewers respawn
        if (this.holderEntityId != -1) {
            this.spawnHolder(viewer);
        }

        return this;
    }

    private void spawnHolder(Player viewer) {
        if (this.holderEntityId == -1) {
            this.holderEntityId = EntityUtil.getUniqueEntityId();
        }

        // Spawn an invisible bat and attach it to that
        // Also used on newer mc versions when smooth movement is required
        // Smooth movement is desirable when animations are going to be played
        PacketPlayOutSpawnEntityLivingHandle holderPacket = PacketPlayOutSpawnEntityLivingHandle.createNew();
        DataWatcher holder_meta = new DataWatcher();
        holderPacket.setEntityId(this.holderEntityId);
        holderPacket.setEntityUUID(UUID.randomUUID());
        holderPacket.setPosX(this.posX);
        holderPacket.setPosY(this.posY - (this.glowing ? BAT_HOLDER_OFFSET_GLOWING : BAT_HOLDER_OFFSET));
        holderPacket.setPosZ(this.posZ);
        holderPacket.setEntityType(EntityType.BAT);
        holder_meta.set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE | EntityHandle.DATA_FLAG_FLYING));
        holder_meta.set(EntityHandle.DATA_SILENT, true);
        holder_meta.set(EntityInsentientHandle.DATA_INSENTIENT_FLAGS, (byte) EntityInsentientHandle.DATA_INSENTIENT_FLAG_NOAI);
        holder_meta.set(EntityLivingHandle.DATA_NO_GRAVITY, true);
        holder_meta.set(EntityBatHandle.DATA_BAT_FLAGS, (byte) 0);
        PacketUtil.sendEntityLivingSpawnPacket(viewer, holderPacket, holder_meta);

        if (PacketPlayOutMountHandle.T.isAvailable()) {
            PacketUtil.sendPacket(viewer, PacketPlayOutMountHandle.createNew(this.holderEntityId, new int[] {this.entityId}));
        } else {
            PacketUtil.sendPacket(viewer, PacketPlayOutAttachEntityHandle.createNewMount(this.entityId, this.holderEntityId));
        }
    }
}
