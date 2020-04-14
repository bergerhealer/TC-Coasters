package com.bergerkiller.bukkit.coasters.particles;

import java.util.UUID;

import org.bukkit.enchantments.Enchantment;
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
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityEquipmentHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityTeleportHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutSpawnEntityLivingHandle;

/**
 * Displays an item or 3D model using the head of an armorstand
 */
public class TrackParticleObject extends TrackParticle {
    private static final double ARMORSTAND_HEAD_OFFSET = 1.44;
    private DoubleOctree.Entry<TrackParticle> position;
    private Vector pose;
    private ItemStack item;
    private int entityId = -1;
    private boolean positionChanged = false;
    private boolean poseChanged = false;

    protected TrackParticleObject(Vector position, Quaternion orientation, ItemStack item) {
        this.position = DoubleOctree.Entry.create(position, this);
        this.pose = Util.getArmorStandPose(orientation);
        this.item = item;
    }

    public void setPositionOrientation(Vector position, Quaternion orientation) {
        Vector pose = Util.getArmorStandPose(orientation);
        if (!this.pose.equals(pose)) {
            this.pose.copy(pose);
            this.poseChanged = true;
            this.scheduleUpdateAppearance();
        }
        if (!this.position.equalsCoord(position)) {
            this.position = updatePosition(this.position, position);
            this.positionChanged = true;
            this.scheduleUpdateAppearance();
        }
    }

    @Override
    protected void onAdded() {
        addPosition(this.position);
    }

    @Override
    protected void onRemoved() {
        removePosition(this.position);
    }

    @Override
    public boolean isAlwaysVisible() {
        return true;
    }

    @Override
    public void makeVisibleFor(Player viewer) {
        if (this.entityId == -1) {
            this.entityId = EntityUtil.getUniqueEntityId();
        }

        TrackParticleState state = getState(viewer);

        DataWatcher metadata = new DataWatcher();
        metadata.set(EntityArmorStandHandle.DATA_POSE_HEAD, this.pose);
        metadata.set(EntityHandle.DATA_FLAGS, (byte) EntityHandle.DATA_FLAG_INVISIBLE);

        if (state == TrackParticleState.SELECTED) {
            metadata.setByte(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, EntityArmorStandHandle.DATA_FLAG_SET_MARKER);
            metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_GLOWING, true);
        }

        PacketPlayOutSpawnEntityLivingHandle spawnPacket = PacketPlayOutSpawnEntityLivingHandle.createNew();
        spawnPacket.setEntityId(this.entityId);
        spawnPacket.setEntityUUID(UUID.randomUUID());
        spawnPacket.setEntityType(EntityType.ARMOR_STAND);
        spawnPacket.setPosX(position.getX());
        spawnPacket.setPosY(position.getY() - ARMORSTAND_HEAD_OFFSET);
        spawnPacket.setPosZ(position.getZ());
        spawnPacket.setYaw(0.0f);
        spawnPacket.setPitch(0.0f);
        spawnPacket.setHeadYaw(0.0f);
        PacketUtil.sendEntityLivingSpawnPacket(viewer, spawnPacket, metadata);

        PacketUtil.sendPacket(viewer, PacketPlayOutEntityEquipmentHandle.createNew(this.entityId, EquipmentSlot.HEAD, this.item));
    }

    @Override
    public void makeHiddenFor(Player viewer) {
        if (this.entityId != -1) {
            PacketUtil.sendPacket(viewer, PacketType.OUT_ENTITY_DESTROY.newInstance(this.entityId));
        }
    }

    @Override
    public void onStateUpdated(Player viewer) {
        super.onStateUpdated(viewer);

        if (this.entityId != -1) {
            DataWatcher metadata = new DataWatcher();
            metadata.set(EntityHandle.DATA_FLAGS, (byte) EntityHandle.DATA_FLAG_INVISIBLE);
            if (getState(viewer) == TrackParticleState.SELECTED) {
                metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_GLOWING, true);
                metadata.setByte(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, EntityArmorStandHandle.DATA_FLAG_SET_MARKER);
            } else {
                metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_GLOWING, false);
                metadata.setByte(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, 0);
            }
            PacketUtil.sendPacket(viewer, PacketPlayOutEntityMetadataHandle.createNew(this.entityId, metadata, true));
        }

        /*
        PacketPlayOutEntityEquipmentHandle equipPacket = PacketPlayOutEntityEquipmentHandle.createNew(
                this.entityId, EquipmentSlot.HAND, this.itemType.getItem(state));
        PacketUtil.sendPacket(viewer, equipPacket);

        DataWatcher metadata = new DataWatcher();
        metadata.setByte(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_FLYING | EntityHandle.DATA_FLAG_INVISIBLE);
        metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_ON_FIRE, Common.evaluateMCVersion(">", "1.8"));
        metadata.setByte(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, EntityArmorStandHandle.DATA_FLAG_HAS_ARMS | EntityArmorStandHandle.DATA_FLAG_SET_MARKER);
        if (state == TrackParticleState.SELECTED && getWorld().getPlugin().getGlowingSelections()) {
            metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_GLOWING, true);
        }
        PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(this.entityId, metadata, true);
        PacketUtil.sendPacket(viewer, metaPacket);
        */
    }

    @Override
    public void updateAppearance() {
        if (this.positionChanged) {
            this.positionChanged = false;

            if (this.entityId != -1) {
                PacketPlayOutEntityTeleportHandle tpPacket = PacketPlayOutEntityTeleportHandle.createNew(
                        this.entityId,
                        this.position.getX(),
                        this.position.getY() - ARMORSTAND_HEAD_OFFSET,
                        this.position.getZ(),
                        0.0f, 0.0f, false);
                this.broadcastPacket(tpPacket);
            }
        }
        if (this.poseChanged) {
            this.poseChanged = false;

            if (this.entityId != -1) {
                DataWatcher meta = new DataWatcher();
                meta.set(EntityArmorStandHandle.DATA_POSE_HEAD, this.pose);
                this.broadcastPacket(PacketPlayOutEntityMetadataHandle.createNew(this.entityId, meta, true));
            }
        }
    }

    @Override
    public double distanceSquared(Vector viewerPosition) {
        return this.position.distanceSquared(viewerPosition);
    }

    @Override
    public boolean usesEntityId(int entityId) {
        return this.entityId == entityId;
    }
}
