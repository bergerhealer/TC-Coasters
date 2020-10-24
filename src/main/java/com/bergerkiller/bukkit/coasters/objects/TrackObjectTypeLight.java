package com.bergerkiller.bukkit.coasters.objects;

import java.util.function.Supplier;

import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.csv.TrackCSV.TrackObjectTypeEntry;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.object.ui.LightSelectMenu;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleLight;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleLight.LightType;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.util.StringArrayBuffer;
import com.bergerkiller.bukkit.coasters.util.SyntaxException;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.LogicUtil;

/**
 * Item stack displayed on an armorstand
 */
public class TrackObjectTypeLight implements TrackObjectType<TrackParticleLight> {
    private final double width;
    private final Matrix4x4 transform;
    private final LightType type;
    private final int level;

    private TrackObjectTypeLight(double width, Matrix4x4 transform, LightType type, int level) {
        if (type == null) {
            throw new IllegalArgumentException("LightType can not be null");
        } else if (level < 0 || level >= 16) {
            throw new IllegalArgumentException("Light level is outside range 0 - 15");
        }
        this.width = width;
        this.transform = transform;
        this.type = type;
        this.level = level;
    }

    public static TrackObjectTypeLight create(double width, LightType type, int level) {
        return new TrackObjectTypeLight(width, null, type, level);
    }

    public static TrackObjectTypeLight createDefault() {
        return create(0.0, LightType.BLOCK, 15);
    }

    @Override
    public String getTitle() {
        return "Light";
    }

    @Override
    public double getWidth() {
        return this.width;
    }

    @Override
    public TrackObjectTypeLight setWidth(double width) {
        return new TrackObjectTypeLight(width, this.transform, this.type, this.level);
    }

    @Override
    public Matrix4x4 getTransform() {
        return this.transform;
    }

    @Override
    public TrackObjectType<TrackParticleLight> setTransform(Matrix4x4 transform) {
        return new TrackObjectTypeLight(this.width, transform, this.type, this.level);
    }

    /**
     * Gets the type of light emitted
     * 
     * @return light type
     */
    public LightType getType() {
        return this.type;
    }

    /**
     * Creates a copy of this type with the emitted light type changed
     * 
     * @param type The new light type to set to
     * @return copy of this type with light type changed
     */
    public TrackObjectTypeLight setType(LightType type) {
        return new TrackObjectTypeLight(this.width, this.transform, type, this.level);
    }

    /**
     * Gets the light level of the light emitted
     * 
     * @return emitted light level
     */
    public int getLevel() {
        return this.level;
    }

    /**
     * Creates a copy of this type with the emitted light level changed
     * 
     * @param level The new light level to set to
     * @return copy of this type with light level changed
     */
    public TrackObjectTypeLight setLevel(int level) {
        return new TrackObjectTypeLight(this.width, this.transform, this.type, level);
    }

    @Override
    public String generateName() {
        return "L_" + this.type.name() + this.level;
    }

    @Override
    public TrackParticleLight createParticle(TrackConnection.PointOnPath point) {
        TrackParticleLight particle = point.getWorld().getParticles().addParticleLight(point.position, this.type, this.level);
        particle.setAlwaysVisible(true);
        return particle;
    }

    @Override
    public void updateParticle(TrackParticleLight particle, TrackConnection.PointOnPath point) {
        particle.setPosition(point.position);
        particle.setType(this.getType());
        particle.setLevel(this.getLevel());
    }

    @Override
    public boolean isSameImage(TrackObjectType<?> type) {
        TrackObjectTypeLight other = (TrackObjectTypeLight) type;
        return this.getType() == other.getType() && this.getLevel() == other.getLevel();
    }

    @Override
    public void drawImage(TCCoasters plugin, MapCanvas canvas) {
        canvas.fill(MapColorPalette.COLOR_YELLOW);
        canvas.draw(MapFont.TINY, 4, 4, MapColorPalette.COLOR_WHITE, Integer.toString(this.level));
    }

    @Override
    public void openMenu(MapWidget parent, Supplier<PlayerEditState> stateSupplier) {
        parent.addWidget(new LightSelectMenu(stateSupplier));
    }

    @Override
    public TrackObjectTypeLight acceptItem(ItemStack item) {
        return this;
    }

    @Override
    public int hashCode() {
        return (this.type.ordinal()<<4) + this.level;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof TrackObjectTypeLight) {
            TrackObjectTypeLight other = (TrackObjectTypeLight) o;
            return this.type == other.type &&
                   this.level == other.level &&
                   this.width == other.width &&
                   LogicUtil.bothNullOrEqual(this.transform, other.transform);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "{TrackObjectType[Light] type=" + this.getType().name() + ", level=" + this.getLevel() + "}";
    }

    /**
     * Stores the details of a material (in a falling block), which can later be referred to again by name
     */
    public static final class CSVEntry extends TrackObjectTypeEntry<TrackObjectTypeLight> {
        @Override
        public String getType() {
            return "LIGHT";
        }

        @Override
        public TrackObjectTypeLight getDefaultType() {
            return TrackObjectTypeLight.createDefault();
        }

        @Override
        public TrackObjectTypeLight readDetails(StringArrayBuffer buffer) throws SyntaxException {
            String typeName = buffer.next();
            LightType type = null;
            for (LightType eType : LightType.values()) {
                if (eType.name().equalsIgnoreCase(typeName)) {
                    type = eType;
                    break;
                }
            }
            if (type == null) {
                throw buffer.createSyntaxException("Unknown light type: " + typeName);
            }
            int level = buffer.nextInt();
            return TrackObjectTypeLight.create(this.width, type, level);
        }

        @Override
        public void writeDetails(StringArrayBuffer buffer, TrackObjectTypeLight objectType) {
            buffer.put(objectType.getType().name());
            buffer.putInt(objectType.getLevel());
        }
    }
}
