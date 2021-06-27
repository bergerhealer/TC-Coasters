package com.bergerkiller.bukkit.coasters.particles;

import java.util.UUID;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.collections.octree.DoubleOctree;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityDestroyHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityTeleportHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.item.EntityItemHandle;

/**
 * A particle consisting of a floating item
 */
public class TrackParticleItem extends TrackParticle {
    protected static final int FLAG_POSITION_CHANGED  = (1<<2);
    protected static final int FLAG_ITEM_CHANGED      = (1<<3);
    private static final Vector OFFSET = new Vector(0.0, -0.34, 0.0);
    private TrackParticleItemType itemType = TrackParticleItemType.DEFAULT;
    private DoubleOctree.Entry<TrackParticle> position;
    private int entityId = -1;
    private UUID entityUUID = null;

    public TrackParticleItem(Vector position) {
        this.position = DoubleOctree.Entry.create(position, this);
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
        if (this.position.distanceSquared(position) > (0.001 * 0.001)) {
            this.position = updatePosition(this.position, position);
            this.setFlag(FLAG_POSITION_CHANGED);
            this.scheduleUpdateAppearance();
        }
    }

    public void setItemType(TrackParticleItemType itemType) {
        if (!this.itemType.equals(itemType)) {
            this.itemType = itemType;
            this.setFlag(FLAG_ITEM_CHANGED);
            this.scheduleUpdateAppearance();
        }
    }

    @Override
    public double distanceSquared(Vector viewerPosition) {
        return this.position.distanceSquared(viewerPosition);
    }

    @Override
    public void updateAppearance() {
        if (this.clearFlag(FLAG_POSITION_CHANGED) && this.entityId != -1) {
            PacketPlayOutEntityTeleportHandle tpPacket = PacketPlayOutEntityTeleportHandle.createNew(
                    this.entityId,
                    this.position.getX() + OFFSET.getX(),
                    this.position.getY() + OFFSET.getY(),
                    this.position.getZ() + OFFSET.getZ(),
                    0.0f, 0.0f, false);
            broadcastPacket(tpPacket);
        }
        if (this.clearFlag(FLAG_ITEM_CHANGED) && this.entityId != -1) {
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
        spawnPacket.setEntityType(EntityType.DROPPED_ITEM);
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
            PacketUtil.sendPacket(viewer, PacketPlayOutEntityDestroyHandle.createNewSingle(this.entityId));
        }
    }

    @Override
    public boolean usesEntityId(int entityId) {
        return this.entityId == entityId;
    }
}
