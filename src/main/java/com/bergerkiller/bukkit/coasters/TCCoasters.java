package com.bergerkiller.bukkit.coasters;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import com.bergerkiller.bukkit.common.bases.IntVector3;
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

    public void removeNodeFromEditStates(TrackNode node) {
        for (PlayerEditState state : editStates.values()) {
            state.setEditing(node, false);
        }
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

        // Log off all players
        for (Player player : new ArrayList<Player>(this.editStates.keySet())) {
            this.logoutPlayer(player);
        }

        // Clean up when disabling (save dirty coasters + despawn particles)
        for (World world : Bukkit.getWorlds()) {
            unloadWorld(world);
        }
    }

    /**
     * Checks whether a player has permission to use this plugin
     * 
     * @param sender
     * @return True if permission is granted
     */
    public boolean hasPermission(CommandSender sender) {
        // Im lazy, ok?
        return sender.hasPermission("train.coasters.use") || sender.isOp();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command is only for players");
            return true;
        }
        if (!hasPermission(sender)) {
            sender.sendMessage(ChatColor.RED + "Sorry, no permission for this.");
            return true;
        }

        final Player p = (Player) sender;
        PlayerEditState state = this.getEditState(p);

        if (args.length > 0 && args[0].equals("create")) {
            sender.sendMessage("Creating a new track node at your position");
            state.createTrack();
        } else if (args.length > 0 && args[0].equals("delete")) {
            if (state.getEditedNodes().isEmpty()) {
                sender.sendMessage("No track nodes selected, nothing has been deleted!");
            } else {
                sender.sendMessage("Deleting " + state.getEditedNodes().size() + " track nodes!");
                state.deleteTrack();
            }
        } else if (args.length > 0 && args[0].equals("give")) {
            sender.sendMessage("Gave you a track editor map!");
            p.getInventory().addItem(MapDisplay.createMapItem(TCCoastersDisplay.class));
        } else if (args.length > 0 && args[0].equals("save")) {
            sender.sendMessage("Saving all tracks to disk now");
            for (CoasterWorldAccess coasterWorld : this.getCoasterWorlds()) {
                coasterWorld.getTracks().saveForced();
            }
        } else if (args.length > 0 && LogicUtil.contains(args[0], "load", "reload")) {
            sender.sendMessage("Loading all tracks from disk now");

            // First, log out all players to guarantee their state is saved and then reset
            for (Player player : new ArrayList<Player>(this.editStates.keySet())) {
                this.logoutPlayer(player);
            }

            // Unload all coasters, saving coasters that have open changes first
            // The load command should only be used to load new coasters / reload existing ones
            for (World world : Bukkit.getWorlds()) {
                unloadWorld(world);
            }

            // Reload all coasters
            for (World world : Bukkit.getWorlds()) {
                this.getCoasterWorld(world).getTracks().load();
            }

            // For all players holding the editor map, reload it
            for (TCCoastersDisplay display : MapDisplay.getAllDisplays(TCCoastersDisplay.class)) {
                display.setRunning(false);
                display.setRunning(true);
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
        } else if (args.length > 0 && args[0].equals("copy")) {
            state.getClipboard().copy();
            if (state.getClipboard().isFilled()) {
                sender.sendMessage(state.getClipboard().getNodeCount() + " track nodes copied to the clipboard!");
            } else {
                sender.sendMessage("No tracks selected, clipboard cleared!");
            }
        } else if (args.length > 0 && args[0].equals("paste")) {
            if (state.getClipboard().isFilled()) {
                state.getClipboard().paste();
                sender.sendMessage(state.getClipboard().getNodeCount() + " track nodes pasted from the clipboard at your position!");
            } else {
                sender.sendMessage("Clipboard is empty, nothing has been pasted!");
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
                    sender.sendMessage(ChatColor.RED + "Input value " + input + " not understood");
                }
            }

            // Display feedback to user
            Vector ori = state.getLastEditedNode().getOrientation();
            String ori_str = "dx=" + Double.toString(MathUtil.round(ori.getX(), 4)) + " / " +
                             "dy=" + Double.toString(MathUtil.round(ori.getY(), 4)) + " / " +
                             "dz=" + Double.toString(MathUtil.round(ori.getZ(), 4));
            if (args.length >= 2) {
                sender.sendMessage(ChatColor.GREEN + "Track orientation set to " + ori_str);
            } else {
                sender.sendMessage(ChatColor.GREEN + "Current track orientation is " + ori_str);
            }
        } else if (args.length > 0 && LogicUtil.contains(args[0], "rail", "rails", "railblock", "railsblock")) {
            if (state.getEditedNodes().isEmpty()) {
                sender.sendMessage("You don't have any nodes selected!");
                return true;
            }

            // Argument is specified; set it
            if (args.length >= 4) {
                // X/Y/Z specified
                int x = ParseUtil.parseInt(args[1], 0);
                int y = ParseUtil.parseInt(args[2], 0);
                int z = ParseUtil.parseInt(args[3], 0);
                state.setRailBlock(new IntVector3(x, y, z));
            } else if (args.length >= 2) {
                // BlockFace translate or 'Reset'
                BlockFace parsedFace = null;
                String input = args[1].toLowerCase(Locale.ENGLISH);
                for (BlockFace face : BlockFace.values()) {
                    if (face.name().toLowerCase(Locale.ENGLISH).equals(input)) {
                        parsedFace = face;
                        break;
                    }
                }
                if (parsedFace != null) {
                    sender.sendMessage(ChatColor.YELLOW + "Rail block moved one block " + parsedFace);
                    IntVector3 old = state.getLastEditedNode().getRailBlock(true);
                    state.setRailBlock(old.add(parsedFace));
                } else if (input.equals("reset")) {
                    sender.sendMessage(ChatColor.YELLOW + "Rail block position reset");
                    state.resetRailsBlocks();
                } else {
                    sender.sendMessage(ChatColor.RED + "Input value " + input + " not understood");
                }
            }

            // Display feedback to user
            IntVector3 rail = state.getLastEditedNode().getRailBlock(true);
            String rail_str = "x=" + rail.x + " / " +
                              "y=" + rail.y + " / " +
                              "z=" + rail.z;
            if (args.length >= 2) {
                sender.sendMessage(ChatColor.GREEN + "Track rail block set to " + rail_str);
            } else {
                sender.sendMessage(ChatColor.GREEN + "Current track rail block is " + rail_str);
            }
        } else {
            sender.sendMessage(ChatColor.RED + "What did you want? Try /tcc give");
        }
        return true;
    }

    public void buildAll() {
        for (CoasterWorldAccess coasterWorld : this.getCoasterWorlds()) {
            coasterWorld.getRails().rebuild();
        }
    }

    public boolean isHoldingEditTool(Player player) {
        if (!this.hasPermission(player)) {
            return false;
        }

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
                coasterWorld.saveChanges();
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
