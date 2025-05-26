package com.bergerkiller.bukkit.coasters.objects.display;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.csv.TrackCSV.TrackObjectTypeEntry;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.object.ui.DisplayTypePositionMenu;
import com.bergerkiller.bukkit.coasters.editor.object.ui.ItemSelectMenu;
import com.bergerkiller.bukkit.coasters.objects.TrackObjectType;
import com.bergerkiller.bukkit.coasters.objects.TrackObjectTypeItem;
import com.bergerkiller.bukkit.coasters.objects.lod.LODItemStack;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleDisplayItem;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.util.StringArrayBuffer;
import com.bergerkiller.bukkit.coasters.util.SyntaxException;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.wrappers.Brightness;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Item stack displayed using a display item entity
 */
public class TrackObjectTypeDisplayItemStack implements TrackObjectTypeDisplay<TrackParticleDisplayItem>, TrackObjectTypeItem<TrackParticleDisplayItem> {
    private final double width;
    private final Matrix4x4 transform;
    private final LODItemStack.List lodList;
    private final double clip;
    private final Brightness brightness;
    private final Vector size;

    private TrackObjectTypeDisplayItemStack(double width, Matrix4x4 transform, double clip, Brightness brightness, Vector size, LODItemStack.List lodList) {
        if (lodList == null) {
            throw new IllegalArgumentException("Item can not be null");
        }
        this.width = width;
        this.transform = transform;
        this.clip = clip;
        this.size = size;
        this.brightness = brightness;
        this.lodList = lodList;
    }

    public static TrackObjectTypeDisplayItemStack create(double width, double clip, Brightness brightness, Vector size, ItemStack item) {
        return create(width, clip, brightness, size, LODItemStack.createList(item));
    }

    public static TrackObjectTypeDisplayItemStack create(double width, double clip, Brightness brightness, Vector size, LODItemStack.List lodList) {
        return new TrackObjectTypeDisplayItemStack(width, null, clip, brightness, size, lodList);
    }

    public static TrackObjectTypeDisplayItemStack createDefault() {
        return create(0.0, 0.0, Brightness.UNSET, new Vector(1.0, 1.0, 1.0),
                new ItemStack(MaterialUtil.getFirst("OAK_PLANKS", "LEGACY_WOOD")));
    }

    @Override
    public String getTitle() {
        return "Item\nDisplay";
    }

    @Override
    public double getWidth() {
        return this.width;
    }

    @Override
    public TrackObjectTypeDisplayItemStack setWidth(double width) {
        return new TrackObjectTypeDisplayItemStack(width, this.transform, this.clip, this.brightness, this.size, this.lodList);
    }

    @Override
    public Matrix4x4 getTransform() {
        return this.transform;
    }

    @Override
    public TrackObjectTypeDisplayItemStack setTransform(Matrix4x4 transform) {
        return new TrackObjectTypeDisplayItemStack(this.width, transform, this.clip, this.brightness, this.size, this.lodList);
    }

    @Override
    public LODItemStack.List getLODItems() {
        return this.lodList;
    }

    @Override
    public TrackObjectTypeDisplayItemStack setLODItems(LODItemStack.List lodList) {
        return new TrackObjectTypeDisplayItemStack(this.width, this.transform, this.clip, this.brightness, this.size, lodList);
    }

    @Override
    public double getClip() {
        return clip;
    }

    @Override
    public TrackObjectTypeDisplayItemStack setClip(double clip) {
        return new TrackObjectTypeDisplayItemStack(this.width, this.transform, clip, this.brightness, this.size, this.lodList);
    }

    @Override
    public Brightness getBrightness() {
        return brightness;
    }

    @Override
    public TrackObjectTypeDisplayItemStack setBrightness(Brightness brightness) {
        return new TrackObjectTypeDisplayItemStack(this.width, this.transform, this.clip, brightness, this.size, this.lodList);
    }

    @Override
    public Vector getSize() {
        return size;
    }

    @Override
    public TrackObjectTypeDisplayItemStack setSize(Vector size) {
        return new TrackObjectTypeDisplayItemStack(this.width, this.transform, this.clip, this.brightness, size, this.lodList);
    }

    @Override
    @SuppressWarnings("deprecation")
    public String generateName() {
        ItemStack icon = lodList.getIcon();
        if (icon == null) {
            return "I_D_UNSET";
        } else if (ItemUtil.hasDurability(icon)) {
            return "I_D_" + icon.getType() + "_" + icon.getDurability();
        } else {
            return "I_D_" + icon.getType();
        }
    }

    @Override
    public TrackParticleDisplayItem createParticle(TrackConnection.PointOnPath point) {
        TrackParticleDisplayItem particle = point.getWorld().getParticles().addParticleDisplayItem(
                point.position, point.orientation, this.clip, this.brightness, this.size,
                this.lodList.getNearest().getItem());
        particle.setAlwaysVisible(true);
        return particle;
    }

    @Override
    public void updateParticle(TrackParticleDisplayItem particle, TrackConnection.PointOnPath point) {
        particle.setPositionOrientation(point.position, point.orientation);
        particle.setClip(this.clip);
        particle.setBrightness(this.brightness);
        particle.setSize(this.size);
        particle.setItem(this.lodList.getNearest().getItem());
    }

    @Override
    public boolean isSameImage(TrackObjectType<?> type) {
        return Objects.equals(this.getLODItems().getIcon(),
                ((TrackObjectTypeDisplayItemStack) type).getLODItems().getIcon());
    }

    @Override
    public void drawImage(TCCoasters plugin, MapCanvas canvas) {
        ItemStack icon = this.lodList.getIcon();
        if (icon != null) {
            canvas.fillItem(plugin.getResourcePack(), icon);
        }
    }

    @Override
    public void openMenu(MapWidget parent, Supplier<PlayerEditState> stateSupplier) {
        parent.addWidget(new ItemSelectMenu(stateSupplier));
    }

    @Override
    public void openPositionMenu(MapWidget parent, Supplier<PlayerEditState> stateSupplier) {
        parent.addWidget(new DisplayTypePositionMenu(stateSupplier));
    }

    @Override
    public TrackObjectTypeDisplayItemStack acceptItem(ItemStack item) {
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
        } else if (o instanceof TrackObjectTypeDisplayItemStack) {
            TrackObjectTypeDisplayItemStack other = (TrackObjectTypeDisplayItemStack) o;
            return this.lodList.equals(other.lodList) &&
                   this.width == other.width &&
                   this.clip == other.clip &&
                   this.brightness == other.brightness &&
                   this.size.equals(other.size) &&
                   LogicUtil.bothNullOrEqual(this.transform, other.transform);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "{TrackObjectType[ItemStack Display] lod-items=" + this.lodList + "}";
    }

    /**
     * Stores the details of an ItemStack, clip and scale, which can later be referred to again by name
     */
    public static final class CSVEntry extends TrackObjectTypeEntry<TrackObjectTypeDisplayItemStack> {
        @Override
        public String getType() {
            return "ITEMSTACK_DISPLAY";
        }

        @Override
        public TrackObjectTypeDisplayItemStack getDefaultType() {
            return TrackObjectTypeDisplayItemStack.createDefault();
        }

        @Override
        public TrackObjectTypeDisplayItemStack readDetails(StringArrayBuffer buffer) throws SyntaxException {
            ItemStack itemStack = buffer.nextItemStack();
            if (itemStack == null) {
                return null;
            }

            // Read additional display properties, keyed by name
            double clip = 0.0;
            Brightness brightness = Brightness.UNSET;
            Vector size = new Vector(1.0, 1.0, 1.0);
            while (buffer.hasNext()) {
                String propKey = buffer.next();
                if (propKey.equals("CLIP")) {
                    clip = buffer.nextDouble();
                } else if (propKey.equals("SIZE")) {
                    size = buffer.nextVector();
                } else if (propKey.equals("BRIGHTNESS")) {
                    brightness = buffer.nextBrightness();
                }
            }

            return TrackObjectTypeDisplayItemStack.create(this.width, clip, brightness, size, itemStack);
        }

        @Override
        public void writeDetails(StringArrayBuffer buffer, TrackObjectTypeDisplayItemStack objectType) {
            buffer.putItemStack(objectType.getLODItems().getNearest().getItem());
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
            if (objectType.getBrightness() != Brightness.UNSET) {
                buffer.put("BRIGHTNESS");
                buffer.putBrightness(objectType.getBrightness());
            }
        }
    }
}
