package com.bergerkiller.bukkit.coasters.particles;

import java.util.Collections;
import java.util.UUID;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.util.VirtualArrowItem;
import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.collections.octree.DoubleOctree;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.generated.net.minecraft.server.EntityArmorStandHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityTeleportHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutSpawnEntityHandle;

public class TrackParticleFallingBlock extends TrackParticle {
    private static final ItemStack MARKER_ITEM = new ItemStack(MaterialUtil.getFirst("REDSTONE_TORCH", "LEGACY_REDSTONE_TORCH_ON"));
    private DoubleOctree.Entry<TrackParticle> position;
    private Quaternion orientation;
    private BlockData material;
    private double width;
    private int entityId = -1;
    private int markerA_entityId = -1;
    private int markerB_entityId = -1;
    private boolean positionChanged = false;
    private boolean materialChanged = false;

    protected TrackParticleFallingBlock(Vector position, Quaternion orientation, BlockData material, double width) {
        this.position = DoubleOctree.Entry.create(position, this);
        this.orientation = orientation.clone();
        this.material = material;
        this.width = width;
    }

    public void setPositionOrientation(Vector position, Quaternion orientation) {
        if (!this.orientation.equals(orientation)) {
            this.orientation.setTo(orientation);
            this.positionChanged = true;
            this.scheduleUpdateAppearance();
        }
        if (!this.position.equalsCoord(position)) {
            this.position = updatePosition(this.position, position);
            this.positionChanged = true;
            this.scheduleUpdateAppearance();
        }
    }

    public void setMaterial(BlockData material) {
        if (this.material != material) {
            this.material = material;
            this.materialChanged = true;
            this.scheduleUpdateAppearance();
        }
    }

    public void setWidth(double width) {
        if (this.width != width) {
            this.width = width;
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

        Vector ypr = this.orientation.getYawPitchRoll();

        PacketPlayOutSpawnEntityHandle spawnPacket = PacketPlayOutSpawnEntityHandle.createNew();
        spawnPacket.setEntityId(this.entityId);
        spawnPacket.setEntityUUID(UUID.randomUUID());
        spawnPacket.setEntityType(EntityType.FALLING_BLOCK);
        spawnPacket.setPosX(position.getX());
        spawnPacket.setPosY(position.getY());
        spawnPacket.setPosZ(position.getZ());
        spawnPacket.setYaw((float) ypr.getY());
        spawnPacket.setPitch((float) ypr.getX());
        spawnPacket.setFallingBlockData(material);
        PacketUtil.sendPacket(viewer, spawnPacket);

        DataWatcher metadata = new DataWatcher();
        metadata.set(EntityHandle.DATA_NO_GRAVITY, true);

        if (state == TrackParticleState.SELECTED) {
            metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_GLOWING, true);
            metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_ON_FIRE, Common.evaluateMCVersion(">", "1.8"));
        }

        PacketUtil.sendPacket(viewer, PacketPlayOutEntityMetadataHandle.createNew(this.entityId, metadata, true));

        if (state == TrackParticleState.SELECTED) {
            spawnMarkers(viewer);
        }
    }

    private void spawnMarkers(Player viewer) {
        // Spawn 1 or 2 marker entities to denote the position on the path
        if (this.width <= 0.0) {
            this.markerA_entityId = VirtualArrowItem.create(this.markerA_entityId)
                    .item(MARKER_ITEM)
                    .position(this.position, this.orientation)
                    .spawn(viewer);
        } else {
            Vector dir = this.orientation.forwardVector().multiply(0.5 * this.width);
            this.markerA_entityId = VirtualArrowItem.create(this.markerA_entityId)
                    .item(MARKER_ITEM)
                    .position(this.position.toVector().subtract(dir), this.orientation)
                    .spawn(viewer);
            this.markerB_entityId = VirtualArrowItem.create(this.markerB_entityId)
                    .item(MARKER_ITEM)
                    .position(this.position.toVector().add(dir), this.orientation)
                    .spawn(viewer);
        }
    }

    private void moveMarkers(Player viewer) {
        // Move 1 or 2 marker entities to denote the position on the path
        if (this.width <= 0.0) {
            // Move first item
            VirtualArrowItem.create(this.markerA_entityId)
                    .position(this.position, this.orientation)
                    .move(Collections.singleton(viewer));

            // Despawn second item if a previous width was set
            if (this.markerB_entityId != -1) {
                VirtualArrowItem.create(this.markerB_entityId).destroy(viewer);
            }
        } else {
            // Move both items
            Vector dir = this.orientation.forwardVector().multiply(0.5 * this.width);
            VirtualArrowItem itemA = VirtualArrowItem.create(this.markerA_entityId)
                    .item(MARKER_ITEM)
                    .position(this.position.toVector().subtract(dir), this.orientation);
            VirtualArrowItem itemB = VirtualArrowItem.create(this.markerB_entityId)
                    .item(MARKER_ITEM)
                    .position(this.position.toVector().add(dir), this.orientation);

            if (itemA.hasEntityId()) {
                itemA.move(Collections.singleton(viewer));
            } else {
                this.markerA_entityId = itemA.spawn(viewer);
            }
            if (itemB.hasEntityId()) {
                itemB.move(Collections.singleton(viewer));
            } else {
                this.markerB_entityId = itemB.spawn(viewer);
            }
        }
    }

    @Override
    public void makeHiddenFor(Player viewer) {
        if (this.entityId != -1) {
            PacketUtil.sendPacket(viewer, PacketType.OUT_ENTITY_DESTROY.newInstance(this.entityId));
        }
        if (this.markerA_entityId != -1) {
            VirtualArrowItem.create(this.markerA_entityId).destroy(viewer);
        }
        if (this.markerB_entityId != -1) {
            VirtualArrowItem.create(this.markerB_entityId).destroy(viewer);
        }
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

        VirtualArrowItem.create(this.markerA_entityId).destroy(viewer);
        VirtualArrowItem.create(this.markerB_entityId).destroy(viewer);

        if (state == TrackParticleState.SELECTED) {
            spawnMarkers(viewer);
        }
    }

    @Override
    public void updateAppearance() {
        if (this.materialChanged) {
            this.materialChanged = false;
            this.positionChanged = false;

            for (Player viewer : this.getViewers()) {
                this.makeHiddenFor(viewer);
                this.makeVisibleFor(viewer);
            }
        }
        if (this.positionChanged) {
            this.positionChanged = false;

            if (this.entityId != -1) {
                Vector ypr = this.orientation.getYawPitchRoll();
                PacketPlayOutEntityTeleportHandle tpPacket = PacketPlayOutEntityTeleportHandle.createNew(
                        this.entityId,
                        this.position.getX(),
                        this.position.getY(),
                        this.position.getZ(),
                        (float) ypr.getY(), (float) ypr.getX(), false);
                this.broadcastPacket(tpPacket);
            }

            for (Player viewer : this.getViewers()) {
                if (getState(viewer) == TrackParticleState.SELECTED) {
                    moveMarkers(viewer);
                }
            }

            if (this.width <= 0.0) {
                this.markerB_entityId = -1;
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