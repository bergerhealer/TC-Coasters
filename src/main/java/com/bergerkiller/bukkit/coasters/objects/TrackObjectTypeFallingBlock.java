package com.bergerkiller.bukkit.coasters.objects;

import java.util.function.Supplier;

import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.object.ui.ItemSelectMenu;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleFallingBlock;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.util.Model;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.math.Vector3;
import com.bergerkiller.bukkit.common.utils.DebugUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.TCConfig;

/**
 * Item stack displayed on an armorstand
 */
public class TrackObjectTypeFallingBlock implements TrackObjectType<TrackParticleFallingBlock> {
    private final double width;
    private final BlockData material;

    private TrackObjectTypeFallingBlock(double width, BlockData material) {
        if (material == null) {
            throw new IllegalArgumentException("Material can not be null");
        }
        this.width = width;
        this.material = material;
    }

    public static TrackObjectTypeFallingBlock create(double width, BlockData material) {
        return new TrackObjectTypeFallingBlock(width, material);
    }

    public static TrackObjectTypeFallingBlock createDefault() {
        return create(0.0, BlockData.fromMaterial(MaterialUtil.getFirst("RAIL", "LEGACY_RAILS")));
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
        return new TrackObjectTypeFallingBlock(width, this.material);
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
        return new TrackObjectTypeFallingBlock(this.width, material);
    }

    @Override
    public String generateName() {
        return "B_" + material.getBlockName();
    }

    @Override
    public TrackParticleFallingBlock createParticle(TrackConnection.PointOnPath point) {
        return point.getWorld().getParticles().addParticleFallingBlock(point.position, point.orientation, this.material, this.width);
    }

    @Override
    public void updateParticle(TrackParticleFallingBlock particle, TrackConnection.PointOnPath point) {
        particle.setPositionOrientation(point.position, point.orientation);
        particle.setMaterial(this.material);
        particle.setWidth(this.width);
    }

    @Override
    public boolean isSameImage(TrackObjectType<?> type) {
        return this.getMaterial().equals(((TrackObjectTypeFallingBlock) type).getMaterial());
    }

    @Override
    public void drawImage(MapCanvas canvas) {
        Model model = TCConfig.resourcePack.getBlockModel(this.material);
        canvas.setLightOptions(0.0f, 1.0f, new Vector3(-1, 1, -1));
        canvas.drawModel(model, 1.0f, 27, 22, DebugUtil.getFloatValue("a", 225.0f), DebugUtil.getFloatValue("b", -45.0f));
    }

    @Override
    public void openMenu(MapWidget parent, Supplier<PlayerEditState> stateSupplier) {
        parent.addWidget(new ItemSelectMenu(stateSupplier));
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
            return this.material == other.material && this.width == other.width;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "{TrackObjectType[FallingBlock] material=" + this.material.getBlockName() + "}";
    }
}
