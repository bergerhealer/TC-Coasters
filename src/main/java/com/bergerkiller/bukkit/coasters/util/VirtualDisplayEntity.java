package com.bergerkiller.bukkit.coasters.util;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.collections.octree.DoubleOctree;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.common.wrappers.ItemDisplayMode;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityDestroyHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityTeleportHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutMountHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.DisplayHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.decoration.EntityArmorStandHandle;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Helper class for spawning and updating an item or block display entity.
 * Can optionally be mounted on a 'holder' for smooth movement interpolation.
 */
public class VirtualDisplayEntity {
    /**
     * On Minecraft 1.19.4 display entities had their yaw flipped. As such an extra flip was needed
     * to correct for this. No per-player logic is needed as ViaVersion will flip yaw automatically
     * when 1.19.4 clients connect to a 1.20 server.
     */
    private static final float INITIAL_ITEM_YAW = Common.evaluateMCVersion("<=", "1.19.4") ? 180.0f : 0.0f;

    private int holderEntityId;
    private int entityId;
    private double posX, posY, posZ;
    private Quaternion orientation;
    private double clip = Double.NaN;
    private Vector scale;
    private Boolean glowing = null;
    private boolean centerBlock = false;

    // For Item Display entities
    private ItemStack item;
    // For Block Display entities
    private BlockData blockData;

    private VirtualDisplayEntity(int holderEntityId, int entityId) {
        this.holderEntityId = holderEntityId;
        this.entityId = entityId;
    }

    public static VirtualDisplayEntity create(int holderEntityId, int entityId) {
        return new VirtualDisplayEntity(holderEntityId, entityId);
    }

    public VirtualDisplayEntity position(DoubleOctree.Entry<?> position) {
        this.posX = position.getX();
        this.posY = position.getY();
        this.posZ = position.getZ();
        return this;
    }

    public VirtualDisplayEntity position(Vector position) {
        this.posX = position.getX();
        this.posY = position.getY();
        this.posZ = position.getZ();
        return this;
    }

    public VirtualDisplayEntity orientation(Quaternion orientation) {
        this.orientation = orientation;
        return this;
    }

    public VirtualDisplayEntity clip(double clip) {
        this.clip = clip;
        return this;
    }

    public VirtualDisplayEntity scale(Vector scale) {
        this.scale = scale;
        return this;
    }

    public VirtualDisplayEntity glowing(boolean glowing) {
        this.glowing = glowing;
        return this;
    }

    public VirtualDisplayEntity centerBlock() {
        this.centerBlock = true;
        return this;
    }

    public VirtualDisplayEntity item(ItemStack item) {
        this.item = item;
        return this;
    }

    public VirtualDisplayEntity block(BlockData blockData) {
        this.blockData = blockData;
        return this;
    }

    public int entityId() {
        return this.entityId;
    }

    public int holderEntityId() {
        return this.holderEntityId;
    }

    public VirtualDisplayEntity spawnHolder(Iterable<Player> viewers) {
        if (this.holderEntityId == -1) {
            for (Player viewer : viewers) {
                this.spawnHolder(viewer);
            }
        }
        return this;
    }

    public VirtualDisplayEntity updatePosition(Player viewer) {
        if (this.holderEntityId == -1) {
            // Teleport the display entity itself
            PacketPlayOutEntityTeleportHandle tpPacket = PacketPlayOutEntityTeleportHandle.createNew(
                    this.entityId,
                    this.posX,
                    this.posY,
                    this.posZ,
                    0.0f, 0.0f, false);
            PacketUtil.sendPacket(viewer, tpPacket);
        } else {
            // Teleport the armorstand holder
            PacketPlayOutEntityTeleportHandle tpPacket = PacketPlayOutEntityTeleportHandle.createNew(
                    this.holderEntityId,
                    this.posX,
                    this.posY,
                    this.posZ,
                    0.0f, 0.0f, false);
            PacketUtil.sendPacket(viewer, tpPacket);
        }

        return this;
    }

    public VirtualDisplayEntity destroy(Player viewer) {
        if (this.holderEntityId != -1) {
            PacketUtil.sendPacket(viewer, PacketPlayOutEntityDestroyHandle.createNewSingle(this.holderEntityId));
        }
        if (this.entityId != -1) {
            PacketUtil.sendPacket(viewer, PacketPlayOutEntityDestroyHandle.createNewSingle(this.entityId));
        }
        return this;
    }

    public VirtualDisplayEntity destroyHolder(Iterable<Player> viewers) {
        if (this.holderEntityId != -1) {
            PacketPlayOutEntityDestroyHandle packet = PacketPlayOutEntityDestroyHandle.createNewSingle(this.holderEntityId);
            for (Player viewer : viewers) {
                PacketUtil.sendPacket(viewer, packet);
            }
            this.holderEntityId = -1;
        }

        viewers.forEach(this::updatePosition);

        return this;
    }

    private void applyProperties(DataWatcher metadata) {
        if (glowing != null) {
            metadata.set(EntityHandle.DATA_FLAGS, (byte) (glowing ? EntityHandle.DATA_FLAG_GLOWING : 0));
        }
        if (!Double.isNaN(clip)) {
            Float f;
            if (clip == 0) {
                f = 0.0f;
            } else {
                f = (float) (this.clip * com.bergerkiller.bukkit.tc.attachments.VirtualDisplayEntity.BBOX_FACT * Util.absMaxAxis(scale));
            }
            metadata.set(DisplayHandle.DATA_WIDTH, f);
            metadata.set(DisplayHandle.DATA_HEIGHT, f);
        }
        if (item != null) {
            metadata.set(DisplayHandle.ItemDisplayHandle.DATA_ITEM_STACK, item);
            metadata.set(DisplayHandle.ItemDisplayHandle.DATA_ITEM_DISPLAY_MODE, ItemDisplayMode.HEAD);
        } else if (blockData != null) {
            metadata.set(DisplayHandle.BlockDisplayHandle.DATA_BLOCK_STATE, blockData);
        }
        if (scale != null && orientation != null) {
            // Add transform information and signal flag to start interpolation
            metadata.set(DisplayHandle.DATA_SCALE, this.scale);
            metadata.set(DisplayHandle.DATA_LEFT_ROTATION, this.orientation);
            metadata.set(DisplayHandle.DATA_INTERPOLATION_START_DELTA_TICKS, 0);
            // For blocks we got to center it
            if (centerBlock) {
                Vector s = scale;
                Vector v = new Vector(-0.5, 0.0, -0.5);
                v.setX(v.getX() * s.getX());
                v.setY(v.getY() * s.getY());
                v.setZ(v.getZ() * s.getZ());
                this.orientation.transformPoint(v);
                metadata.set(DisplayHandle.DATA_TRANSLATION, v);
            }
        }
    }

    public VirtualDisplayEntity updateMetadata(Iterable<Player> viewers) {
        if (this.entityId != -1) {
            DataWatcher metadata = new DataWatcher();
            applyProperties(metadata);
            PacketPlayOutEntityMetadataHandle packet = PacketPlayOutEntityMetadataHandle.createNew(this.entityId, metadata, true);
            for (Player viewer : viewers) {
                PacketUtil.sendPacket(viewer, packet);
            }
        }
        return this;
    }

    public VirtualDisplayEntity updateMetadata(Player viewer) {
        if (this.entityId != -1) {
            DataWatcher metadata = new DataWatcher();
            applyProperties(metadata);
            PacketUtil.sendPacket(viewer, PacketPlayOutEntityMetadataHandle.createNew(this.entityId, metadata, true));
        }
        return this;
    }

    public VirtualDisplayEntity spawn(Player viewer) {
        PacketPlayOutSpawnEntityHandle spawnPacket = PacketPlayOutSpawnEntityHandle.createNew();

        DataWatcher metadata = new DataWatcher();
        metadata.set(DisplayHandle.DATA_INTERPOLATION_DURATION, 3);
        metadata.setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_GLOWING, this.glowing);
        applyProperties(metadata);
        if (item != null) {
            spawnPacket.setEntityType(com.bergerkiller.bukkit.tc.attachments.VirtualDisplayEntity.ITEM_DISPLAY_ENTITY_TYPE);
        } else if (blockData != null) {
            spawnPacket.setEntityType(com.bergerkiller.bukkit.tc.attachments.VirtualDisplayEntity.BLOCK_DISPLAY_ENTITY_TYPE);
        } else {
            return this; // Wtf
        }

        if (this.entityId == -1) {
            this.entityId = EntityUtil.getUniqueEntityId();
        }

        spawnPacket.setEntityId(this.entityId);
        spawnPacket.setEntityUUID(UUID.randomUUID());
        spawnPacket.setPosX(this.posX);
        spawnPacket.setPosY(this.posY);
        spawnPacket.setPosZ(this.posZ);
        spawnPacket.setYaw((item == null) ? 0.0f : INITIAL_ITEM_YAW);
        spawnPacket.setPitch(0.0f);
        PacketUtil.sendPacket(viewer, spawnPacket);
        PacketUtil.sendPacket(viewer, PacketPlayOutEntityMetadataHandle.createNew(this.entityId, metadata, true));

        // Put in a mount when we've allocated a holder entity ID
        // Not doing so can cause the object to not move when viewers respawn
        if (this.holderEntityId != -1) {
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
        holderPacket.setPosY(this.posY);
        holderPacket.setPosZ(this.posZ);
        holderPacket.setEntityType(EntityType.ARMOR_STAND);
        holder_meta.set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE | EntityHandle.DATA_FLAG_FLYING));
        holder_meta.set(EntityHandle.DATA_SILENT, true);
        holder_meta.set(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, (byte) (EntityArmorStandHandle.DATA_FLAG_NO_BASEPLATE | EntityArmorStandHandle.DATA_FLAG_SET_MARKER));
        holder_meta.set(EntityLivingHandle.DATA_NO_GRAVITY, true);
        PacketUtil.sendEntityLivingSpawnPacket(viewer, holderPacket, holder_meta);
        PacketUtil.sendPacket(viewer, PacketPlayOutMountHandle.createNew(this.holderEntityId, new int[] {this.entityId}));
    }
}
