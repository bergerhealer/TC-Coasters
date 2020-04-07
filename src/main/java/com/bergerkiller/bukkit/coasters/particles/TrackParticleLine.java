package com.bergerkiller.bukkit.coasters.particles;

import java.util.UUID;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.collections.octree.DoubleOctree;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.generated.net.minecraft.server.EntityBatHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityInsentientHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutAttachEntityHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityTeleportHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutSpawnEntityLivingHandle;

/**
 * A particle consisting of a line created using a leash between two invisible entities
 */
public class TrackParticleLine extends TrackParticle {
    private static final Vector OFFSET1 = new Vector(0.7, 0.16, -0.5);
    private static final Vector OFFSET2 = new Vector(0.0, -1.1, -0.2);
    private DoubleOctree.Entry<TrackParticle> p1, p2;
    private int e1 = -1, e2 = -1;
    private boolean positionChanged = false;
    private boolean needsRespawn = false;

    public TrackParticleLine(Vector p1, Vector p2) {
        this.setPositions(p1, p2);
    }

    @Override
    protected void onAdded() {
        addPosition(this.p1);
        addPosition(this.p2);
    }

    @Override
    protected void onRemoved() {
        removePosition(this.p1);
        removePosition(this.p2);
    }

    public void setPositions(Vector p1, Vector p2) {
        // Swap p1 and p2 sometimes, as it reduces hanging ellipsis effects
        if (p1.getY() > p2.getY()) {
            Vector c = p1;
            p1 = p2;
            p2 = c;
        }

        if (this.p1 == null || this.p2 == null) {
            // First time updating - set initial positions
            this.p1 = DoubleOctree.Entry.create(p1, this);
            this.p2 = DoubleOctree.Entry.create(p2, this);
        } else if (!this.p1.equalsCoord(p1) || !this.p2.equalsCoord(p2)) {
            // When p1 and p2 swap around, respawn everything to prevent visual glitches
            this.needsRespawn = this.p1.distanceSquared(p1) > this.p2.distanceSquared(p1) &&
                                this.p2.distanceSquared(p2) > this.p1.distanceSquared(p2);

            // Mark position changed and update points
            this.positionChanged = true;
            this.p1 = updatePosition(this.p1, p1);
            this.p2 = updatePosition(this.p2, p2);
            this.scheduleUpdateAppearance();
        }
    }

    @Override
    public double distanceSquared(Vector viewerPosition) {
        return Math.min(this.p1.distanceSquared(viewerPosition),
                        this.p2.distanceSquared(viewerPosition));
    }

    @Override
    public void updateAppearance() {
        if (this.needsRespawn) {
            this.needsRespawn = false;
            this.positionChanged = false;
            for (Player player : this.getViewers()) {
                makeHiddenFor(player);
                makeVisibleFor(player);
            }
        }
        if (this.positionChanged) {
            this.positionChanged = false;

            if (this.e1 != -1) {
                PacketPlayOutEntityTeleportHandle tpPacket = PacketPlayOutEntityTeleportHandle.createNew(
                        this.e1,
                        this.p1.getX() + OFFSET1.getX(),
                        this.p1.getY() + OFFSET1.getY(),
                        this.p1.getZ() + OFFSET1.getZ(),
                        0.0f, 0.0f, false);
                broadcastPacket(tpPacket);
            }
            if (this.e2 != -1) {
                PacketPlayOutEntityTeleportHandle tpPacket = PacketPlayOutEntityTeleportHandle.createNew(
                        this.e2,
                        this.p2.getX() + OFFSET2.getX(),
                        this.p2.getY() + OFFSET2.getY(),
                        this.p2.getZ() + OFFSET2.getZ(),
                        0.0f, 0.0f, false);
                broadcastPacket(tpPacket);
            }
        }
    }

    @Override
    public void makeHiddenFor(Player viewer) {
        if (this.e1 != -1 && this.e2 != -1) {
            PacketUtil.sendPacket(viewer, PacketType.OUT_ENTITY_DESTROY.newInstance(this.e1, this.e2));
        }
    }

    @Override
    public void makeVisibleFor(Player viewer) {
        if (this.e1 == -1 || this.e2 == -1) {
            this.e1 = EntityUtil.getUniqueEntityId();
            this.e2 = EntityUtil.getUniqueEntityId();
        }

        PacketPlayOutSpawnEntityLivingHandle p1 = PacketPlayOutSpawnEntityLivingHandle.createNew();
        DataWatcher p1_meta = new DataWatcher();
        p1.setEntityId(this.e1);
        p1.setEntityUUID(UUID.randomUUID());
        p1.setPosX(this.p1.getX() + OFFSET1.getX());
        p1.setPosY(this.p1.getY() + OFFSET1.getY());
        p1.setPosZ(this.p1.getZ() + OFFSET1.getZ());
        p1.setEntityType(EntityType.BAT);
        p1_meta.set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE | EntityHandle.DATA_FLAG_FLYING));
        p1_meta.set(EntityHandle.DATA_SILENT, true);
        p1_meta.set(EntityInsentientHandle.DATA_INSENTIENT_FLAGS, (byte) EntityInsentientHandle.DATA_INSENTIENT_FLAG_NOAI);
        p1_meta.set(EntityLivingHandle.DATA_NO_GRAVITY, true);
        p1_meta.set(EntityBatHandle.DATA_BAT_FLAGS, (byte) 0);

        PacketPlayOutSpawnEntityLivingHandle p2 = PacketPlayOutSpawnEntityLivingHandle.createNew();
        DataWatcher p2_meta = new DataWatcher();
        p2.setEntityId(this.e2);
        p2.setEntityUUID(UUID.randomUUID());
        p2.setPosX(this.p2.getX() + OFFSET2.getX());
        p2.setPosY(this.p2.getY() + OFFSET2.getY());
        p2.setPosZ(this.p2.getZ() + OFFSET2.getZ());
        p2.setEntityType(EntityType.BAT);
        p2_meta.set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE | EntityHandle.DATA_FLAG_FLYING));
        p2_meta.set(EntityHandle.DATA_SILENT, true);
        p2_meta.set(EntityInsentientHandle.DATA_INSENTIENT_FLAGS, (byte) EntityInsentientHandle.DATA_INSENTIENT_FLAG_NOAI);
        p2_meta.set(EntityLivingHandle.DATA_NO_GRAVITY, true);
        p2_meta.set(EntityBatHandle.DATA_BAT_FLAGS, (byte) 0);

        PacketUtil.sendEntityLivingSpawnPacket(viewer, p1, p1_meta);
        PacketUtil.sendEntityLivingSpawnPacket(viewer, p2, p2_meta);

        PacketPlayOutAttachEntityHandle ap1 = PacketPlayOutAttachEntityHandle.T.newHandleNull();
        ap1.setVehicleId(this.e1);
        ap1.setPassengerId(this.e2);

        // Fix for <= 1.8.8
        if (PacketPlayOutAttachEntityHandle.T.leashId.isAvailable()) {
            PacketPlayOutAttachEntityHandle.T.leashId.set(ap1.getRaw(), 1);
        }

        PacketUtil.sendPacket(viewer, ap1);
    }

    @Override
    public boolean usesEntityId(int entityId) {
        return this.e1 == entityId || this.e2 == entityId;
    }
}
