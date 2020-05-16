package com.bergerkiller.bukkit.coasters.objects;

import java.util.function.Supplier;

import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.csv.TrackCSV.TrackObjectTypeEntry;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.object.ui.BlockSelectMenu;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleFallingBlock;
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

/**
 * Item stack displayed on an armorstand
 */
public class TrackObjectTypeFallingBlock implements TrackObjectType<TrackParticleFallingBlock> {
    private final double width;
    private final Matrix4x4 transform;
    private final BlockData material;

    private TrackObjectTypeFallingBlock(double width, Matrix4x4 transform, BlockData material) {
        if (material == null) {
            throw new IllegalArgumentException("Material can not be null");
        }
        this.width = width;
        this.transform = transform;
        this.material = material;
    }

    public static TrackObjectTypeFallingBlock create(double width, BlockData material) {
        return new TrackObjectTypeFallingBlock(width, null, material);
    }

    public static TrackObjectTypeFallingBlock createDefault() {
        return create(0.0, BlockData.fromMaterial(MaterialUtil.getFirst("OAK_PLANKS", "LEGACY_WOOD")));
    }

    @Override
    public String getTitle() {
        return "Block";
    }

    @Override
    public double getWidth() {
        return this.width;
    }

    @Override
    public TrackObjectTypeFallingBlock setWidth(double width) {
        return new TrackObjectTypeFallingBlock(width, this.transform, this.material);
    }

    @Override
    public Matrix4x4 getTransform() {
        return this.transform;
    }

    @Override
    public TrackObjectType<TrackParticleFallingBlock> setTransform(Matrix4x4 transform) {
        return new TrackObjectTypeFallingBlock(this.width, transform, this.material);
    }

    /**
     * Gets the material displayed
     * 
     * @return material
     */
    public BlockData getMaterial() {
        return this.material;
    }

    /**
     * Creates a copy of this type with the material changed
     * 
     * @param material The new material to set
     * @return copy of this type with material changed
     */
    public TrackObjectTypeFallingBlock setMaterial(BlockData material) {
        return new TrackObjectTypeFallingBlock(this.width, this.transform, material);
    }

    @Override
    public String generateName() {
        return "B_" + material.getBlockName();
    }

    @Override
    public TrackParticleFallingBlock createParticle(TrackConnection.PointOnPath point) {
        TrackParticleFallingBlock particle = point.getWorld().getParticles().addParticleFallingBlock(point.position, point.orientation, this.material);
        particle.setAlwaysVisible(true);
        return particle;
    }

    @Override
    public void updateParticle(TrackParticleFallingBlock particle, TrackConnection.PointOnPath point) {
        particle.setPositionOrientation(point.position, point.orientation);
        particle.setMaterial(this.material);
    }

    @Override
    public boolean isSameImage(TrackObjectType<?> type) {
        return this.getMaterial().equals(((TrackObjectTypeFallingBlock) type).getMaterial());
    }

    @Override
    public void drawImage(TCCoasters plugin, MapCanvas canvas) {
        Model model = plugin.getResourcePack().getBlockModel(this.material);
        canvas.setLightOptions(0.0f, 1.0f, new Vector3(-1, 1, -1));
        canvas.drawModel(model, 1.0f, 27, 22, 225.0f, -45.0f);
    }

    @Override
    public void openMenu(MapWidget parent, Supplier<PlayerEditState> stateSupplier) {
        parent.addWidget(new BlockSelectMenu(stateSupplier));
    }

    @Override
    public TrackObjectTypeFallingBlock acceptItem(ItemStack item) {
        BlockData block = BlockData.fromItemStack(item);
        if (block != null) {
            return this.setMaterial(block);
        } else {
            return this;
        }
    }

    @Override
    public int hashCode() {
        return this.material.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof TrackObjectTypeFallingBlock) {
            TrackObjectTypeFallingBlock other = (TrackObjectTypeFallingBlock) o;
            return this.material == other.material &&
                   this.width == other.width &&
                   LogicUtil.bothNullOrEqual(this.transform, other.transform);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "{TrackObjectType[FallingBlock] material=" + this.material.getBlockName() + "}";
    }

    /**
     * Stores the details of a material (in a falling block), which can later be referred to again by name
     */
    public static final class CSVEntry extends TrackObjectTypeEntry<TrackObjectTypeFallingBlock> {
        @Override
        public String getType() {
            return "BLOCK";
        }

        @Override
        public TrackObjectTypeFallingBlock getDefaultType() {
            return TrackObjectTypeFallingBlock.createDefault();
        }

        @Override
        public TrackObjectTypeFallingBlock readDetails(StringArrayBuffer buffer) throws SyntaxException {
            BlockData material = buffer.nextBlockData();
            return TrackObjectTypeFallingBlock.create(this.width, material);
        }

        @Override
        public void writeDetails(StringArrayBuffer buffer, TrackObjectTypeFallingBlock objectType) {
            buffer.putBlockData(objectType.getMaterial());
        }
    }
}
