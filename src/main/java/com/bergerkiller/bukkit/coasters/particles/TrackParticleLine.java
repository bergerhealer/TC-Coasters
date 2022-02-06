package com.bergerkiller.bukkit.coasters.particles;

import java.util.UUID;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.collections.octree.DoubleOctree;
import com.bergerkiller.bukkit.common.utils.DebugUtil;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutAttachEntityHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityDestroyHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityEquipmentHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityTeleportHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityInsentientHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.ambient.EntityBatHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.decoration.EntityArmorStandHandle;

/**
 * A particle consisting of a line created using a leash between two invisible entities
 */
public class TrackParticleLine extends TrackParticle {
    protected static final int FLAG_POSITION_CHANGED  = (1<<2);
    protected static final int FLAG_NEEDS_RESPAWN     = (1<<3);

    private static final LineOffsets OFFSETS_1_8_TO_1_15_2 = new LineOffsets(0.7, 0.16, -0.5,
                                                                             0.0, -1.1, -0.2,
                                                                             false);
    private static final LineOffsets OFFSETS_1_16_TO_1_16_1 = new LineOffsets(0.7, -0.065, -0.5,
                                                                              0.0, -0.45, -0.2,
                                                                              false);
    private static final LineOffsets OFFSETS_1_16_2 = new LineOffsets(0.0, -0.31, 0.0,
                                                                      0.0, -0.45, -0.2,
                                                                      false);
    private static final LineOffsets OFFSETS_1_17 = new LineOffsets(0.0, -0.31, 0.0,
                                                                    0.0, -0.45, -0.2,
                                                                    true);

    // Configuration of the ArmorStand spawned on 1.17 and later to fix the leash glitch
    private static final Vector UNGLITCH_AS_OFFSETS = new Vector(0.0, -1.0, -0.2);
    private static final Vector UNGLITCH_AS_POSE = new Vector(-22.0, -55.0, -5.0);
    private static final float UNGITCH_AS_YAW = 20.0f;
    private static final ItemStack UNGLITCH_AS_ITEM = ItemUtil.createItem(new ItemStack(MaterialUtil.getFirst("OAK_BUTTON", "LEGACY_WOOD_BUTTON")));

    private DoubleOctree.Entry<TrackParticle> p1, p2;
    private int e1 = -1, e2 = -1, e3 = -1;

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

    private LineOffsets getOffsets(Player player) {
        if (PlayerUtil.evaluateGameVersion(player, ">=", "1.17")) {
            return OFFSETS_1_17;
        } else if (PlayerUtil.evaluateGameVersion(player, ">=", "1.16.2")) {
            return OFFSETS_1_16_2;
        } else if (PlayerUtil.evaluateGameVersion(player, ">=", "1.16")) {
            return OFFSETS_1_16_TO_1_16_1;
        } else {
            return OFFSETS_1_8_TO_1_15_2;
        }
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
            this.setFlag(FLAG_NEEDS_RESPAWN,
                    this.p1.distanceSquared(p1) > this.p2.distanceSquared(p1) &&
                    this.p2.distanceSquared(p2) > this.p1.distanceSquared(p2));

            // Mark position changed and update points
            this.setFlag(FLAG_POSITION_CHANGED);
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
        if (this.clearFlag(FLAG_NEEDS_RESPAWN)) {
            this.clearFlag(FLAG_POSITION_CHANGED);
            for (Player player : this.getViewers()) {
                makeHiddenFor(player);
                makeVisibleFor(player);
            }
        }
        if (this.clearFlag(FLAG_POSITION_CHANGED)) {
            for (Player viewer : this.getViewers()) {
                LineOffsets offsets = getOffsets(viewer);
                if (this.e1 != -1) {
                    PacketPlayOutEntityTeleportHandle tpPacket = PacketPlayOutEntityTeleportHandle.createNew(
                            this.e1,
                            this.p1.getX() + offsets.p1x,
                            this.p1.getY() + offsets.p1y,
                            this.p1.getZ() + offsets.p1z,
                            0.0f, 0.0f, false);
                    PacketUtil.sendPacket(viewer, tpPacket);
                }
                if (this.e2 != -1) {
                    PacketPlayOutEntityTeleportHandle tpPacket = PacketPlayOutEntityTeleportHandle.createNew(
                            this.e2,
                            this.p2.getX() + offsets.p2x,
                            this.p2.getY() + offsets.p2y,
                            this.p2.getZ() + offsets.p2z,
                            0.0f, 0.0f, false);
                    PacketUtil.sendPacket(viewer, tpPacket);
                }

                boolean fixLeashGlitch = offsets.fixLeashGlitch(this.world);
                if (fixLeashGlitch && this.e3 != -1) {
                    PacketPlayOutEntityTeleportHandle tpPacket = PacketPlayOutEntityTeleportHandle.createNew(
                            this.e3,
                            this.p2.getX() + UNGLITCH_AS_OFFSETS.getX(),
                            this.p2.getY() + UNGLITCH_AS_OFFSETS.getY(),
                            this.p2.getZ() + UNGLITCH_AS_OFFSETS.getZ(),
                            UNGITCH_AS_YAW, 0.0f, false);
                    PacketUtil.sendPacket(viewer, tpPacket);
                }
            }
        }
    }

    @Override
    public void makeHiddenFor(Player viewer) {
        if (this.e1 != -1) {
            PacketUtil.sendPacket(viewer, PacketPlayOutEntityDestroyHandle.createNewSingle(this.e1));
        }
        if (this.e2 != -1) {
            PacketUtil.sendPacket(viewer, PacketPlayOutEntityDestroyHandle.createNewSingle(this.e2));
        }
        if (this.e3 != -1) {
            PacketUtil.sendPacket(viewer, PacketPlayOutEntityDestroyHandle.createNewSingle(this.e3));
        }
    }

    @Override
    public void makeVisibleFor(Player viewer) {
        LineOffsets offsets = getOffsets(viewer);
        boolean fixLeashGlitch = offsets.fixLeashGlitch(this.world);

        if (this.e1 == -1 || this.e2 == -1) {
            this.e1 = EntityUtil.getUniqueEntityId();
            this.e2 = EntityUtil.getUniqueEntityId();
        }
        if (fixLeashGlitch && this.e3 == -1) {
            this.e3 = EntityUtil.getUniqueEntityId();
        }

        PacketPlayOutSpawnEntityLivingHandle p1 = PacketPlayOutSpawnEntityLivingHandle.createNew();
        DataWatcher p1_meta = new DataWatcher();
        p1.setEntityId(this.e1);
        p1.setEntityUUID(UUID.randomUUID());
        p1.setPosX(this.p1.getX() + offsets.p1x);
        p1.setPosY(this.p1.getY() + offsets.p1y);
        p1.setPosZ(this.p1.getZ() + offsets.p1z);
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
        p2.setPosX(this.p2.getX() + offsets.p2x);
        p2.setPosY(this.p2.getY() + offsets.p2y);
        p2.setPosZ(this.p2.getZ() + offsets.p2z);
        p2.setEntityType(EntityType.BAT);
        p2_meta.set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE | EntityHandle.DATA_FLAG_FLYING));
        p2_meta.set(EntityHandle.DATA_SILENT, true);
        p2_meta.set(EntityInsentientHandle.DATA_INSENTIENT_FLAGS, (byte) EntityInsentientHandle.DATA_INSENTIENT_FLAG_NOAI);
        p2_meta.set(EntityLivingHandle.DATA_NO_GRAVITY, true);
        p2_meta.set(EntityBatHandle.DATA_BAT_FLAGS, (byte) 0);

        if (fixLeashGlitch) {
            PacketPlayOutSpawnEntityLivingHandle p3 = PacketPlayOutSpawnEntityLivingHandle.createNew();
            DataWatcher p3_meta = new DataWatcher();
            p3.setEntityId(this.e3);
            p3.setEntityUUID(UUID.randomUUID());
            p3.setPosX(this.p2.getX() + UNGLITCH_AS_OFFSETS.getX());
            p3.setPosY(this.p2.getY() + UNGLITCH_AS_OFFSETS.getY());
            p3.setPosZ(this.p2.getZ() + UNGLITCH_AS_OFFSETS.getZ());
            p3.setYaw(UNGITCH_AS_YAW);
            p3.setEntityType(EntityType.ARMOR_STAND);
            p3_meta.set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE | EntityHandle.DATA_FLAG_FLYING));
            p3_meta.set(EntityHandle.DATA_SILENT, true);
            p3_meta.set(EntityLivingHandle.DATA_NO_GRAVITY, true);
            p3_meta.set(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, (byte) (EntityArmorStandHandle.DATA_FLAG_NO_BASEPLATE));
            p3_meta.set(EntityArmorStandHandle.DATA_POSE_ARM_RIGHT, UNGLITCH_AS_POSE);
            PacketUtil.sendEntityLivingSpawnPacket(viewer, p3, p3_meta);
            PacketUtil.sendPacket(viewer, PacketPlayOutEntityEquipmentHandle.createNew(this.e3, EquipmentSlot.HAND, UNGLITCH_AS_ITEM));
        }

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
        return this.e1 == entityId || this.e2 == entityId || this.e3 == entityId;
    }

    private static final class LineOffsets {
        public final double p1x, p1y, p1z;
        public final double p2x, p2y, p2z;
        private final boolean hasLeashGlitch;

        public LineOffsets(double p1x, double p1y, double p1z, double p2x, double p2y, double p2z, boolean hasLeashGlitch) {
            this.p1x = p1x;
            this.p1y = p1y;
            this.p1z = p1z;
            this.p2x = p2x;
            this.p2y = p2y;
            this.p2z = p2z;
            this.hasLeashGlitch = hasLeashGlitch;
        }

        public boolean fixLeashGlitch(TrackParticleWorld world) {
            return this.hasLeashGlitch && world.getPlugin().isLeashGlitchFixEnabled();
        }

        @SuppressWarnings("unused")
        public LineOffsets debug() {
            return new LineOffsets(DebugUtil.getDoubleValue("p1x", this.p1x),
                                   DebugUtil.getDoubleValue("p1y", this.p1y),
                                   DebugUtil.getDoubleValue("p1z", this.p1z),
                                   DebugUtil.getDoubleValue("p2x", this.p2x),
                                   DebugUtil.getDoubleValue("p2y", this.p2y),
                                   DebugUtil.getDoubleValue("p2z", this.p2z),
                                   true);
        }
    }
}
