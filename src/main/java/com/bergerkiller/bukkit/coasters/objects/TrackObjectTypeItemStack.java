package com.bergerkiller.bukkit.coasters.objects;

import java.util.function.Supplier;

import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.object.ui.ItemSelectMenu;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleArmorStandItem;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.csv.TrackCoasterCSV.TrackObjectTypeEntry;
import com.bergerkiller.bukkit.coasters.util.StringArrayBuffer;
import com.bergerkiller.bukkit.coasters.util.SyntaxException;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;

/**
 * Item stack displayed on an armorstand
 */
public class TrackObjectTypeItemStack implements TrackObjectType<TrackParticleArmorStandItem> {
    private final double width;
    private final ItemStack item;

    private TrackObjectTypeItemStack(double width, ItemStack item) {
        if (item == null) {
            throw new IllegalArgumentException("Item can not be null");
        }
        this.width = width;
        this.item = item;
    }

    public static TrackObjectTypeItemStack create(double width, ItemStack item) {
        return new TrackObjectTypeItemStack(width, item);
    }

    public static TrackObjectTypeItemStack createDefault() {
        return create(0.0, new ItemStack(MaterialUtil.getFirst("RAIL", "LEGACY_RAILS")));
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
    public TrackObjectTypeItemStack setWidth(double width) {
        return new TrackObjectTypeItemStack(width, this.item);
    }

    /**
     * Gets the item stack displayed
     * 
     * @return item stack
     */
    public ItemStack getItem() {
        return this.item;
    }

    /**
     * Creates a copy of this type with the item stack changed
     * 
     * @param item The new item stack to set
     * @return copy of this type with item stack changed
     */
    public TrackObjectTypeItemStack setItem(ItemStack item) {
        return new TrackObjectTypeItemStack(this.width, item);
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
        return this.getItem().equals(((TrackObjectTypeItemStack) type).getItem());
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
    public TrackObjectTypeItemStack acceptItem(ItemStack item) {
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
        } else if (o instanceof TrackObjectTypeItemStack) {
            TrackObjectTypeItemStack other = (TrackObjectTypeItemStack) o;
            return this.item.equals(other.item) && this.width == other.width;
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
    public static final class CSVEntry extends TrackObjectTypeEntry<TrackObjectTypeItemStack> {
        @Override
        public String getType() {
            return "ITEMSTACK";
        }

        @Override
        public TrackObjectTypeItemStack getDefaultType() {
            return TrackObjectTypeItemStack.createDefault();
        }

        @Override
        public TrackObjectTypeItemStack readDetails(StringArrayBuffer buffer) throws SyntaxException {
            ItemStack itemStack = buffer.nextItemStack();
            if (itemStack == null) {
                return null;
            }
            return TrackObjectTypeItemStack.create(this.width, itemStack);
        }

        @Override
        public void writeDetails(StringArrayBuffer buffer, TrackObjectTypeItemStack objectType) {
            buffer.putItemStack(objectType.getItem());
        }
    }
}
