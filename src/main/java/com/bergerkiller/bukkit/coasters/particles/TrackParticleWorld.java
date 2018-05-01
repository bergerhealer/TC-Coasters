package com.bergerkiller.bukkit.coasters.particles;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.meta.TrackEditState;

/**
 * Tracks and updates all the particle items on a single world
 */
public class TrackParticleWorld {
    private final TCCoasters plugin;
    private final World world;
    public List<TrackParticle> particles = new ArrayList<TrackParticle>();
    private List<Player> players = new ArrayList<Player>();

    public TrackParticleWorld(TCCoasters plugin, World world) {
        this.plugin = plugin;
        this.world = world;
    }

    public final TCCoasters getPlugin() {
        return this.plugin;
    }

    public final World getWorld() {
        return this.world;
    }

    public TrackParticleLine addParticleLine(Vector p1, Vector p2) {
        return addParticle(new TrackParticleLine(this, p1, p2));
    }

    public TrackParticleItem addParticleItem(Vector position) {
        return addParticle(new TrackParticleItem(this, position));
    }

    public TrackParticleArrow addParticleArrow(Vector position, Vector direction) {
        return addParticle(new TrackParticleArrow(this, position, direction));
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
        Vector pos = viewer.getEyeLocation().toVector();
        if (viewer.getWorld() != this.world || !this.plugin.isTracksVisible(viewer)) {
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
            for (TrackParticle particle : this.particles) {
                particle.updateFor(viewer, pos);
            }
        }
    }
}
