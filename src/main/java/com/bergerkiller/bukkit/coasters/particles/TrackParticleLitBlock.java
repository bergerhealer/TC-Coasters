package com.bergerkiller.bukkit.coasters.particles;

import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityItemHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutSpawnEntityHandle;

public class TrackParticleLitBlock extends TrackParticle {
    private IntVector3 block;
    private int entityId = -1;

    public TrackParticleLitBlock(TrackParticleWorld world, IntVector3 block) {
        super(world);
        this.block = block;
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
        packet.setEntityTypeId(70);
        packet.setExtraData(getMat(viewer).getCombinedId());
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
    public void updateAppearance() {
        for (Player viewer : this.getViewers()) {
            makeHiddenFor(viewer);
            makeVisibleFor(viewer);
        }
    }

    @Override
    public boolean usesEntityId(int entityId) {
        return this.entityId == entityId;
    }

    private final BlockData getMat(Player viewer) {
        if (getState(viewer) == TrackParticleState.SELECTED) {
            return BlockData.fromMaterial(Material.GOLD_BLOCK);
        } else {
            return BlockData.fromMaterial(Material.GLASS);
        }
    }
}
