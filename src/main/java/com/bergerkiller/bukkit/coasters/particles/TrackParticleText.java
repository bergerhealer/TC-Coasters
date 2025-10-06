package com.bergerkiller.bukkit.coasters.particles;

import java.util.EnumMap;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.collections.octree.DoubleOctree;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.common.wrappers.ChatText;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityDestroyHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityTeleportHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityVelocityHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.item.EntityItemHandle;

/**
 * A particle consisting of a floating text balloon that always faces the viewer, with
 * a colored cube (item) denoting the exact position. This is used for indicating
 * junction names on the track.
 */
public class TrackParticleText extends TrackParticle {
    protected static final int FLAG_POSITION_CHANGED    = (1<<2);
    protected static final int FLAG_TEXT_CHANGED        = (1<<3);
    protected static final int FLAG_TEXT_COLOR_CHANGED  = (1<<4);
    protected static final int FLAG_SHOW_ITEM           = (1<<5);
    private static final Vector ITEM_OFFSET = new Vector(0.0, -0.2, 0.0);
    private static final Vector ARMORSTAND_OFFSET = new Vector(0.0, -2.5, 0.0);
    private int entityId = -1;
    private ChatColor textColor = ChatColor.BLACK;
    private String text;
    private DoubleOctree.Entry<TrackParticle> position;

    protected TrackParticleText(Vector position, String text, boolean showItem) {
        this.position = DoubleOctree.Entry.create(position, this);
        this.text = text;
        this.textColor = getTextColor(this.text);
        this.setFlag(FLAG_SHOW_ITEM, showItem);
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
        if (!this.position.equalsCoord(position)) {
            this.position = updatePosition(this.position, position);
            this.setFlag(FLAG_POSITION_CHANGED);
            this.scheduleUpdateAppearance();
        }
    }

    public void setText(String text) {
        if (!this.text.equals(text)) {
            this.text = text;
            this.setFlag(FLAG_TEXT_CHANGED);
            ChatColor color = getTextColor(text);
            if (this.textColor != color) {
                this.textColor = color;
                this.setFlag(FLAG_TEXT_COLOR_CHANGED);
            }
            this.scheduleUpdateAppearance();
        }
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

        DataWatcher metadata = new DataWatcher();
        metadata.set(EntityHandle.DATA_NO_GRAVITY, true);
        metadata.set(EntityHandle.DATA_CUSTOM_NAME_VISIBLE, true);
        metadata.set(EntityHandle.DATA_CUSTOM_NAME, ChatText.fromMessage(this.text));

        if (this.getFlag(FLAG_SHOW_ITEM)) {
            // Spawns a hovering item entity
            PacketPlayOutSpawnEntityHandle spawnPacket = PacketPlayOutSpawnEntityHandle.createNew();
            spawnPacket.setEntityId(this.entityId);
            spawnPacket.setEntityUUID(UUID.randomUUID());
            spawnPacket.setEntityType(EntityType.DROPPED_ITEM);
            spawnPacket.setPosX(this.position.getX() + ITEM_OFFSET.getX());
            spawnPacket.setPosY(this.position.getY() + ITEM_OFFSET.getY());
            spawnPacket.setPosZ(this.position.getZ() + ITEM_OFFSET.getZ());
            spawnPacket.setMotX(0.0);
            spawnPacket.setMotY(0.0);
            spawnPacket.setMotZ(0.0);
            spawnPacket.setPitch(0.0f);
            spawnPacket.setYaw(0.0f);
            PacketUtil.sendPacket(viewer, spawnPacket);

            // Send velocity 0 0 0 packet otherwise items fly away on 1.14+
            PacketUtil.sendPacket(viewer, PacketPlayOutEntityVelocityHandle.createNew(this.entityId, 0.0, 0.0, 0.0));

            metadata.set(EntityItemHandle.DATA_ITEM, getItem(this.textColor));
            PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(this.entityId, metadata, true);
            PacketUtil.sendPacket(viewer, metaPacket);
        } else {
            // Armorstand itself should be invisible
            metadata.set(EntityHandle.DATA_FLAGS, (byte) EntityHandle.DATA_FLAG_INVISIBLE);

            // Spawns an invisible armorstand, which displays only the nametag
            PacketPlayOutSpawnEntityLivingHandle spawnPacket = PacketPlayOutSpawnEntityLivingHandle.createNew();
            spawnPacket.setEntityId(this.entityId);
            spawnPacket.setEntityUUID(UUID.randomUUID());
            spawnPacket.setEntityType(EntityType.ARMOR_STAND);
            spawnPacket.setPosX(this.position.getX() + ARMORSTAND_OFFSET.getX());
            spawnPacket.setPosY(this.position.getY() + ARMORSTAND_OFFSET.getY());
            spawnPacket.setPosZ(this.position.getZ() + ARMORSTAND_OFFSET.getZ());
            spawnPacket.setMotX(0.0);
            spawnPacket.setMotY(0.0);
            spawnPacket.setMotZ(0.0);
            spawnPacket.setPitch(0.0f);
            spawnPacket.setYaw(0.0f);
            PacketUtil.sendEntityLivingSpawnPacket(viewer, spawnPacket, metadata);
        }
    }

    @Override
    public void makeHiddenFor(Player viewer) {
        if (this.entityId != -1) {
            PacketUtil.sendPacket(viewer, PacketPlayOutEntityDestroyHandle.createNewSingle(this.entityId));
        }
    }

    @Override
    public void updateAppearance() {
        if (this.clearFlag(FLAG_POSITION_CHANGED) && this.entityId != -1) {
            PacketPlayOutEntityTeleportHandle tpPacket = PacketPlayOutEntityTeleportHandle.createNew(
                    this.entityId,
                    this.position.getX() + ITEM_OFFSET.getX(),
                    this.position.getY() + ITEM_OFFSET.getY(),
                    this.position.getZ() + ITEM_OFFSET.getZ(),
                    0.0f, 0.0f, false);
            broadcastPacket(tpPacket);
        }
        if (this.clearFlag(FLAG_TEXT_CHANGED) && this.entityId != -1) {
            DataWatcher metadata = new DataWatcher();
            metadata.set(EntityItemHandle.DATA_CUSTOM_NAME, ChatText.fromMessage(this.text));
            if (this.getFlag(FLAG_SHOW_ITEM) && this.clearFlag(FLAG_TEXT_COLOR_CHANGED)) {
                metadata.set(EntityItemHandle.DATA_ITEM, getItem(this.textColor));
            }
            PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(this.entityId, metadata, true);
            broadcastPacket(metaPacket);
        }
    }

    @Override
    public boolean usesEntityId(int entityId) {
        return this.entityId == entityId;
    }

    /**
     * Gets a dynamic chat color using an index and prepends it to the text
     * 
     * @param index
     * @param text
     * @return colored text by ordinal
     */
    public static String getOrdinalText(int index, String text) {
        return color_wheel_values[index % color_wheel_values.length].toString() + text;
    }

    // Retrieves an ItemStack for displaying certain color text
    // A best matching wool color item is chosen
    private static final EnumMap<ChatColor, ItemStack> _colorItemCache = new EnumMap<ChatColor, ItemStack>(ChatColor.class);

    private static final ChatColor getTextColor(String text) {
        ChatColor color = ChatColor.BLACK;
        if (text.length() >= 2 && text.charAt(0) == StringUtil.CHAT_STYLE_CHAR) {
            color = ChatColor.getByChar(text.charAt(1));
            if (color == null) {
                color = ChatColor.BLACK;
            }
        }
        return color;
    }

    @SuppressWarnings("deprecation")
    private static final ItemStack getItem(ChatColor color) {
        // Find the item that matches
        ItemStack item = _colorItemCache.get(color);
        if (item == null) {
            // Attempts to use concrete if it exists, otherwise falls back to WOOL
            // Uses legacy <1.13 logic, because on 1.13 and onwards the color is no longer part of data
            BlockData block = BlockData.fromMaterialData(
                    MaterialUtil.getFirst("LEGACY_CONCRETE_POWDER", "LEGACY_WOOL"),
                    toDyeColor(color).getWoolData());
            item = block.createItem(1);

            // Store
            _colorItemCache.put(color, item);
        }
        return item;
    }

    // turns a chat color into a dye color with best effort
    @SuppressWarnings("deprecation")
    private static final DyeColor toDyeColor(ChatColor color) {
        //TODO: Meh?
        switch (color) {
        case BLACK:
            return DyeColor.BLACK;
        case DARK_BLUE:
            return DyeColor.BLUE;
        case DARK_GREEN:
            return DyeColor.GREEN;
        case DARK_AQUA:
            return DyeColor.CYAN;
        case DARK_RED:
            return DyeColor.RED;
        case DARK_PURPLE:
            return DyeColor.PURPLE;
        case GOLD:
            return DyeColor.ORANGE;
        case GRAY:
            return DyeColor.getByWoolData((byte) 0x8);
        case DARK_GRAY:
            return DyeColor.GRAY;
        case BLUE:
            return DyeColor.LIGHT_BLUE;
        case GREEN:
            return DyeColor.LIME;
        case AQUA:
            return DyeColor.LIGHT_BLUE;
        case RED:
            return DyeColor.BROWN;
        case LIGHT_PURPLE:
            return DyeColor.MAGENTA;
        case YELLOW:
            return DyeColor.YELLOW;
        case WHITE:
            return DyeColor.WHITE;
        default:
            return DyeColor.BLACK;
        }
    }

    // cyclical array of chat colors used to turn an index into a color
    // there is a clear red/green/blue/cyan/magenta/yellow repeating pattern
    private static final ChatColor[] color_wheel_values = {
            ChatColor.DARK_RED, ChatColor.DARK_GREEN, ChatColor.DARK_BLUE,
            ChatColor.DARK_AQUA, ChatColor.DARK_PURPLE, ChatColor.YELLOW,
            ChatColor.RED, ChatColor.GREEN, ChatColor.BLUE,
            ChatColor.AQUA, ChatColor.LIGHT_PURPLE, ChatColor.GOLD,
            ChatColor.BLACK, ChatColor.DARK_GRAY, ChatColor.GRAY, ChatColor.WHITE
    };

}
