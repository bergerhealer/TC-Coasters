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
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityDestroyHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityEquipmentHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityTeleportHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.decoration.EntityArmorStandHandle;

/**
 * Helper class for spawning and updating a virtual arrow item.
 * This is an item held by an invisible armor stand, pointing
 * in a given orientation.
 */
public class VirtualArrowItem {
    private static final Vector UP_ARM_OFFSET = new Vector(0.05, -0.05, -0.56);

    private static final DataWatcher.Prototype SPAWN_METADATA = DataWatcher.Prototype.build()
            .set(EntityHandle.DATA_NO_GRAVITY, true)
            .setClientByteDefault(EntityHandle.DATA_FLAGS, 0)
            .setByte(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, EntityArmorStandHandle.DATA_FLAG_SET_MARKER | EntityArmorStandHandle.DATA_FLAG_NO_BASEPLATE)
            .setClientDefault(EntityArmorStandHandle.DATA_POSE_ARM_RIGHT, new Vector(15.0, 0.0, 10.0))
            .create();

    private int entityId;
    private boolean glowing = false;
    private double posX, posY, posZ;
    private Vector rotation;
    private ItemStack item;

    private VirtualArrowItem(int entityId) {
        this.entityId = entityId;
    }

    /**
     * Initializes a new VirtualArrowItem state
     * 
     * @param entityId The entity id of the item, -1 if a new one is being spawned
     * @return VirtualArrowItem
     */
    public static VirtualArrowItem create(int entityId) {
        return new VirtualArrowItem(entityId);
    }

    public boolean hasEntityId() {
        return this.entityId != -1;
    }

    public VirtualArrowItem glowing(boolean glowing) {
        this.glowing = glowing;
        return this;
    }

    public VirtualArrowItem item(ItemStack item) {
        this.item = item;
        return this;
    }

    public VirtualArrowItem position(DoubleOctree.Entry<?> position, Quaternion orientation) {
        return position(position.getX(), position.getY(), position.getZ(), orientation);
    }

    public VirtualArrowItem position(Vector position, Quaternion orientation) {
        return position(position.getX(), position.getY(), position.getZ(), orientation);
    }

    public VirtualArrowItem position(double posX, double posY, double posZ, Quaternion orientation) {
        // Use direction for rotX/rotZ, and up vector for rotY rotation around it
        // This creates an arrow that smoothly rotates around its center point using rotY
        this.rotation = Util.getArmorStandPose(orientation);
        this.rotation.setX(this.rotation.getX() - 90.0);

        // Cancel relative positioning of the item itself
        Vector upOffset = UP_ARM_OFFSET.clone();
        orientation.transformPoint(upOffset);

        // Update
        this.posX = posX + upOffset.getX() + 0.315;
        this.posY = posY + upOffset.getY() - 1.35;
        this.posZ = posZ + upOffset.getZ();

        return this;
    }

    /**
     * Refreshes the position and orientation of this virtual arrow item.
     * Only the position property has to be set.
     * 
     * @param viewers Players to which to send the move and pose packets
     * @return this
     */
    public VirtualArrowItem move(Iterable<Player> viewers) {
        if (this.entityId != -1) {
            PacketPlayOutEntityTeleportHandle tpPacket = PacketPlayOutEntityTeleportHandle.createNew(
                    this.entityId,
                    this.posX,  this.posY,  this.posZ,
                    0.0f, 0.0f, false);

            DataWatcher metadata = new DataWatcher();
            metadata.set(EntityArmorStandHandle.DATA_POSE_ARM_RIGHT, this.rotation);
            PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(this.entityId, metadata, true);

            for (Player viewer : viewers) {
                PacketUtil.sendPacket(viewer, tpPacket);
                PacketUtil.sendPacket(viewer, metaPacket);
            }
        }
        return this;
    }

    /**
     * Refreshes the item of this virtual arrow item.
     * Only the item property has to be set.
     * 
     * @param viewer Player to which to send the update packets
     * @return this
     */
    public VirtualArrowItem updateItem(Player viewer) {
        if (this.entityId != -1) {
            PacketPlayOutEntityEquipmentHandle equipPacket = PacketPlayOutEntityEquipmentHandle.createNew(
                    this.entityId, EquipmentSlot.HAND, this.item);
            PacketUtil.sendPacket(viewer, equipPacket);
        }
        return this;
    }

    /**
     * Refreshes the glowing state of this virtual arrow item.
     * Only the glowing property has to be set.
     * 
     * @param viewer Player to which to send the update packets
     * @return this
     */
    public VirtualArrowItem updateGlowing(Player viewer) {
        if (this.entityId != -1) {
            DataWatcher metadata = new DataWatcher();
            metadata.setByte(EntityHandle.DATA_FLAGS, computeFlags());
            PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(this.entityId, metadata, true);
            PacketUtil.sendPacket(viewer, metaPacket);
        }
        return this;
    }

    /**
     * Spawns the entity.
     * All properties must be set.
     * 
     * @param viewer Player to which to send the spawn packets
     * @return entity id of the spawned entity (generated if -1 in {@link #create(entityId)})
     */
    public int spawn(Player viewer) {
        if (this.entityId == -1) {
            this.entityId = EntityUtil.getUniqueEntityId();
        }
        PacketPlayOutSpawnEntityLivingHandle spawnPacket = PacketPlayOutSpawnEntityLivingHandle.T.newHandleNull();
        spawnPacket.setEntityId(this.entityId);
        spawnPacket.setEntityUUID(UUID.randomUUID());
        spawnPacket.setEntityType(EntityType.ARMOR_STAND);
        spawnPacket.setPosX(posX);
        spawnPacket.setPosY(posY);
        spawnPacket.setPosZ(posZ);

        DataWatcher metadata = SPAWN_METADATA.create();
        metadata.setByte(EntityHandle.DATA_FLAGS, computeFlags());
        metadata.set(EntityArmorStandHandle.DATA_POSE_ARM_RIGHT, rotation);

        PacketUtil.sendEntityLivingSpawnPacket(viewer, spawnPacket, metadata);

        PacketPlayOutEntityEquipmentHandle equipPacket = PacketPlayOutEntityEquipmentHandle.createNew(
                this.entityId, EquipmentSlot.HAND, this.item);
        PacketUtil.sendPacket(viewer, equipPacket);

        return this.entityId;
    }

    private byte computeFlags() {
        int flags = EntityHandle.DATA_FLAG_FLYING | EntityHandle.DATA_FLAG_INVISIBLE;
        if (Common.evaluateMCVersion(">", "1.8")) {
            flags |= EntityHandle.DATA_FLAG_ON_FIRE;
        }
        if (glowing) {
            flags |= EntityHandle.DATA_FLAG_GLOWING;
        }
        return (byte) flags;
    }

    /**
     * Destroys the virtual arrow item (if spawned and entity id was not set to -1).
     * No properties have to be set.
     * 
     * @param viewer Player to which to send destroy packets
     */
    public void destroy(Player viewer) {
        if (this.entityId != -1) {
            PacketUtil.sendPacket(viewer, PacketPlayOutEntityDestroyHandle.createNewSingle(this.entityId));
        }
    }

    /**
     * Calculates the distance an armorstand will move to correct for a change in arm
     * rotation.
     *
     * @param oldRot
     * @param newRot
     * @return arm rotation distance (squared)
     */
    public static double getArmRotationDistanceSquared(Quaternion oldRot, Quaternion newRot) {
        Vector oldArmPos = UP_ARM_OFFSET.clone();
        oldRot.transformPoint(oldArmPos);
        Vector newArmPos = UP_ARM_OFFSET.clone();
        newRot.transformPoint(newArmPos);
        return oldArmPos.distanceSquared(newArmPos);
    }
}