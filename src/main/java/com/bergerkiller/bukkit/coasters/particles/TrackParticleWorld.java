package com.bergerkiller.bukkit.coasters.particles;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.world.CoasterWorldAccess;
import com.bergerkiller.bukkit.common.math.Quaternion;

/**
 * Tracks and updates all the particle items on a single world
 */
public class TrackParticleWorld extends CoasterWorldAccess.Component {
    public List<TrackParticle> particles = new ArrayList<TrackParticle>();
    private List<Player> players = new ArrayList<Player>();

    public TrackParticleWorld(CoasterWorldAccess world) {
        super(world);
    }

    public TrackParticleLine addParticleLine(Vector p1, Vector p2) {
        return addParticle(new TrackParticleLine(this, p1, p2));
    }

    public TrackParticleItem addParticleItem(Vector position) {
        return addParticle(new TrackParticleItem(this, position));
    }

    public TrackParticleArrow addParticleArrow(Vector position, Vector direction, Vector up) {
        return addParticle(new TrackParticleArrow(this, position, Quaternion.fromLookDirection(direction, up)));
    }

    public TrackParticleArrow addParticleArrow(Vector position, Quaternion orientation) {
        return addParticle(new TrackParticleArrow(this, position, orientation));
    }

    protected <T extends TrackParticle> T addParticle(T particle) {
        this.particles.add(particle);
        return particle;
    }

    public void removeParticle(TrackParticle particle) {
        this.particles.remove(particle);
        particle.makeHiddenForAll();
    }

    public void removeAll() {
        for (TrackParticle particle : this.particles) {
            particle.makeHiddenForAll();
        }
        this.particles.clear();
    }

    public void updateAll() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            this.update(viewer);
        }
        for (TrackParticle particle : this.particles) {
            particle.updateAppearance();
        }
    }

    public void update(Player viewer) {
        if (viewer.getWorld() != this.getWorld() || this.getPlugin().getEditState(viewer).getMode() == PlayerEditState.Mode.DISABLED) {
            if (this.players.contains(viewer)) {
                this.players.remove(viewer);
                for (TrackParticle particle : this.particles) {
                    particle.updateFor(viewer, null);
                }
            }
        } else {
            if (!this.players.contains(viewer)) {
                this.players.add(viewer);
            }

            Vector pos = viewer.getEyeLocation().toVector();
            for (TrackParticle particle : this.particles) {
                particle.updateFor(viewer, pos);
            }
        }
    }
}
