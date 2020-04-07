package com.bergerkiller.bukkit.coasters.particles;

import java.util.UUID;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.collections.octree.DoubleOctree;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityTeleportHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutSpawnEntityHandle;

public class TrackParticleLitBlock extends TrackParticle {
    private DoubleOctree.Entry<TrackParticle> position;
    private int entityId = -1;
    private boolean positionChanged = false;

    public TrackParticleLitBlock(IntVector3 block) {
        this.position = DoubleOctree.Entry.create(block.midX(), block.y, block.midZ(), this);
    }

    @Override
    protected void onAdded() {
        addPosition(this.position);
    }

    @Override
    protected void onRemoved() {
        removePosition(this.position);
    }

    public void setBlock(IntVector3 block) {
        if (block != null && !this.position.equalsBlockCoord(block)) {
            this.position = updatePosition(this.position, DoubleOctree.Entry.create(block.midX(), block.y, block.midZ(), this));
            this.positionChanged = true;
            this.scheduleUpdateAppearance();
        }
    }

    @Override
    public boolean isVisible(Player viewer) {
        return getState(viewer) != TrackParticleState.HIDDEN;
    }

    @Override
    public double distanceSquared(Vector viewerPosition) {
        return this.position.distanceSquared(viewerPosition);
    }

    @Override
    public void makeVisibleFor(Player viewer) {
        if (this.entityId == -1) {
            this.entityId = EntityUtil.getUniqueEntityId();
        }

        PacketPlayOutSpawnEntityHandle packet = PacketPlayOutSpawnEntityHandle.T.newHandleNull();
        packet.setEntityId(this.entityId);
        packet.setEntityUUID(UUID.randomUUID());
        packet.setPosX(this.position.getX());
        packet.setPosY(this.position.getY());
        packet.setPosZ(this.position.getZ());
        packet.setEntityType(EntityType.FALLING_BLOCK);
        packet.setFallingBlockData(getMat(viewer));
        PacketUtil.sendPacket(viewer, packet);

        DataWatcher metadata = new DataWatcher();
        metadata.set(EntityHandle.DATA_NO_GRAVITY, true);
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
    public void onStateUpdated(Player viewer) {
        super.onStateUpdated(viewer);
        if (this.getViewers().contains(viewer)) {
            this.makeHiddenFor(viewer);
            this.makeVisibleFor(viewer);
        }
    }

    @Override
    public void updateAppearance() {
        if (this.positionChanged) {
            this.positionChanged = false;
            if (this.entityId != -1) {
                PacketPlayOutEntityTeleportHandle tpPacket = PacketPlayOutEntityTeleportHandle.createNew(
                        this.entityId,
                        this.position.getX(),
                        this.position.getY(),
                        this.position.getZ(),
                        0.0f, 0.0f, false);
                broadcastPacket(tpPacket);
            }
        }
    }

    @Override
    public boolean usesEntityId(int entityId) {
        return this.entityId == entityId;
    }

    private final BlockData getMat(Player viewer) {
        if (getState(viewer) == TrackParticleState.SELECTED) {
            return BlockData.fromMaterial(MaterialUtil.getFirst("GOLD_BLOCK", "LEGACY_GOLD_BLOCK"));
        } else {
            return BlockData.fromMaterial(MaterialUtil.getFirst("GLASS", "LEGACY_GLASS"));
        }
    }
}
