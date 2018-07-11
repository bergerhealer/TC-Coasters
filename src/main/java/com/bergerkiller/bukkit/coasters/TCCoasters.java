package com.bergerkiller.bukkit.coasters;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.TCCoastersDisplay;
import com.bergerkiller.bukkit.coasters.tracks.TrackCoaster;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.world.CoasterWorldAccess;
import com.bergerkiller.bukkit.coasters.world.CoasterWorldImpl;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.rails.type.RailType;

public class TCCoasters extends JavaPlugin {
    private Task updateTask;
    private Task autosaveTask;
    private final TCCoastersListener listener = new TCCoastersListener(this);
    private final TCCoastersInteractionListener interactionListener = new TCCoastersInteractionListener(this);
    private final Map<Player, PlayerEditState> editStates = new HashMap<Player, PlayerEditState>();
    private final Map<World, CoasterWorldImpl> worlds = new HashMap<World, CoasterWorldImpl>();

    public void unloadWorld(World world) {
        CoasterWorldImpl coasterWorld = worlds.get(world);
        if (coasterWorld != null) {
            coasterWorld.unload();
            worlds.remove(world);
        }
    }

    /**
     * Gets all the coaster information stored for a particular World
     * 
     * @param world
     * @return world coaster information
     */
    public CoasterWorldAccess getCoasterWorld(World world) {
        CoasterWorldImpl coasterWorld = this.worlds.get(world);
        if (coasterWorld == null) {
            coasterWorld = new CoasterWorldImpl(this, world);
            this.worlds.put(world, coasterWorld);
            coasterWorld.load();
        }
        return coasterWorld;
    }

    /**
     * Gets all the coaster worlds on which coaster information is stored
     * 
     * @return coaster worlds
     */
    public Collection<CoasterWorldAccess> getCoasterWorlds() {
        return CommonUtil.unsafeCast(this.worlds.values());
    }

    public PlayerEditState getEditState(Player player) {
        PlayerEditState state = editStates.get(player);
        if (state == null) {
            state = new PlayerEditState(this, player);
            editStates.put(player, state);
            state.load();
        }
        return state;
    }

    public void logoutPlayer(Player player) {
        PlayerEditState state = editStates.get(player);
        if (state != null) {
            state.save();
            editStates.remove(player);
        }
    }

    public FileConfiguration getPlayerConfig(Player player) {
        File folder = new File(this.getDataFolder(), "players");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return new FileConfiguration(new File(folder, player.getUniqueId().toString() + ".yml"));
    }

    /**
     * Attempts to find the coaster by a given name
     * 
     * @param name
     * @return coaster
     */
    public TrackCoaster findCoaster(String name) {
        for (CoasterWorldAccess world : this.worlds.values()) {
            TrackCoaster coaster = world.getTracks().findCoaster(name);
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
        this.interactionListener.enable();
        this.updateTask = new Task(this) {
            @Override
            public void run() {
                for (CoasterWorldImpl coasterWorld : worlds.values()) {
                    coasterWorld.updateAll();
                }

                Iterator<PlayerEditState> iter = editStates.values().iterator();
                while (iter.hasNext()) {
                    PlayerEditState state = iter.next();
                    if (!state.getPlayer().isOnline()) {
                        iter.remove();
                    } else {
                        state.update();
                    }
                }
            }
        }.start(1, 1);

        // Autosave every 30 seconds approximately
        this.autosaveTask = new AutosaveTask(this).start(30*20, 30*20);

        // Magic!
        RailType.register(new CoasterRailType(this), false);

        // Load all coasters from csv
        for (World world : Bukkit.getWorlds()) {
            this.getCoasterWorld(world).getTracks().load();
        }

        // Update right away
        this.updateTask.run();
    }

    @Override
    public void onDisable() {
        this.listener.disable();
        this.interactionListener.disable();
        this.updateTask.stop();
        this.autosaveTask.stop();

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
        PlayerEditState state = this.getEditState(p);

        if (args.length > 0 && args[0].equals("create")) {
            sender.sendMessage("Creating a new track node at your position");
            state.createTrack();
        } else if (args.length > 0 && args[0].equals("give")) {
            sender.sendMessage("Gave you a track editor map!");
            p.getInventory().addItem(MapDisplay.createMapItem(TCCoastersDisplay.class));
        } else if (args.length > 0 && args[0].equals("save")) {
            sender.sendMessage("Saving all tracks to disk now");
            for (CoasterWorldAccess coasterWorld : this.getCoasterWorlds()) {
                coasterWorld.getTracks().save(false);
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
        } else if (args.length > 0 && args[0].equals("undo")) {
            if (state.getHistory().undo()) {
                sender.sendMessage("Your last change has been undone");
            } else {
                sender.sendMessage("No more changes to undo");
            }
        } else if (args.length > 0 && args[0].equals("redo")) {
            if (state.getHistory().redo()) {
                sender.sendMessage("Redo of previous undo is successful");
            } else {
                sender.sendMessage("No more changes to redo");
            }
        } else if (args.length > 0 && LogicUtil.contains(args[0], "orientation", "ori", "rot", "rotation", "rotate")) {
            if (state.getEditedNodes().isEmpty()) {
                sender.sendMessage("You don't have any nodes selected!");
                return true;
            }

            // Argument is specified; set it
            if (args.length >= 4) {
                // X/Y/Z specified
                double x = ParseUtil.parseDouble(args[1], 0.0);
                double y = ParseUtil.parseDouble(args[2], 0.0);
                double z = ParseUtil.parseDouble(args[3], 0.0);
                state.setOrientation(new Vector(x, y, z));
            } else if (args.length >= 2) {
                // FACE or angle specified
                BlockFace parsedFace = null;
                String input = args[1].toLowerCase(Locale.ENGLISH);
                for (BlockFace face : BlockFace.values()) {
                    if (face.name().toLowerCase(Locale.ENGLISH).equals(input)) {
                        parsedFace = face;
                        break;
                    }
                }
                if (parsedFace != null) {
                    state.setOrientation(FaceUtil.faceToVector(parsedFace));
                } else if (ParseUtil.isNumeric(input)) {
                    // Angle offset applies to the 'up' orientation
                    // Compute average forward direction vector of selected nodes
                    Vector forward = new Vector();
                    for (TrackNode node : state.getEditedNodes()) {
                        forward.add(node.getDirection());
                    }
                    double angle = ParseUtil.parseDouble(input, 0.0);
                    Quaternion q = Quaternion.fromLookDirection(forward, new Vector(0, 1, 0));
                    q.rotateZ(angle);
                    state.setOrientation(q.upVector());
                } else {
                    sender.sendMessage("Input value " + input + " not understood");
                }
            }

            // Display feedback to user
            Vector ori = state.getEditedNodes().iterator().next().getOrientation();
            String ori_str = "dx=" + Double.toString(MathUtil.round(ori.getX(), 4)) + " / " +
                             "dy=" + Double.toString(MathUtil.round(ori.getY(), 4)) + " / " +
                             "dz=" + Double.toString(MathUtil.round(ori.getZ(), 4));
            if (args.length >= 2) {
                sender.sendMessage("Track orientation set to " + ori_str);
            } else {
                sender.sendMessage("Current track orientation is " + ori_str);
            }
        } else {
            sender.sendMessage("What did you want? Try /tcc give");
        }
        return true;
    }

    public void buildAll() {
        for (CoasterWorldAccess coasterWorld : this.getCoasterWorlds()) {
            coasterWorld.getRails().rebuild();
        }
    }

    public boolean isHoldingEditTool(Player player) {
        ItemStack mainItem = HumanHand.getItemInMainHand(player);
        if (MapDisplay.getViewedDisplay(player, mainItem) instanceof TCCoastersDisplay) {
            return true;
        } else if (!ItemUtil.isEmpty(mainItem)) {
            return false;
        }

        ItemStack offItem = HumanHand.getItemInOffHand(player);
        return MapDisplay.getViewedDisplay(player, offItem) instanceof TCCoastersDisplay;
    }

    private static class AutosaveTask extends Task {

        public AutosaveTask(JavaPlugin plugin) {
            super(plugin);
        }

        @Override
        public void run() {
            for (CoasterWorldImpl coasterWorld : ((TCCoasters) this.getPlugin()).worlds.values()) {
                coasterWorld.save(true);
            }

            Iterator<PlayerEditState> iter = ((TCCoasters) this.getPlugin()).editStates.values().iterator();
            while (iter.hasNext()) {
                PlayerEditState state = iter.next();
                state.save();
                if (!state.getPlayer().isOnline()) {
                    iter.remove();
                }
            }
        }
    }

    /**
     * Escapes a name so it is suitable for saving as a file name
     * 
     * @param name to escape
     * @return escaped name
     */
    public static String escapeName(String name) {
        final char[] illegalChars = {'%', '\\', '/', ':', '"', '*', '?', '<', '>', '|'};
        for (char illegal : illegalChars) {
            int idx = 0;
            String repl = null;
            while ((idx = name.indexOf(illegal, idx)) != -1) {
                if (repl == null) {
                    repl = String.format("%%%02X", (int) illegal);
                }
                name = name.substring(0, idx) + repl + name.substring(idx + 1);
                idx += repl.length() - 1;
            }
        }
        return name;
    }

    /**
     * Undoes escaping of a file name, returning the original name it was saved as
     * 
     * @param escapedName
     * @return un-escaped escapedName
     */
    public static String unescapeName(String escapedName) {
        try {
            return URLDecoder.decode(escapedName, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return escapedName;
        }
    }
}
