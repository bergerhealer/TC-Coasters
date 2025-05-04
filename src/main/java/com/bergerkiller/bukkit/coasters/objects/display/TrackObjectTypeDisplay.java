package com.bergerkiller.bukkit.coasters.objects.display;

import com.bergerkiller.bukkit.coasters.objects.TrackObjectType;
import com.bergerkiller.bukkit.coasters.particles.TrackParticle;
import com.bergerkiller.bukkit.common.wrappers.Brightness;
import org.bukkit.util.Vector;

/**
 * A type of display entity driven object. Common display entity properties are exposed.
 *
 * @param <P> Particle type
 */
public interface TrackObjectTypeDisplay<P extends TrackParticle> extends TrackObjectType<P> {
    /**
     * Gets the configured Display clip size. If 0, disabled clipping.
     *
     * @return clip Clip size, 0 if disabled (don't clip)
     */
    double getClip();

    /**
     * Creates a copy of this type with the clip size changed
     *
     * @param clip New clip to set
     * @return copy of this type with the clip changed
     */
    TrackObjectTypeDisplay<P> setClip(double clip);

    /**
     * Gets the amount of brightness this display entity has. This adds a 'glow' kind of effect.
     * If unset, uses the light level where the entity is at.
     *
     * @return Brightness
     */
    Brightness getBrightness();

    /**
     * Creates a copy of this type with brightness updated. This adds a 'glow' kind of effect.
     * If unset, uses the light level where the entity is at.
     *
     * @param brightness Brightness value
     * @return copy of this type with the brightness changed
     */
    TrackObjectTypeDisplay<P> setBrightness(Brightness brightness);

    /**
     * Gets the configured Display size
     *
     * @return Size, (1,1,1) is default
     */
    Vector getSize();

    /**
     * Creates a copy of this type with the size changed
     *
     * @param size New size to set
     * @return copy of this type with the size changed
     */
    public TrackObjectTypeDisplay<P> setSize(Vector size);
}
