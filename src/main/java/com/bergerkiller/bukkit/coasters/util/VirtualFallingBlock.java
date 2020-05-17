package com.bergerkiller.bukkit.coasters.util;

import java.util.UUID;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.collections.octree.DoubleOctree;
import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.generated.net.minecraft.server.EntityBatHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityInsentientHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutAttachEntityHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityTeleportHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutMountHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutSpawnEntityHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutSpawnEntityLivingHandle;

/**
 * Helper class for spawning and updating a virtual falling block.
 * This is a falling block entity, and on older Minecraft versions is supported
 * by another invisible entity to disable gravity. The block material data
 * can be set.
 */
public class VirtualFallingBlock {
    private static final double BAT_HOLDER_OFFSET = 0.675;
    private static final double FALLING_BLOCK_OFFSET = 0.01;
    private static final boolean CAN_DISABLE_GRAVITY = Common.evaluateMCVersion(">=", "1.10.2");
    private static final boolean CAN_GLOW = Common.evaluateMCVersion(">=", "1.9");
    private static final boolean CAN_FIRE_LIT = Common.evaluateMCVersion(">", "1.8");
    private int holderEntityId;
    private int entityId;
    private double posX, posY, posZ;
    private BlockData material;
    private boolean glowing = false;
    private boolean smooth = false;
    private boolean respawn = false;

    private VirtualFallingBlock(int holderEntityId, int entityId) {
        this.holderEntityId = holderEntityId;
        this.entityId = entityId;
    }

    public static VirtualFallingBlock create(int holderEntityId, int entityId) {
        return new VirtualFallingBlock(holderEntityId, entityId);
    }

    public int entityId() {
        return this.entityId;
    }

    public int holderEntityId() {
        return this.holderEntityId;
    }

    public VirtualFallingBlock material(BlockData material) {
        this.material = material;
        return this;
    }

    public VirtualFallingBlock position(DoubleOctree.Entry<?> position) {
        this.posX = position.getX();
        this.posY = position.getY();
        this.posZ = position.getZ();
        return this;
    }

    public VirtualFallingBlock position(Vector position) {
        this.posX = position.getX();
        this.posY = position.getY();
        this.posZ = position.getZ();
        return this;
    }

    public VirtualFallingBlock glowing(boolean glowing) {
        this.glowing = glowing;
        return this;
    }

    public VirtualFallingBlock smoothMovement(boolean smooth) {
        this.smooth = smooth;
        return this;
    }

    public VirtualFallingBlock respawn(boolean respawn) {
        this.respawn = respawn;
        return this;
    }

    public VirtualFallingBlock updatePosition(Iterable<Player> viewers) {
        if (this.entityId != -1 && this.smooth && this.holderEntityId == -1) {
            for (Player viewer : viewers) {
                this.spawnHolder(viewer);
            }
        } else if (this.holderEntityId != -1) {
            if (this.respawn) {
                for (Player viewer : viewers) {
                    PacketUtil.sendPacket(viewer, PacketType.OUT_ENTITY_DESTROY.newInstance(this.holderEntityId));
                    this.spawnHolder(viewer);
                }
            } else {
                PacketPlayOutEntityTeleportHandle tpPacket = PacketPlayOutEntityTeleportHandle.createNew(
                        this.holderEntityId,
                        this.posX,
                        this.posY - BAT_HOLDER_OFFSET,
                        this.posZ,
                        0.0f, 0.0f, false);
                for (Player viewer : viewers) {
                    PacketUtil.sendPacket(viewer, tpPacket);
                }
            }
        } else if (this.entityId != -1) {
            PacketPlayOutEntityTeleportHandle tpPacket = PacketPlayOutEntityTeleportHandle.createNew(
                    this.entityId,
                    this.posX, this.posY, this.posZ,
                    0.0f, 0.0f, false);
            for (Player viewer : viewers) {
                PacketUtil.sendPacket(viewer, tpPacket);
            }
        }
        return this;
    }

    public VirtualFallingBlock updateMetadata(Player viewer) {
        if (this.entityId != -1 && CAN_GLOW) {
            DataWatcher metadata = new DataWatcher();
            metadata.set(EntityHandle.DATA_FLAGS, (byte) 0);
            metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_GLOWING, this.glowing);
            metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_ON_FIRE, this.glowing && CAN_FIRE_LIT);
            PacketUtil.sendPacket(viewer, PacketPlayOutEntityMetadataHandle.createNew(this.entityId, metadata, true));
        }
        return this;
    }

    public VirtualFallingBlock destroy(Player viewer) {
        if (this.holderEntityId != -1 && this.entityId != -1) {
            PacketUtil.sendPacket(viewer, PacketType.OUT_ENTITY_DESTROY.newInstance(this.holderEntityId, this.entityId));
        } else if (this.entityId != -1) {
            PacketUtil.sendPacket(viewer, PacketType.OUT_ENTITY_DESTROY.newInstance(this.entityId));
        }
        return this;
    }

    public VirtualFallingBlock destroyHolder(Iterable<Player> viewers) {
        if (this.holderEntityId != -1 && CAN_DISABLE_GRAVITY) {
            CommonPacket packet = PacketType.OUT_ENTITY_DESTROY.newInstance(this.holderEntityId);
            for (Player viewer : viewers) {
                PacketUtil.sendPacket(viewer, packet);
            }
            this.holderEntityId = -1;
        }
        return this;
    }

    public VirtualFallingBlock spawn(Player viewer) {
        if (this.entityId == -1) {
            this.entityId = EntityUtil.getUniqueEntityId();
        }
        if (CAN_DISABLE_GRAVITY) {
            this.holderEntityId = -1;
        } else if (this.holderEntityId == -1) {
            this.holderEntityId = EntityUtil.getUniqueEntityId();
        }

        PacketPlayOutSpawnEntityHandle packet = PacketPlayOutSpawnEntityHandle.T.newHandleNull();
        packet.setEntityId(this.entityId);
        packet.setEntityUUID(UUID.randomUUID());
        packet.setPosX(this.posX);
        packet.setPosY(this.posY - FALLING_BLOCK_OFFSET);
        packet.setPosZ(this.posZ);
        packet.setEntityType(EntityType.FALLING_BLOCK);
        packet.setFallingBlockData(this.material);
        PacketUtil.sendPacket(viewer, packet);

        if (CAN_DISABLE_GRAVITY && this.holderEntityId == -1) {
            DataWatcher metadata = new DataWatcher();
            metadata.set(EntityHandle.DATA_NO_GRAVITY, true);
            if (this.glowing && CAN_GLOW) {
                metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_GLOWING, true);
                metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_ON_FIRE, CAN_FIRE_LIT);
            }
            PacketUtil.sendPacket(viewer, PacketPlayOutEntityMetadataHandle.createNew(this.entityId, metadata, true));
        } else {
            // If glowing, send glowing metadata as well
            if (this.glowing && CAN_GLOW) {
                DataWatcher metadata = new DataWatcher();
                metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_GLOWING, true);
                metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_ON_FIRE, CAN_FIRE_LIT);
                PacketUtil.sendPacket(viewer, PacketPlayOutEntityMetadataHandle.createNew(this.entityId, metadata, true));
            }

            // Spawn and mount into a holder entity
            this.spawnHolder(viewer);
        }

        return this;
    }

    private void spawnHolder(Player viewer) {
        if (this.holderEntityId == -1) {
            this.holderEntityId = EntityUtil.getUniqueEntityId();
        }

        // Spawn an invisible bat and attach it to that
        // Also used on newer mc versions when smooth movement is required
        // Smooth movement is desirable when animations are going to be played
        PacketPlayOutSpawnEntityLivingHandle holderPacket = PacketPlayOutSpawnEntityLivingHandle.createNew();
        DataWatcher holder_meta = new DataWatcher();
        holderPacket.setEntityId(this.holderEntityId);
        holderPacket.setEntityUUID(UUID.randomUUID());
        holderPacket.setPosX(this.posX);
        holderPacket.setPosY(this.posY - BAT_HOLDER_OFFSET);
        holderPacket.setPosZ(this.posZ);
        holderPacket.setEntityType(EntityType.BAT);
        holder_meta.set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE | EntityHandle.DATA_FLAG_FLYING));
        holder_meta.set(EntityHandle.DATA_SILENT, true);
        holder_meta.set(EntityInsentientHandle.DATA_INSENTIENT_FLAGS, (byte) EntityInsentientHandle.DATA_INSENTIENT_FLAG_NOAI);
        holder_meta.set(EntityLivingHandle.DATA_NO_GRAVITY, true);
        holder_meta.set(EntityBatHandle.DATA_BAT_FLAGS, (byte) 0);
        PacketUtil.sendEntityLivingSpawnPacket(viewer, holderPacket, holder_meta);

        if (PacketPlayOutMountHandle.T.isAvailable()) {
            PacketUtil.sendPacket(viewer, PacketPlayOutMountHandle.createNew(this.holderEntityId, new int[] {this.entityId}));
        } else {
            PacketUtil.sendPacket(viewer, PacketPlayOutAttachEntityHandle.createNewMount(this.entityId, this.holderEntityId));
        }
    }
}
