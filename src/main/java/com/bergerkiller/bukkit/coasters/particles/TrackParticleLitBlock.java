package com.bergerkiller.bukkit.coasters.particles;

import java.util.UUID;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;
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
    private IntVector3 block;
    private int entityId = -1;
    private boolean positionChanged = false;

    public TrackParticleLitBlock(TrackParticleWorld world, IntVector3 block) {
        super(world);
        this.block = block;
    }

    public void setBlock(IntVector3 block) {
        if (block != null && !this.block.equals(block)) {
            this.block = block;
            this.positionChanged = true;
        }
    }

    @Override
    public boolean isVisible(Player viewer, Vector viewerPosition) {
        if (getState(viewer) == TrackParticleState.HIDDEN) {
            return false;
        }
        return super.isVisible(viewer, viewerPosition);
    }

    @Override
    public double distanceSquared(Vector viewerPosition) {
        double dx = (viewerPosition.getX() - block.x);
        double dy = (viewerPosition.getY() - block.y);
        double dz = (viewerPosition.getZ() - block.z);
        return dx*dx + dy*dy + dz*dz;
    }

    @Override
    public double getViewDistance() {
        return 16;
    }

    @Override
    public void makeVisibleFor(Player viewer) {
        if (this.entityId == -1) {
            this.entityId = EntityUtil.getUniqueEntityId();
        }

        PacketPlayOutSpawnEntityHandle packet = PacketPlayOutSpawnEntityHandle.T.newHandleNull();
        packet.setEntityId(this.entityId);
        packet.setEntityUUID(UUID.randomUUID());
        packet.setPosX(this.block.x + 0.5);
        packet.setPosY(this.block.y + 0.0);
        packet.setPosZ(this.block.z + 0.5);
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
        makeHiddenFor(viewer);
        if (this.isVisible(viewer, viewer.getEyeLocation().toVector())) {
            makeVisibleFor(viewer);
        }
    }

    @Override
    public void updateAppearance() {
        if (this.positionChanged) {
            this.positionChanged = false;
            if (this.entityId != -1) {
                PacketPlayOutEntityTeleportHandle tpPacket = PacketPlayOutEntityTeleportHandle.createNew(
                        this.entityId,
                        this.block.x + 0.5,
                        this.block.y + 0.0,
                        this.block.z + 0.5,
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
