package com.bergerkiller.bukkit.coasters.objects;

import java.util.function.Supplier;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.csv.TrackCSV.TrackObjectTypeEntry;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleLine;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection.PointOnPath;
import com.bergerkiller.bukkit.coasters.util.StringArrayBuffer;
import com.bergerkiller.bukkit.coasters.util.SyntaxException;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.LogicUtil;

/**
 * Leash displayed as a floating line
 */
public class TrackObjectTypeLeash implements TrackObjectType<TrackParticleLine> {
    private final double width;
    private final Matrix4x4 transform;

    private TrackObjectTypeLeash(double width, Matrix4x4 transform) {
        this.width = width;
        this.transform = transform;
    }

    public static TrackObjectTypeLeash create(double width) {
        return new TrackObjectTypeLeash(width, null);
    }

    public static TrackObjectTypeLeash createDefault() {
        return create(1.0);
    }

    @Override
    public String getTitle() {
        return "Leash";
    }

    @Override
    public double getWidth() {
        return this.width;
    }

    @Override
    public TrackObjectType<TrackParticleLine> setWidth(double width) {
        return new TrackObjectTypeLeash(width, this.transform);
    }

    @Override
    public Matrix4x4 getTransform() {
        return this.transform;
    }

    @Override
    public TrackObjectType<TrackParticleLine> setTransform(Matrix4x4 transform) {
        return new TrackObjectTypeLeash(this.width, transform);
    }

    @Override
    public String generateName() {
        return "leash";
    }

    @Override
    public TrackParticleLine createParticle(PointOnPath point) {
        Vector dir = point.orientation.forwardVector().multiply(0.5 * this.width);
        Vector p1 = point.position.clone().subtract(dir);
        Vector p2 = point.position.clone().add(dir);
        TrackParticleLine particle = point.getWorld().getParticles().addParticleLine(p1, p2);
        particle.setAlwaysVisible(true);
        return particle;
    }

    @Override
    public void updateParticle(TrackParticleLine particle, PointOnPath point) {
        Vector dir = point.orientation.forwardVector().multiply(0.5 * this.width);
        Vector p1 = point.position.clone().subtract(dir);
        Vector p2 = point.position.clone().add(dir);
        particle.setPositions(p1, p2);
    }

    @Override
    public boolean isSameImage(TrackObjectType<?> type) {
        return true;
    }

    @Override
    public void drawImage(TCCoasters plugin, MapCanvas canvas) {
        canvas.draw(plugin.loadTexture("com/bergerkiller/bukkit/coasters/resources/leash.png"), 0, 0);
    }

    @Override
    public void openMenu(MapWidget parent, Supplier<PlayerEditState> stateSupplier) {
    }

    @Override
    public TrackObjectType<TrackParticleLine> acceptItem(ItemStack item) {
        return this;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(this.width);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof TrackObjectTypeLeash) {
            TrackObjectTypeLeash other = (TrackObjectTypeLeash) o;
            return this.width == other.width &&
                   LogicUtil.bothNullOrEqual(this.transform, other.transform);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "{TrackObjectType[Leash]}";
    }

    /**
     * Stores the details of a material (in a falling block), which can later be referred to again by name
     */
    public static final class CSVEntry extends TrackObjectTypeEntry<TrackObjectTypeLeash> {
        @Override
        public String getType() {
            return "LEASH";
        }

        @Override
        public TrackObjectTypeLeash getDefaultType() {
            return TrackObjectTypeLeash.createDefault();
        }

        @Override
        public TrackObjectTypeLeash readDetails(StringArrayBuffer buffer) throws SyntaxException {
            return TrackObjectTypeLeash.create(this.width);
        }

        @Override
        public void writeDetails(StringArrayBuffer buffer, TrackObjectTypeLeash objectType) {
        }
    }
}
