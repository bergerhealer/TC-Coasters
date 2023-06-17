package com.bergerkiller.bukkit.coasters.objects.display;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.csv.TrackCSV.TrackObjectTypeEntry;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.object.ui.BlockSelectMenu;
import com.bergerkiller.bukkit.coasters.editor.object.ui.DisplayTypePositionMenu;
import com.bergerkiller.bukkit.coasters.objects.TrackObjectType;
import com.bergerkiller.bukkit.coasters.objects.TrackObjectTypeBlock;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleDisplayBlock;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.util.StringArrayBuffer;
import com.bergerkiller.bukkit.coasters.util.SyntaxException;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.util.Model;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Vector3;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.function.Supplier;

/**
 * Block displayed using a display block entity
 */
public class TrackObjectTypeDisplayBlock implements TrackObjectTypeDisplay<TrackParticleDisplayBlock>, TrackObjectTypeBlock<TrackParticleDisplayBlock> {
    private final double width;
    private final Matrix4x4 transform;
    private final BlockData blockData;
    private final double clip;
    private final Vector size;

    private TrackObjectTypeDisplayBlock(double width, Matrix4x4 transform, double clip, Vector size, BlockData blockData) {
        if (blockData == null) {
            throw new IllegalArgumentException("Block can not be null");
        }
        this.width = width;
        this.transform = transform;
        this.clip = clip;
        this.size = size;
        this.blockData = blockData;
    }

    public static TrackObjectTypeDisplayBlock create(double width, double clip, Vector size, BlockData blockData) {
        return new TrackObjectTypeDisplayBlock(width, null, clip, size, blockData);
    }

    public static TrackObjectTypeDisplayBlock createDefault() {
        return create(0.0, 0.0, new Vector(1.0, 1.0, 1.0),
                BlockData.fromMaterial(MaterialUtil.getFirst("OAK_PLANKS", "LEGACY_WOOD")));
    }

    @Override
    public String getTitle() {
        return "Block\nDisplay";
    }

    @Override
    public double getWidth() {
        return this.width;
    }

    @Override
    public TrackObjectTypeDisplayBlock setWidth(double width) {
        return new TrackObjectTypeDisplayBlock(width, this.transform, this.clip, this.size, this.blockData);
    }

    @Override
    public Matrix4x4 getTransform() {
        return this.transform;
    }

    @Override
    public TrackObjectTypeDisplayBlock setTransform(Matrix4x4 transform) {
        return new TrackObjectTypeDisplayBlock(this.width, transform, this.clip, this.size, this.blockData);
    }

    @Override
    public BlockData getBlockData() {
        return this.blockData;
    }

    @Override
    public TrackObjectTypeDisplayBlock setBlockData(BlockData blockData) {
        return new TrackObjectTypeDisplayBlock(this.width, this.transform, this.clip, this.size, blockData);
    }

    @Override
    public double getClip() {
        return clip;
    }

    @Override
    public TrackObjectTypeDisplayBlock setClip(double clip) {
        return new TrackObjectTypeDisplayBlock(this.width, this.transform, clip, this.size, this.blockData);
    }

    @Override
    public Vector getSize() {
        return size;
    }

    @Override
    public TrackObjectTypeDisplayBlock setSize(Vector size) {
        return new TrackObjectTypeDisplayBlock(this.width, this.transform, this.clip, size, this.blockData);
    }

    @Override
    public String generateName() {
        return "B_D_" + blockData.getBlockName();
    }

    @Override
    public TrackParticleDisplayBlock createParticle(TrackConnection.PointOnPath point) {
        TrackParticleDisplayBlock particle = point.getWorld().getParticles().addParticleDisplayBlock(
                point.position, point.orientation, this.clip, this.size, this.blockData);
        particle.setAlwaysVisible(true);
        return particle;
    }

    @Override
    public void updateParticle(TrackParticleDisplayBlock particle, TrackConnection.PointOnPath point) {
        particle.setPositionOrientation(point.position, point.orientation);
        particle.setClip(this.clip);
        particle.setSize(this.size);
        particle.setBlockData(this.blockData);
    }

    @Override
    public boolean isSameImage(TrackObjectType<?> type) {
        return this.getBlockData().equals(((TrackObjectTypeDisplayBlock) type).getBlockData());
    }

    @Override
    public void drawImage(TCCoasters plugin, MapCanvas canvas) {
        Model model = plugin.getResourcePack().getBlockModel(this.blockData);
        canvas.setLightOptions(0.0f, 1.0f, new Vector3(-1, 1, -1));
        canvas.drawModel(model, 1.0f, 27, 22, 225.0f, -45.0f);
    }

    @Override
    public void openMenu(MapWidget parent, Supplier<PlayerEditState> stateSupplier) {
        parent.addWidget(new BlockSelectMenu(stateSupplier));
    }

    @Override
    public void openPositionMenu(MapWidget parent, Supplier<PlayerEditState> stateSupplier) {
        parent.addWidget(new DisplayTypePositionMenu(stateSupplier));
    }

    @Override
    public TrackObjectTypeDisplayBlock acceptItem(ItemStack item) {
        BlockData block = BlockData.fromItemStack(item);
        if (block != null) {
            return this.setBlockData(block);
        } else {
            return this;
        }
    }

    @Override
    public int hashCode() {
        return this.blockData.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof TrackObjectTypeDisplayBlock) {
            TrackObjectTypeDisplayBlock other = (TrackObjectTypeDisplayBlock) o;
            return this.blockData == other.blockData &&
                   this.width == other.width &&
                   this.clip == other.clip &&
                   this.size.equals(other.size) &&
                   LogicUtil.bothNullOrEqual(this.transform, other.transform);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "{TrackObjectType[Block Display] block=" + this.blockData + "}";
    }

    /**
     * Stores the details of the block BlockData, clip and scale, which can later be referred to again by name
     */
    public static final class CSVEntry extends TrackObjectTypeEntry<TrackObjectTypeDisplayBlock> {
        @Override
        public String getType() {
            return "BLOCK_DISPLAY";
        }

        @Override
        public TrackObjectTypeDisplayBlock getDefaultType() {
            return TrackObjectTypeDisplayBlock.createDefault();
        }

        @Override
        public TrackObjectTypeDisplayBlock readDetails(StringArrayBuffer buffer) throws SyntaxException {
            BlockData blockData = buffer.nextBlockData();
            if (blockData == null) {
                return null;
            }

            // Read additional display properties, keyed by name
            double clip = 0.0;
            Vector size = new Vector(1.0, 1.0, 1.0);
            while (buffer.hasNext()) {
                String propKey = buffer.next();
                if (propKey.equals("CLIP")) {
                    clip = buffer.nextDouble();
                } else if (propKey.equals("SIZE")) {
                    size = buffer.nextVector();
                }
            }

            return TrackObjectTypeDisplayBlock.create(this.width, clip, size, blockData);
        }

        @Override
        public void writeDetails(StringArrayBuffer buffer, TrackObjectTypeDisplayBlock objectType) {
            buffer.putBlockData(objectType.getBlockData());
            if (objectType.getClip() != 0.0) {
                buffer.put("CLIP");
                buffer.putDouble(objectType.getClip());
            }
            if (objectType.getSize().getX() != 1.0 ||
                objectType.getSize().getY() != 1.0 ||
                objectType.getSize().getZ() != 1.0
            ) {
                buffer.put("SIZE");
                buffer.putVector(objectType.getSize());
            }
        }
    }
}
