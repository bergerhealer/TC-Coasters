package com.bergerkiller.bukkit.coasters.particles;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityItemHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityTeleportHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutSpawnEntityHandle;

/**
 * A particle consisting of a floating item
 */
public class TrackParticleItem extends TrackParticle {
    private static final Vector OFFSET = new Vector(0.0, -0.34, 0.0);
    private static final double VIEW_RADIUS = 25.0;
    private TrackParticleItemType itemType = TrackParticleItemType.DEFAULT;
    private Vector position;
    private int entityId = -1;
    private UUID entityUUID = null;
    private boolean positionChanged = false;
    private boolean itemChanged = false;

    public TrackParticleItem(TrackParticleWorld world, Vector position) {
        super(world);
        this.position = position.clone();
    }

    public void setPosition(Vector position) {
        if (position.distanceSquared(this.position) > (0.001 * 0.001)) {
            this.position.setX(position.getX());
            this.position.setY(position.getY());
            this.position.setZ(position.getZ());
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
    public double distanceSquared(Vector viewerPosition) {
        return this.position.distanceSquared(viewerPosition);
    }

    @Override
    public double getViewDistance() {
        return VIEW_RADIUS;
    }

    @Override
    public void updateAppearance() {
        if (this.positionChanged) {
            this.positionChanged = false;
            if (this.entityId != -1) {
                PacketPlayOutEntityTeleportHandle tpPacket = PacketPlayOutEntityTeleportHandle.createNew(
                        this.entityId,
                        this.position.getX() + OFFSET.getX(),
                        this.position.getY() + OFFSET.getY(),
                        this.position.getZ() + OFFSET.getZ(),
                        0.0f, 0.0f, false);
                broadcastPacket(tpPacket);
            }
        }
        if (this.itemChanged) {
            this.itemChanged = false;

            for (Player viewer : this.getViewers()) {
                DataWatcher metadata = new DataWatcher();
                metadata.set(EntityItemHandle.DATA_ITEM, this.itemType.getItem(this.getState(viewer)));
                PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(this.entityId, metadata, true);
                PacketUtil.sendPacket(viewer, metaPacket);
            }
        }
    }

    @Override
    public void onStateUpdated(Player viewer) {
        super.onStateUpdated(viewer);

        DataWatcher metadata = new DataWatcher();
        metadata.set(EntityItemHandle.DATA_ITEM, this.itemType.getItem(this.getState(viewer)));
        PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(this.entityId, metadata, true);
        PacketUtil.sendPacket(viewer, metaPacket);
    }

    @Override
    public void makeVisibleFor(Player viewer) {
        if (this.entityId == -1) {
            this.entityId = EntityUtil.getUniqueEntityId();
            this.entityUUID = UUID.randomUUID();
        }

        PacketPlayOutSpawnEntityHandle spawnPacket = PacketPlayOutSpawnEntityHandle.T.newHandleNull();
        spawnPacket.setEntityId(this.entityId);
        spawnPacket.setEntityUUID(this.entityUUID);
        spawnPacket.setEntityTypeId(2);
        spawnPacket.setExtraData(1);
        spawnPacket.setPosX(this.position.getX() + OFFSET.getX());
        spawnPacket.setPosY(this.position.getY() + OFFSET.getY());
        spawnPacket.setPosZ(this.position.getZ() + OFFSET.getZ());
        spawnPacket.setMotX(0.0);
        spawnPacket.setMotY(0.0);
        spawnPacket.setMotZ(0.0);
        spawnPacket.setPitch(0.0f);
        spawnPacket.setYaw(0.0f);
        PacketUtil.sendPacket(viewer, spawnPacket);

        DataWatcher metadata = new DataWatcher();
        metadata.set(EntityItemHandle.DATA_ITEM, this.itemType.getItem(this.getState(viewer)));
        metadata.set(EntityHandle.DATA_NO_GRAVITY, true);
        metadata.set(EntityHandle.DATA_FLAGS, (byte) EntityHandle.DATA_FLAG_FLYING);
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
    public boolean usesEntityId(int entityId) {
        return this.entityId == entityId;
    }
}
