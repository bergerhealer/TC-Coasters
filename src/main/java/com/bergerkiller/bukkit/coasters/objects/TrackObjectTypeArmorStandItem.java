package com.bergerkiller.bukkit.coasters.objects;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import com.bergerkiller.bukkit.coasters.csv.TrackCSV;
import com.bergerkiller.bukkit.coasters.editor.object.ui.lod.ItemLODSelect;
import com.bergerkiller.bukkit.coasters.objects.display.TrackObjectTypeDisplayItemStack;
import com.bergerkiller.bukkit.coasters.objects.lod.LODItemStack;
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
    private final LODItemStack.List lodList;

    private TrackObjectTypeArmorStandItem(double width, Matrix4x4 transform, LODItemStack.List lodList) {
        if (lodList == null) {
            throw new IllegalArgumentException("Item can not be null");
        }
        this.width = width;
        this.transform = transform;
        this.lodList = lodList;
    }

    public static TrackObjectTypeArmorStandItem create(double width, ItemStack item) {
        return create(width, LODItemStack.createList(item));
    }

    public static TrackObjectTypeArmorStandItem create(double width, LODItemStack.List lodList) {
        return new TrackObjectTypeArmorStandItem(width, null, lodList);
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
        return new TrackObjectTypeArmorStandItem(width, this.transform, this.lodList);
    }

    @Override
    public Matrix4x4 getTransform() {
        return this.transform;
    }

    @Override
    public TrackObjectType<TrackParticleArmorStandItem> setTransform(Matrix4x4 transform) {
        return new TrackObjectTypeArmorStandItem(this.width, transform, this.lodList);
    }

    @Override
    public LODItemStack.List getLODItems() {
        return this.lodList;
    }

    @Override
    public TrackObjectTypeArmorStandItem setLODItems(LODItemStack.List lodList) {
        return new TrackObjectTypeArmorStandItem(this.width, this.transform, lodList);
    }

    @Override
    @SuppressWarnings("deprecation")
    public String generateName() {
        ItemStack icon = lodList.getNearestNotHidden().getItem();
        if (icon == null) {
            return "I_UNSET";
        } else if (ItemUtil.hasDurability(icon)) {
            return "I_" + icon.getType() + "_" + icon.getDurability();
        } else {
            return "I_" + icon.getType();
        }
    }

    @Override
    public TrackParticleArmorStandItem createParticle(TrackConnection.PointOnPath point) {
        TrackParticleArmorStandItem particle = point.getWorld().getParticles().addParticleArmorStandItem(
                point.position, point.orientation, this.lodList);
        particle.setAlwaysVisible(true);
        return particle;
    }

    @Override
    public void updateParticle(TrackParticleArmorStandItem particle, TrackConnection.PointOnPath point) {
        particle.setPositionOrientation(point.position, point.orientation);
        particle.setLODItems(this.lodList);
    }

    @Override
    public boolean isSameImage(TrackObjectType<?> type) {
        return Objects.equals(this.getLODItems().getNearestNotHidden().getItem(),
                ((TrackObjectTypeArmorStandItem) type).getLODItems().getNearestNotHidden().getItem());
    }

    @Override
    public void drawImage(TCCoasters plugin, MapCanvas canvas) {
        this.lodList.getNearestNotHidden().drawIcon(plugin, canvas);
    }

    @Override
    public void openMenu(MapWidget parent, Supplier<PlayerEditState> stateSupplier) {
        if (lodList.size() == 1) {
            parent.addWidget(new ItemSelectMenu(stateSupplier));
        } else {
            parent.addWidget(new ItemLODSelect(stateSupplier));
        }
    }

    @Override
    public TrackObjectTypeArmorStandItem acceptItem(ItemStack item) {
        return this.setLODItems(this.lodList.update(0,
                this.lodList.getItem(0).withItem(item)));
    }

    @Override
    public int hashCode() {
        return this.lodList.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof TrackObjectTypeArmorStandItem) {
            TrackObjectTypeArmorStandItem other = (TrackObjectTypeArmorStandItem) o;
            return this.lodList.equals(other.lodList) &&
                   this.width == other.width &&
                   LogicUtil.bothNullOrEqual(this.transform, other.transform);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "{TrackObjectType[ItemStack] lod-items=" + this.lodList + "}";
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
        public void processReader(TrackCSV.CSVReaderState state) {
            if (!state.pendingLODs.isEmpty()) {
                LODItemStack.List lodList = objectType.getLODItems();
                for (LODItemStack lodItem : state.pendingLODs) {
                    lodList.addNewLOD(lodItem);
                }
                state.pendingLODs.clear();
                objectType = objectType.setLODItems(lodList);
            }

            super.processReader(state);
        }

        @Override
        public List<? extends TrackCSV.CSVEntry> getExtraCSVEntries() {
            return objectType.getLODItems().encodeExtraLODsForCSV();
        }

        @Override
        public void writeDetails(StringArrayBuffer buffer, TrackObjectTypeArmorStandItem objectType) {
            buffer.putItemStack(objectType.getLODItems().getNearest().getItem());
        }
    }
}
