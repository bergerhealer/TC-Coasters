package com.bergerkiller.bukkit.coasters.particles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.collections.octree.DoubleOctree;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.ChatText;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityDestroyHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityTeleportHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.decoration.EntityArmorStandHandle;

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
        this.lines = generateLines(signLines, signLines.length * 5);
        for (TextLine line : lines) {
            line.entityId = EntityUtil.getUniqueEntityId();
        }
    }

    private static TextLine[] generateLines(String[][] signLines, int expectedLineCount) {
        List<TextLine> lines = new ArrayList<>(expectedLineCount + 5);
        for (int i = 0; i < signLines.length; i++) {
            String[] sign = signLines[i];
            if (!lines.isEmpty()) {
                lines.add(TextLineSeparator.create());
            }
            for (String line : sign) {
                lines.add(TextLineContent.create(i == (signLines.length - 1), line));
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
        TextLine[] old_lines = this.lines;
        TextLine[] new_lines = generateLines(lines, old_lines.length);

        // If only contents have changed, not the line count, try to repurpose the lines first
        // If we find out any types are mismatched, respawn anyway so the positions are correct
        if (old_lines.length == new_lines.length) {
            boolean isUpdated = true;
            for (int i = 0; i < new_lines.length; i++) {
                if (!old_lines[i].tryUpdate(this, new_lines[i])) {
                    // Must respawn
                    isUpdated = false;
                    break;
                }
            }
            if (isUpdated) {
                return;
            }
        }

        // Update the lines. Re-use entity id's if we can.
        for (int i = 0; i < new_lines.length; i++) {
            new_lines[i].entityId = (i < old_lines.length) ? old_lines[i].entityId : EntityUtil.getUniqueEntityId();
        }

        // De-spawn the signs, update the lines, then show the signs again
        this.getWorld().hideAndDisplayParticle(this, p -> p.lines = new_lines);
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
            PacketUtil.sendEntityLivingSpawnPacket(viewer, spawnPacket, line.getMetadata());
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

    private static abstract class TextLine {
        public int entityId;
        private DataWatcher metadata;

        protected TextLine() {
            this.entityId = -1;
            this.metadata = null;
        }

        public DataWatcher getMetadata() {
            DataWatcher meta = this.metadata;
            if (meta == null) {
                meta = new DataWatcher();
                meta.set(EntityHandle.DATA_NO_GRAVITY, true);
                meta.set(EntityHandle.DATA_CUSTOM_NAME_VISIBLE, true);
                meta.set(EntityHandle.DATA_CUSTOM_NAME, buildText());
                meta.set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE | EntityHandle.DATA_FLAG_ON_FIRE));
                meta.set(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, (byte) (EntityArmorStandHandle.DATA_FLAG_SET_MARKER
                        | EntityArmorStandHandle.DATA_FLAG_NO_BASEPLATE | EntityArmorStandHandle.DATA_FLAG_IS_SMALL));
                this.metadata = meta;
            }
            return meta;
        }

        public void rebuildText(TrackParticleSignText particle) {
            DataWatcher meta = this.metadata;
            if (meta != null) {
                meta.set(EntityHandle.DATA_CUSTOM_NAME, buildText());
                particle.broadcastPacket(PacketPlayOutEntityMetadataHandle.createNew(this.entityId, meta, false));
            }
        }

        public double getOffset() {
            return 0.23;
        }

        public abstract ChatText buildText();

        public abstract boolean tryUpdate(TrackParticleSignText particle, TextLine line);
    }

    private static class TextLineContent extends TextLine {
        private boolean isLast;
        private String content;

        private TextLineContent(boolean isLast, String content) {
            this.isLast = isLast;
            this.content = content;
        }

        public static TextLineContent create(boolean isLast, String content) {
            return new TextLineContent(isLast, content);
        }

        @Override
        public boolean tryUpdate(TrackParticleSignText particle, TextLine line) {
            if (line instanceof TextLineContent) {
                TextLineContent line_content = (TextLineContent) line;
                if (line_content.content.equals(this.content) || line_content.isLast != this.isLast) {
                    return true; // No changes needed
                }

                // Update content in metadata and resend it
                this.content = line_content.content;
                this.isLast = line_content.isLast;
                this.rebuildText(particle);
                return true;
            }
            return false;
        }

        @Override
        public ChatText buildText() {
            ChatColor prefix = isLast ? ChatColor.GREEN : ChatColor.DARK_GREEN;
            return ChatText.fromMessage(prefix + content);
        }
    }

    private static class TextLineSeparator extends TextLine {
        private static final ChatText SEPARATOR_TEXT = ChatText.fromMessage(ChatColor.RED.toString() + ChatColor.STRIKETHROUGH.toString() + "              ");

        private TextLineSeparator() {
        }

        public double getOffset() {
            return 0.1;
        }

        public static TextLineSeparator create() {
            return new TextLineSeparator();
        }

        @Override
        public boolean tryUpdate(TrackParticleSignText particle, TextLine line) {
            return line instanceof TextLineSeparator;
        }

        @Override
        public ChatText buildText() {
            return SEPARATOR_TEXT;
        }
    }
}
