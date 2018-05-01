package com.bergerkiller.bukkit.coasters;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.map.TCCoastersDisplay;
import com.bergerkiller.bukkit.coasters.meta.TrackCoaster;
import com.bergerkiller.bukkit.coasters.meta.TrackEditState;
import com.bergerkiller.bukkit.coasters.meta.TrackNode;
import com.bergerkiller.bukkit.coasters.meta.TrackWorldStorage;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleWorld;
import com.bergerkiller.bukkit.coasters.rails.TrackRailsWorld;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.rails.type.RailType;

public class TCCoasters extends JavaPlugin {
    private Task updateTask;
    private final TCCoastersListener listener = new TCCoastersListener(this);
    private final Map<Player, TrackEditState> editStates = new HashMap<Player, TrackEditState>();
    private final Map<World, TrackParticleWorld> particleWorlds = new HashMap<World, TrackParticleWorld>();
    private final Map<World, TrackWorldStorage> trackWorlds = new HashMap<World, TrackWorldStorage>();
    private final Map<World, TrackRailsWorld> railsWorlds = new HashMap<World, TrackRailsWorld>();

    public void unloadWorld(World world) {
        {
            TrackWorldStorage tracks = getTracks(world);
            tracks.save(true);
            tracks.clear();
            trackWorlds.remove(world);
        }
        {
            TrackParticleWorld particles = getParticles(world);
            particles.removeAll();
            particleWorlds.remove(world);
        }
        {
            railsWorlds.remove(world);
        }
    }

    public TrackRailsWorld getRails(World world) {
        TrackRailsWorld railsWorld = railsWorlds.get(world);
        if (railsWorld == null) {
            railsWorld = new TrackRailsWorld();
            railsWorlds.put(world, railsWorld);
        }
        return railsWorld;
    }

    public TrackWorldStorage getTracks(World world) {
        TrackWorldStorage worldStorage = trackWorlds.get(world);
        if (worldStorage == null) {
            worldStorage = new TrackWorldStorage(this, world);
            trackWorlds.put(world, worldStorage);
        }
        return worldStorage;
    }

    public TrackParticleWorld getParticles(World world) {
        TrackParticleWorld particleWorld = particleWorlds.get(world);
        if (particleWorld == null) {
            particleWorld = new TrackParticleWorld(this, world);
            this.particleWorlds.put(world, particleWorld);
        }
        return particleWorld;
    }

    public boolean isTracksVisible(Player player) {
        TrackEditState state = editStates.get(player);
        return state != null && state.getMode() != TrackEditState.Mode.DISABLED;
    }

    public TrackEditState getEditState(Player player) {
        TrackEditState state = editStates.get(player);
        if (state == null) {
            state = new TrackEditState(this, player);
            editStates.put(player, state);
        }
        return state;
    }

    /**
     * Attempts to find the coaster by a given name
     * 
     * @param name
     * @return coaster
     */
    public TrackCoaster findCoaster(String name) {
        for (TrackWorldStorage storage : this.trackWorlds.values()) {
            TrackCoaster coaster = storage.findCoaster(name);
            if (coaster != null) {
                return coaster;
            }
        }
        return null;
    }

    /**
     * Comes up with a coaster name that does not yet exist
     * 
     * @return free coaster name
     */
    public String generateNewCoasterName() {
        for (int i = 1;;i++) {
            String name = "coaster" + i;
            if (findCoaster(name) == null) {
                return name;
            }
        }
    }

    @Override
    public void onEnable() {
        this.listener.enable();
        this.updateTask = new Task(this) {
            @Override
            public void run() {
                for (TrackWorldStorage world : trackWorlds.values()) {
                    world.updateAll();
                }
                for (TrackParticleWorld world : particleWorlds.values()) {
                    world.updateAll();
                }
                Iterator<TrackEditState> iter = editStates.values().iterator();
                while (iter.hasNext()) {
                    TrackEditState state = iter.next();
                    if (!state.getPlayer().isOnline()) {
                        iter.remove();
                    } else {
                        state.update();
                    }
                }
            }
        }.start(1, 1);

        // Magic!
        RailType.register(new CoasterRailType(this), false);

        // Load all coasters from csv
        for (World world : Bukkit.getWorlds()) {
            this.getTracks(world).load();
        }

        // Update right away
        this.updateTask.run();
    }

    @Override
    public void onDisable() {
        this.listener.disable();
        this.updateTask.stop();

        // Clean up when disabling (save dirty coasters + despawn particles)
        for (World world : Bukkit.getWorlds()) {
            unloadWorld(world);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Im lazy, ok?
        if (!sender.hasPermission("train.coasters.use") && !sender.isOp()) {
            sender.sendMessage("Sorry, no permission for this.");
            return true;
        }

        final Player p = (Player) sender;
        Vector pos = p.getEyeLocation().toVector();

        if (args.length > 0 && args[0].equals("create")) {
            sender.sendMessage("Creating a new track node at your position");
            TrackWorldStorage tracks = this.getTracks(p.getWorld());
            if (tracks.getCoasters().isEmpty()) {
                pos.add(new Vector(0.0, -2.0, 0.0));
                
                tracks.createNew(pos.clone().add(new Vector(4.0, 0.0, 0.0)));
                
                TrackCoaster coaster = tracks.getCoasters().get(0);
                tracks.addNode(coaster.getNodes().get(coaster.getNodes().size() - 1), pos);
                
            } else {
                TrackCoaster coaster = tracks.getCoasters().get(0);
                tracks.addNode(coaster.getNodes().get(coaster.getNodes().size() - 1), pos);
            }
        } else if (args.length > 0 && args[0].equals("give")) {
            sender.sendMessage("Gave you a track editor map!");
            p.getInventory().addItem(MapDisplay.createMapItem(TCCoastersDisplay.class));
        } else if (args.length > 0 && args[0].equals("save")) {
            sender.sendMessage("Saving all tracks to disk now");
            for (TrackWorldStorage world : this.trackWorlds.values()) {
                world.save(false);
            }
        } else if (args.length > 0 && args[0].equals("path")) {
            sender.sendMessage("Logging paths of all selected nodes");
            for (TrackNode node : this.getEditState(p).getEditedNodes()) {
                System.out.println("Path for: " + node.getPosition());
                for (RailPath.Point point : node.buildPath().getPoints()) {
                    System.out.println(point);
                }
            }
        } else if (args.length > 0 && args[0].equals("build")) {
            sender.sendMessage("Rebuilding tracks");
            buildAll();
        } else {
            sender.sendMessage("What did you want? Try /tcc give");
        }
        return true;
    }

    public void buildAll() {
        for (World world : Bukkit.getWorlds()) {
            TrackRailsWorld railsWorld = getRails(world);
            railsWorld.clear();
            for (TrackCoaster coaster : getTracks(world).getCoasters()) {
                for (TrackNode node : coaster.getNodes()) {
                    railsWorld.store(node);
                }
            }
        }
    }

    public boolean isHoldingEditTool(Player player) {
        return MapDisplay.getHeldDisplay(player) instanceof TCCoastersDisplay;
    }

}
