package com.bergerkiller.bukkit.coasters.particles;

import java.util.UUID;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
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
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutSpawnEntityHandle;

/**
 * A particle consisting of a floating arrow pointing into a certain direction.
 * Uses an armor stand with a lever item rotated into the direction. The direction
 * is along which is rotated, the up vector indicates the roll around this vector.
 */
public class TrackParticleArrow extends TrackParticle {
    private final ProtocolPosition prot = new ProtocolPosition();
    private TrackParticleItemType itemType = TrackParticleItemType.LEVER;
    private DoubleOctree.Entry<TrackParticle> position;
    private Quaternion orientation;
    private boolean positionChanged = false;
    private boolean itemChanged = false;
    private int entityId = -1;

    protected TrackParticleArrow(Vector position, Quaternion orientation) {
        this.position = DoubleOctree.Entry.create(position, this);
        this.orientation = orientation.clone();
    }

    @Override
    protected void onAdded() {
        addPosition(this.position);
    }

    @Override
    protected void onRemoved() {
        removePosition(this.position);
    }

    public void setPosition(Vector position) {
        if (!this.position.equalsCoord(position)) {
            this.position = updatePosition(this.position, position);
            this.positionChanged = true;
            this.scheduleUpdateAppearance();
        }
    }

    public void setDirection(Vector direction, Vector up) {
        this.setOrientation(Quaternion.fromLookDirection(direction, up));
    }

    public void setOrientation(Quaternion orientation) {
        if (!orientation.equals(this.orientation)) {
            this.orientation = orientation.clone();
            this.positionChanged = true;
            this.scheduleUpdateAppearance();
        }
    }

    public Quaternion getOrientation() {
        return this.orientation;
    }

    public void setItemType(TrackParticleItemType itemType) {
        if (!this.itemType.equals(itemType)) {
            this.itemType = itemType;
            this.itemChanged = true;
            this.scheduleUpdateAppearance();
        }
    }

    @Override
    public double distanceSquared(Vector viewerPosition) {
        return this.position.distanceSquared(viewerPosition);
    }

    @Override
    public void updateAppearance() {
        if (this.positionChanged) {
            this.positionChanged = false;

            if (this.entityId != -1) {
                this.prot.calculate(this.position.toVector(), this.orientation);

                PacketPlayOutEntityTeleportHandle tpPacket = PacketPlayOutEntityTeleportHandle.createNew(
                        this.entityId,
                        this.prot.posX,  this.prot.posY,  this.prot.posZ,
                        0.0f, 0.0f, false);
                this.broadcastPacket(tpPacket);

                DataWatcher metadata = new DataWatcher();
                metadata.set(EntityArmorStandHandle.DATA_POSE_ARM_RIGHT, prot.rotation);
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

        TrackParticleState state = getState(viewer);

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

        prot.calculate(this.position.toVector(), this.orientation);

        PacketPlayOutSpawnEntityHandle spawnPacket = PacketPlayOutSpawnEntityHandle.T.newHandleNull();
        spawnPacket.setEntityId(this.entityId);
        spawnPacket.setEntityUUID(UUID.randomUUID());
        spawnPacket.setEntityType(EntityType.ARMOR_STAND);
        spawnPacket.setPosX(prot.posX);
        spawnPacket.setPosY(prot.posY);
        spawnPacket.setPosZ(prot.posZ);
        PacketUtil.sendPacket(viewer, spawnPacket);

        DataWatcher metadata = new DataWatcher();
        metadata.set(EntityHandle.DATA_NO_GRAVITY, true);
        metadata.setByte(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_FLYING | EntityHandle.DATA_FLAG_INVISIBLE);
        metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_ON_FIRE, Common.evaluateMCVersion(">", "1.8"));
        metadata.setByte(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, EntityArmorStandHandle.DATA_FLAG_HAS_ARMS | EntityArmorStandHandle.DATA_FLAG_SET_MARKER);
        if (state == TrackParticleState.SELECTED && getWorld().getPlugin().getGlowingSelections()) {
            metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_GLOWING, true);
        }
        metadata.set(EntityArmorStandHandle.DATA_POSE_ARM_RIGHT, prot.rotation);
        PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(this.entityId, metadata, true);
        PacketUtil.sendPacket(viewer, metaPacket);

        PacketPlayOutEntityEquipmentHandle equipPacket = PacketPlayOutEntityEquipmentHandle.createNew(
                this.entityId, EquipmentSlot.HAND, this.itemType.getItem(state));
        PacketUtil.sendPacket(viewer, equipPacket);
    }

    @Override
    public boolean usesEntityId(int entityId) {
        return this.entityId == entityId;
    }

    private static class ProtocolPosition {
        public double posX, posY, posZ;
        public Vector rotation;

        public void calculate(Vector position, Quaternion orientation) {
            // Use direction for rotX/rotZ, and up vector for rotY rotation around it
            // This creates an arrow that smoothly rotates around its center point using rotY
            this.rotation = Util.getArmorStandPose(orientation);
            this.rotation.setX(this.rotation.getX() - 90.0);

            // Absolute position
            this.posX = position.getX() + 0.315;
            this.posY = position.getY() - 1.35;
            this.posZ = position.getZ();

            // Cancel relative positioning of the item itself
            Vector upVector =  new Vector(0.05, -0.05, -0.56);
            orientation.transformPoint(upVector);
            this.posX += upVector.getX();
            this.posY += upVector.getY();
            this.posZ += upVector.getZ();
        }

    }
}
