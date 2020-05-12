package com.bergerkiller.bukkit.coasters.particles;

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
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityEquipmentHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityTeleportHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutSpawnEntityLivingHandle;

/**
 * Displays an item or 3D model using the head of an armorstand
 */
public class TrackParticleArmorStandItem extends TrackParticle {
    protected static final int FLAG_POSITION_CHANGED  = (1<<2);
    protected static final int FLAG_POSE_CHANGED      = (1<<3);
    protected static final int FLAG_ITEM_CHANGED      = (1<<4);
    private static final double ARMORSTAND_HEAD_OFFSET = 1.44;
    private DoubleOctree.Entry<TrackParticle> position;
    private Quaternion orientation;
    private ItemStack item;
    private int entityId = -1;

    protected TrackParticleArmorStandItem(Vector position, Quaternion orientation, ItemStack item) {
        this.position = DoubleOctree.Entry.create(position, this);
        this.orientation = orientation.clone();
        this.item = item;
    }

    public void setPositionOrientation(Vector position, Quaternion orientation) {
        if (!this.orientation.equals(orientation)) {
            this.orientation.setTo(orientation);
            this.setFlag(FLAG_POSE_CHANGED);
            this.scheduleUpdateAppearance();
        }
        if (!this.position.equalsCoord(position)) {
            this.position = updatePosition(this.position, position);
            this.setFlag(FLAG_POSITION_CHANGED);
            this.scheduleUpdateAppearance();
        }
    }

    public void setItem(ItemStack item) {
        if (this.item != item && !this.item.equals(item)) {
            this.item = item;
            this.setFlag(FLAG_ITEM_CHANGED);
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
    public void makeHiddenFor(Player viewer) {
        if (this.entityId != -1) {
            PacketUtil.sendPacket(viewer, PacketType.OUT_ENTITY_DESTROY.newInstance(this.entityId));
        }
    }

    @Override
    public void makeVisibleFor(Player viewer) {
        if (this.entityId == -1) {
            this.entityId = EntityUtil.getUniqueEntityId();
        }

        TrackParticleState state = getState(viewer);

        DataWatcher metadata = new DataWatcher();
        metadata.set(EntityArmorStandHandle.DATA_POSE_HEAD, Util.getArmorStandPose(orientation));
        metadata.set(EntityHandle.DATA_FLAGS, (byte) EntityHandle.DATA_FLAG_INVISIBLE);

        if (state == TrackParticleState.SELECTED) {
            metadata.setByte(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, EntityArmorStandHandle.DATA_FLAG_SET_MARKER);
            metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_GLOWING, true);
            metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_ON_FIRE, Common.evaluateMCVersion(">", "1.8"));
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
    public void onStateUpdated(Player viewer) {
        super.onStateUpdated(viewer);

        TrackParticleState state = getState(viewer);

        if (this.entityId != -1) {
            DataWatcher metadata = new DataWatcher();
            metadata.set(EntityHandle.DATA_FLAGS, (byte) EntityHandle.DATA_FLAG_INVISIBLE);
            if (state == TrackParticleState.SELECTED) {
                metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_GLOWING, true);
                metadata.setByte(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, EntityArmorStandHandle.DATA_FLAG_SET_MARKER);
                metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_ON_FIRE, Common.evaluateMCVersion(">", "1.8"));
            } else {
                metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_GLOWING, false);
                metadata.setByte(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, 0);
            }
            PacketUtil.sendPacket(viewer, PacketPlayOutEntityMetadataHandle.createNew(this.entityId, metadata, true));
        }

        /*
        VirtualArrowItem.create(this.markerA_entityId).destroy(viewer);
        VirtualArrowItem.create(this.markerB_entityId).destroy(viewer);

        if (state == TrackParticleState.SELECTED) {
            spawnMarkers(viewer);
        }
        */
    }

    @Override
    public void updateAppearance() {
        if (this.clearFlag(FLAG_POSITION_CHANGED)) {
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
        if (this.clearFlag(FLAG_POSE_CHANGED) && this.entityId != -1) {
            DataWatcher meta = new DataWatcher();
            meta.set(EntityArmorStandHandle.DATA_POSE_HEAD, Util.getArmorStandPose(orientation));
            this.broadcastPacket(PacketPlayOutEntityMetadataHandle.createNew(this.entityId, meta, true));
        }
        if (this.clearFlag(FLAG_ITEM_CHANGED) && this.entityId != -1) {
            this.broadcastPacket(PacketPlayOutEntityEquipmentHandle.createNew(this.entityId, EquipmentSlot.HEAD, this.item));
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
