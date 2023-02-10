package com.bergerkiller.bukkit.coasters.objects;

import java.util.function.Supplier;

import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.particles.TrackParticle;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.math.Matrix4x4;

/**
 * The appearance and properties of an object. This type is immutable
 * and is assigned to Track Objects to setup their properties.
 * Implementations must implement the {@link Object#hashCode()} and
 * {@link Object#equals(Object)}
 * methods for correct functioning inside maps.
 */
public interface TrackObjectType<P extends TrackParticle> {
    /**
     * Gets a title String to display in the editor gui to identify this type
     * 
     * @return title
     */
    String getTitle();

    /**
     * Gets the width of the object on the tracks, changing the attachment points
     * to the track.
     * 
     * @return width
     */
    double getWidth();

    /**
     * Creates a copy of this track object type with the width updated
     * 
     * @param width
     * @return clone of this type with width changed
     */
    TrackObjectType<P> setWidth(double width);

    /**
     * Gets the transformation matrix describing the relative position of the track object
     * 
     * @return position transform, null for no transformation (identity)
     */
    Matrix4x4 getTransform();

    /**
     * Creates a copy of this track object type with the position transform updated
     * 
     * @param transform The transform, null for no transformation (identity)
     * @return clone of this type with the position transform changed
     */
    TrackObjectType<P> setTransform(Matrix4x4 transform);

    /**
     * Generates a name to easily identify this track object type.
     * It can include any details, such as material names.
     * This name is written to csv.
     * 
     * @return name
     */
    String generateName();

    /**
     * Spawns the particle appropriate for this track object type
     * 
     * @param point Position information of where to spawn
     * @return created particle
     */
    P createParticle(TrackConnection.PointOnPath point);

    /**
     * Refreshes the particle previously spawned using {@link #createParticle(point)}.
     * 
     * @param particle The particle to update
     * @param point The (new) position of the particle
     */
    void updateParticle(P particle, TrackConnection.PointOnPath point);

    /**
     * Gets whether the same image is displayed for this type and the type specified.
     * The input type is guaranteed to be the same class as this type.
     * 
     * @param type other type
     * @return True if the same image is displayed
     */
    boolean isSameImage(TrackObjectType<?> type);

    /**
     * Draws an icon describing this track object type to a canvas
     * 
     * @param plugin The TCCoasters plugin instance
     * @param canvas The canvas to draw it on
     */
    void drawImage(TCCoasters plugin, MapCanvas canvas);

    /**
     * Opens the menu when this track object type is activated (spacebar)
     * in the editor menu.
     * 
     * @param parent Parent widget in which to display the menu
     * @param stateSupplier Supplier for the editing state of the player viewing this menu
     */
    void openMenu(MapWidget parent, Supplier<PlayerEditState> stateSupplier);

    /**
     * Modifies this track object type by setting an applicable property based on the item
     * specified. If this type does not support the item, <i>this</i> is returned.
     * 
     * @param item The item to attempt setting
     * @return the changed object type, or this type if not accepted
     */
    TrackObjectType<P> acceptItem(ItemStack item);
}
