package com.bergerkiller.bukkit.coasters.particles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntSupplier;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.collections.octree.DoubleOctree;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.ChatText;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityDestroyHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityTeleportHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.decoration.EntityArmorStandHandle;

import net.md_5.bungee.api.ChatColor;

/**
 * A particle consisting of a text balloon that always faces the viewer,
 * containing the text of the fake signs put at a particular node.
 */
public class TrackParticleSignText extends TrackParticle {
    protected static final int FLAG_POSITION_CHANGED    = (1<<2);
    private static final Vector ARMORSTAND_OFFSET = new Vector(0.0, 0.0, 0.0);
    private TextLine[] lines;
    private DoubleOctree.Entry<TrackParticle> position;

    protected TrackParticleSignText(Vector position, String[][] signLines) {
        this.position = DoubleOctree.Entry.create(position, this);
        this.lines = generateLines(signLines, EntityUtil::getUniqueEntityId);
    }

    private static TextLine[] generateLines(String[][] signLines, IntSupplier entityIdSupplier) {
        List<TextLine> lines = new ArrayList<>();
        for (String[] sign : signLines) {
            if (!lines.isEmpty()) {
                lines.add(TextLineSeparator.create(entityIdSupplier.getAsInt()));
            }
            for (String line : sign) {
                lines.add(TextLine.create(entityIdSupplier.getAsInt(), line));
            }
        }
        return lines.toArray(new TextLine[lines.size()]);
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

    public void setSignLines(String[][] lines) {
        // De-spawn the original armorstands
        for (Player player : this.getViewers()) {
            this.makeHiddenFor(player);
        }

        // Update the lines. Re-use entity id's if we can.
        TextLineEntityIdExtractor extractor = new TextLineEntityIdExtractor(this.lines);
        this.lines = generateLines(lines, extractor::get);

        // Make the new lines visible again
        for (Player player : this.getViewers()) {
            this.makeVisibleFor(player);
        }
    }

    @Override
    public double distanceSquared(Vector viewerPosition) {
        return this.position.distanceSquared(viewerPosition);
    }

    @Override
    public void makeVisibleFor(Player viewer) {
        double yOffset = 0.0;
        for (int index = this.lines.length; --index >= 0;) {
            TextLine line = this.lines[index];
            double half_offset = 0.5 * line.getOffset();

            // Spawns an invisible armorstand, which displays only the nametag
            yOffset += half_offset;
            PacketPlayOutSpawnEntityLivingHandle spawnPacket = PacketPlayOutSpawnEntityLivingHandle.T.newHandleNull();
            spawnPacket.setEntityId(line.entityId);
            spawnPacket.setEntityUUID(UUID.randomUUID());
            spawnPacket.setEntityType(EntityType.ARMOR_STAND);
            spawnPacket.setPosX(this.position.getX() + ARMORSTAND_OFFSET.getX());
            spawnPacket.setPosY(this.position.getY() + ARMORSTAND_OFFSET.getY() + yOffset);
            spawnPacket.setPosZ(this.position.getZ() + ARMORSTAND_OFFSET.getZ());
            spawnPacket.setMotX(0.0);
            spawnPacket.setMotY(0.0);
            spawnPacket.setMotZ(0.0);
            spawnPacket.setPitch(0.0f);
            spawnPacket.setYaw(0.0f);
            PacketUtil.sendEntityLivingSpawnPacket(viewer, spawnPacket, line.metadata);
            yOffset += half_offset;
        }
    }

    @Override
    public void makeHiddenFor(Player viewer) {
        if (PacketPlayOutEntityDestroyHandle.canDestroyMultiple()) {
            int[] ids = new int[this.lines.length];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = this.lines[i].entityId;
            }
            PacketUtil.sendPacket(viewer, PacketPlayOutEntityDestroyHandle.createNewMultiple(ids));
        } else {
            for (TextLine line : this.lines) {
                PacketUtil.sendPacket(viewer, PacketPlayOutEntityDestroyHandle.createNewSingle(line.entityId));
            }
        }
    }

    @Override
    public void updateAppearance() {
        if (this.clearFlag(FLAG_POSITION_CHANGED)) {
            double yOffset = 0.0;
            for (int index = this.lines.length; --index >= 0;) {
                TextLine line = this.lines[index];
                double half_offset = 0.5 * line.getOffset();

                yOffset += half_offset;
                PacketPlayOutEntityTeleportHandle tpPacket = PacketPlayOutEntityTeleportHandle.createNew(
                        line.entityId,
                        this.position.getX() + ARMORSTAND_OFFSET.getX(),
                        this.position.getY() + ARMORSTAND_OFFSET.getY() + yOffset,
                        this.position.getZ() + ARMORSTAND_OFFSET.getZ(),
                        0.0f, 0.0f, false);
                broadcastPacket(tpPacket);
                yOffset += half_offset;
            }
        }
    }

    @Override
    public boolean usesEntityId(int entityId) {
        for (TextLine line : this.lines) {
            if (line.entityId == entityId) {
                return true;
            }
        }
        return false;
    }

    private static class TextLineEntityIdExtractor {
        private final TextLine[] lines;
        private int index;

        public TextLineEntityIdExtractor(TextLine[] lines) {
            this.lines = lines;
            this.index = 0;
        }

        public int get() {
            if (index < lines.length) {
                return lines[index++].entityId;
            } else {
                return EntityUtil.getUniqueEntityId();
            }
        }
    }

    private static class TextLine {
        public final int entityId;
        public final DataWatcher metadata;

        protected TextLine(int entityId, ChatText text) {
            this.entityId = entityId;

            this.metadata = new DataWatcher();
            this.metadata.set(EntityHandle.DATA_NO_GRAVITY, true);
            this.metadata.set(EntityHandle.DATA_CUSTOM_NAME_VISIBLE, true);
            this.metadata.set(EntityHandle.DATA_CUSTOM_NAME, text);
            this.metadata.set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE | EntityHandle.DATA_FLAG_ON_FIRE));
            this.metadata.set(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, (byte) (EntityArmorStandHandle.DATA_FLAG_SET_MARKER
                    | EntityArmorStandHandle.DATA_FLAG_NO_BASEPLATE | EntityArmorStandHandle.DATA_FLAG_IS_SMALL));
        }

        public double getOffset() {
            return 0.23;
        }

        public static TextLine create(int entityId, String text) {
            return new TextLine(entityId, ChatText.fromMessage(ChatColor.GREEN + text));
        }
    }

    private static class TextLineSeparator extends TextLine {
        private static final ChatText SEPARATOR_TEXT = ChatText.fromMessage(ChatColor.RED.toString() + ChatColor.STRIKETHROUGH.toString() + "              ");

        protected TextLineSeparator(int entityId) {
            super(entityId, SEPARATOR_TEXT);
        }

        public double getOffset() {
            return 0.1;
        }

        public static TextLineSeparator create(int entityId) {
            return new TextLineSeparator(entityId);
        }
    }
}
