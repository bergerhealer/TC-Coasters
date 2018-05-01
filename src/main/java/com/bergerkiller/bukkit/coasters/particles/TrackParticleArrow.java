package com.bergerkiller.bukkit.coasters.particles;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.generated.net.minecraft.server.EntityArmorStandHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityEquipmentHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityTeleportHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutSpawnEntityHandle;

/**
 * A particle consisting of a floating arrow pointing into a certain direction.
 * Uses an armor stand with a lever item rotated into the direction.
 */
public class TrackParticleArrow extends TrackParticle {
    private static final double VIEW_RADIUS = 64.0;
    private final ProtocolPosition prot = new ProtocolPosition();
    private TrackParticleItemType itemType = TrackParticleItemType.LEVER;
    private Vector position, direction;
    private boolean positionChanged = false;
    private boolean itemChanged = false;
    private int entityId = -1;

    public TrackParticleArrow(TrackParticleWorld world, Vector position, Vector direction) {
        super(world);
        this.position = position.clone();
        this.direction = direction.clone();
    }

    public void setPosition(Vector position) {
        if (!position.equals(this.position)) {
            this.position = position.clone();
            this.positionChanged = true;
        }
    }

    public void setDirection(Vector direction) {
        if (!direction.equals(this.direction)) {
            this.direction = direction.clone();
            this.positionChanged = true;
        }
    }

    public void setItemType(TrackParticleItemType itemType) {
        if (!this.itemType.equals(itemType)) {
            this.itemType = itemType;
            this.itemChanged = true;
        }
    }

    @Override
    public boolean isVisible(Vector viewerPosition) {
        return this.position.distanceSquared(viewerPosition) < (VIEW_RADIUS * VIEW_RADIUS);
    }

    @Override
    public void updateAppearance() {
        if (this.positionChanged) {
            this.positionChanged = false;

            if (this.entityId != -1) {
                this.prot.calculate(this.position, this.direction);

                PacketPlayOutEntityTeleportHandle tpPacket = PacketPlayOutEntityTeleportHandle.createNew(
                        this.entityId,
                        this.prot.posX,  this.prot.posY,  this.prot.posZ,
                        0.0f, 0.0f, false);
                this.broadcastPacket(tpPacket);

                DataWatcher metadata = new DataWatcher();
                metadata.set(EntityArmorStandHandle.DATA_POSE_ARM_RIGHT, new Vector(prot.rotX, prot.rotY, prot.rotZ));
                PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(this.entityId, metadata, true);
                this.broadcastPacket(metaPacket);
            }
        }
        if (this.itemChanged) {
            this.itemChanged = false;

            for (Player viewer : this.getViewers()) {
                PacketPlayOutEntityEquipmentHandle equipPacket = PacketPlayOutEntityEquipmentHandle.createNew(
                        this.entityId, EquipmentSlot.HAND, this.itemType.getItem(this.getState(viewer)));
                PacketUtil.sendPacket(viewer, equipPacket);
            }
        }
    }

    @Override
    public void onStateUpdated(Player viewer) {
        super.onStateUpdated(viewer);

        PacketPlayOutEntityEquipmentHandle equipPacket = PacketPlayOutEntityEquipmentHandle.createNew(
                this.entityId, EquipmentSlot.HAND, this.itemType.getItem(this.getState(viewer)));
        PacketUtil.sendPacket(viewer, equipPacket);
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

        prot.calculate(this.position, this.direction);

        PacketPlayOutSpawnEntityHandle spawnPacket = PacketPlayOutSpawnEntityHandle.T.newHandleNull();
        spawnPacket.setEntityId(this.entityId);
        spawnPacket.setEntityUUID(UUID.randomUUID());
        spawnPacket.setEntityTypeId(78);
        spawnPacket.setPosX(prot.posX);
        spawnPacket.setPosY(prot.posY);
        spawnPacket.setPosZ(prot.posZ);
        PacketUtil.sendPacket(viewer, spawnPacket);

        DataWatcher metadata = new DataWatcher();
        metadata.set(EntityHandle.DATA_NO_GRAVITY, true);
        metadata.set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_FLYING | EntityHandle.DATA_FLAG_INVISIBLE));
        metadata.set(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, (byte) EntityArmorStandHandle.DATA_FLAG_HAS_ARMS);
        metadata.set(EntityArmorStandHandle.DATA_POSE_ARM_RIGHT, new Vector(prot.rotX, prot.rotY, prot.rotZ));
        PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(this.entityId, metadata, true);
        PacketUtil.sendPacket(viewer, metaPacket);

        PacketPlayOutEntityEquipmentHandle equipPacket = PacketPlayOutEntityEquipmentHandle.createNew(
                this.entityId, EquipmentSlot.HAND, this.itemType.getItem(this.getState(viewer)));
        PacketUtil.sendPacket(viewer, equipPacket);
    }

    private static class ProtocolPosition {
        public double posX, posY, posZ;
        public double rotX, rotY, rotZ;

        public void calculate(Vector position, Vector direction) {
            Quaternion orientation = Quaternion.fromLookDirection(direction, new Vector(0, 1, 0));
            Vector ypr = orientation.getYawPitchRoll();
            this.rotX = ypr.getX();
            this.rotY = ypr.getY();
            this.rotZ = ypr.getZ();

            this.posX = position.getX() + 0.315;
            this.posY = position.getY() - 1.35;
            this.posZ = position.getZ();

            // Cancel the offset of the arm relative to the yaw rotation point (ypr.y)
            this.posX += 0.06 * Math.cos(Math.toRadians(ypr.getY()));
            this.posZ += 0.06 * Math.sin(Math.toRadians(ypr.getY()));

            // Cancel relative positioning of the item itself
            Vector upVector =  new Vector(0.0, 0.56, -0.05);
            orientation.transformPoint(upVector);
            this.posX += upVector.getX();
            this.posY += upVector.getY();
            this.posZ += upVector.getZ();
        }
    }
}
