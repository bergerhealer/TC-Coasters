package com.bergerkiller.bukkit.coasters.objects;

import java.util.function.Supplier;

import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.csv.TrackCSV.TrackObjectTypeEntry;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.object.ui.ItemSelectMenu;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleArmorStandItem;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.util.StringArrayBuffer;
import com.bergerkiller.bukkit.coasters.util.SyntaxException;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;

/**
 * Item stack displayed on an armorstand
 */
public class TrackObjectTypeArmorStandItem implements TrackObjectTypeItem<TrackParticleArmorStandItem> {
    private final double width;
    private final Matrix4x4 transform;
    private final ItemStack item;

    private TrackObjectTypeArmorStandItem(double width, Matrix4x4 transform, ItemStack item) {
        if (item == null) {
            throw new IllegalArgumentException("Item can not be null");
        }
        this.width = width;
        this.transform = transform;
        this.item = item;
    }

    public static TrackObjectTypeArmorStandItem create(double width, ItemStack item) {
        return new TrackObjectTypeArmorStandItem(width, null, item);
    }

    public static TrackObjectTypeArmorStandItem createDefault() {
        return create(0.0, new ItemStack(MaterialUtil.getFirst("OAK_PLANKS", "LEGACY_WOOD")));
    }

    @Override
    public String getTitle() {
        return "Item";
    }

    @Override
    public double getWidth() {
        return this.width;
    }

    @Override
    public TrackObjectTypeArmorStandItem setWidth(double width) {
        return new TrackObjectTypeArmorStandItem(width, this.transform, this.item);
    }

    @Override
    public Matrix4x4 getTransform() {
        return this.transform;
    }

    @Override
    public TrackObjectType<TrackParticleArmorStandItem> setTransform(Matrix4x4 transform) {
        return new TrackObjectTypeArmorStandItem(this.width, transform, this.item);
    }

    @Override
    public ItemStack getItem() {
        return this.item;
    }

    @Override
    public TrackObjectTypeArmorStandItem setItem(ItemStack item) {
        return new TrackObjectTypeArmorStandItem(this.width, this.transform, item);
    }

    @Override
    @SuppressWarnings("deprecation")
    public String generateName() {
        if (ItemUtil.hasDurability(item)) {
            return "I_" + item.getType() + "_" + item.getDurability();
        } else {
            return "I_" + item.getType();
        }
    }

    @Override
    public TrackParticleArmorStandItem createParticle(TrackConnection.PointOnPath point) {
        TrackParticleArmorStandItem particle = point.getWorld().getParticles().addParticleArmorStandItem(point.position, point.orientation, this.item);
        particle.setAlwaysVisible(true);
        return particle;
    }

    @Override
    public void updateParticle(TrackParticleArmorStandItem particle, TrackConnection.PointOnPath point) {
        particle.setPositionOrientation(point.position, point.orientation);
        particle.setItem(this.item);
    }

    @Override
    public boolean isSameImage(TrackObjectType<?> type) {
        return this.getItem().equals(((TrackObjectTypeArmorStandItem) type).getItem());
    }

    @Override
    public void drawImage(TCCoasters plugin, MapCanvas canvas) {
        canvas.fillItem(plugin.getResourcePack(), this.item);
    }

    @Override
    public void openMenu(MapWidget parent, Supplier<PlayerEditState> stateSupplier) {
        parent.addWidget(new ItemSelectMenu(stateSupplier));
    }

    @Override
    public TrackObjectTypeArmorStandItem acceptItem(ItemStack item) {
        return this.setItem(item);
    }

    @Override
    public int hashCode() {
        return this.item.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof TrackObjectTypeArmorStandItem) {
            TrackObjectTypeArmorStandItem other = (TrackObjectTypeArmorStandItem) o;
            return this.item.equals(other.item) &&
                   this.width == other.width &&
                   LogicUtil.bothNullOrEqual(this.transform, other.transform);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "{TrackObjectType[ItemStack] item=" + this.item + "}";
    }

    /**
     * Stores the details of an ItemStack, which can later be referred to again by name
     */
    public static final class CSVEntry extends TrackObjectTypeEntry<TrackObjectTypeArmorStandItem> {
        @Override
        public String getType() {
            return "ITEMSTACK";
        }

        @Override
        public TrackObjectTypeArmorStandItem getDefaultType() {
            return TrackObjectTypeArmorStandItem.createDefault();
        }

        @Override
        public TrackObjectTypeArmorStandItem readDetails(StringArrayBuffer buffer) throws SyntaxException {
            ItemStack itemStack = buffer.nextItemStack();
            if (itemStack == null) {
                return null;
            }
            return TrackObjectTypeArmorStandItem.create(this.width, itemStack);
        }

        @Override
        public void writeDetails(StringArrayBuffer buffer, TrackObjectTypeArmorStandItem objectType) {
            buffer.putItemStack(objectType.getItem());
        }
    }
}
