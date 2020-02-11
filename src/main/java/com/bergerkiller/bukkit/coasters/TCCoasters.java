package com.bergerkiller.bukkit.coasters;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;

import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.TCCoastersDisplay;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.events.CoasterCopyEvent;
import com.bergerkiller.bukkit.coasters.events.CoasterImportEvent;
import com.bergerkiller.bukkit.coasters.signs.SignActionTrackAnimate;
import com.bergerkiller.bukkit.coasters.tracks.TrackCoaster;
import com.bergerkiller.bukkit.coasters.tracks.TrackCoaster.CoasterLoadException;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeAnimationState;
import com.bergerkiller.bukkit.coasters.tracks.csv.TrackCoasterCSVWriter;
import com.bergerkiller.bukkit.coasters.util.PlayerOrigin;
import com.bergerkiller.bukkit.coasters.world.CoasterWorldAccess;
import com.bergerkiller.bukkit.coasters.world.CoasterWorldImpl;
import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.Hastebin;
import com.bergerkiller.bukkit.common.Hastebin.DownloadResult;
import com.bergerkiller.bukkit.common.Hastebin.UploadResult;
import com.bergerkiller.bukkit.common.PluginBase;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.common.localization.LocalizationEnum;
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
import com.bergerkiller.bukkit.tc.signactions.SignAction;

public class TCCoasters extends PluginBase {
    private static final double DEFAULT_SMOOTHNESS = 10000.0;
    private static final boolean DEFAULT_GLOWING_SELECTIONS = true;
    private static final int DEFAULT_PARTICLE_VIEW_RANGE = 64;
    private static final int DEFAULT_MAXIMUM_PARTICLE_COUNT = 5000;
    private static final boolean DEFAULT_PLOTSQUARED_ENABLED = false;
    private Task updateTask;
    private Task autosaveTask;
    private final CoasterRailType coasterRailType = new CoasterRailType(this);
    private final SignActionTrackAnimate trackAnimateAction = new SignActionTrackAnimate();
    private final Hastebin hastebin = new Hastebin(this);
    private final TCCoastersListener listener = new TCCoastersListener(this);
    private final TCCoastersInteractionListener interactionListener = new TCCoastersInteractionListener(this);
    private final Map<Player, PlayerEditState> editStates = new HashMap<Player, PlayerEditState>();
    private final Map<World, CoasterWorldImpl> worlds = new HashMap<World, CoasterWorldImpl>();
    private final Map<Player, Integer> noPermDebounceMap = new HashMap<Player, Integer>();
    private double smoothness = DEFAULT_SMOOTHNESS;
    private boolean glowingSelections = DEFAULT_GLOWING_SELECTIONS;
    private int particleViewRange = DEFAULT_PARTICLE_VIEW_RANGE;
    private int maximumParticleCount = DEFAULT_MAXIMUM_PARTICLE_COUNT;
    private boolean plotSquaredEnabled = DEFAULT_PLOTSQUARED_ENABLED;
    private Listener plotSquaredHandler = null;

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

    public synchronized void forAllEditStates(Consumer<PlayerEditState> function) {
        for (PlayerEditState editState : editStates.values()) {
            function.accept(editState);
        }
    }

    public synchronized List<Player> getPlayersWithEditStates() {
        return new ArrayList<Player>(editStates.keySet());
    }

    public synchronized PlayerEditState getEditState(Player player) {
        PlayerEditState state = editStates.get(player);
        if (state == null) {
            state = new PlayerEditState(this, player);
            editStates.put(player, state);
            state.load();
        }
        return state;
    }

    public synchronized void logoutPlayer(Player player) {
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

    /**
     * Gets the smoothness of the tracks created
     * 
     * @return smoothness, higher values is smoother
     */
    public double getSmoothness() {
        return this.smoothness;
    }

    /**
     * Gets whether selections should be glowing
     *
     * @return true if selections should glow
     */
    public boolean getGlowingSelections() {
        return this.glowingSelections;
    }

    /**
     * Whether PlotSquared is enabled in the configuration and the plugin is loaded
     * 
     * @return True if plot squared is enabled
     */
    public boolean isPlotSquaredEnabled() {
        return this.plotSquaredEnabled && this.plotSquaredHandler != null;
    }

    /**
     * Gets the particle view range. Players can see particles when
     * below this distance away from a particle.
     * 
     * @return particle view range
     */
    public int getParticleViewRange() {
        return this.particleViewRange;
    }

    /**
     * Gets the maximum number of particles that can be visible to a player at once
     * 
     * @return maximum particle count
     */
    public int getMaximumParticleCount() {
        return this.maximumParticleCount;
    }

    @Override
    public void enable() {
        this.listener.enable();
        this.interactionListener.enable();
        this.updateTask = new Task(this) {
            @Override
            public void run() {
                for (CoasterWorldImpl coasterWorld : worlds.values()) {
                    coasterWorld.updateAll();
                }

                // Clean this up
                if (!noPermDebounceMap.isEmpty()) {
                    int time = CommonUtil.getServerTicks();
                    Iterator<Integer> iter = noPermDebounceMap.values().iterator();
                    while (iter.hasNext()) {
                        if (iter.next().intValue() <= time) {
                            iter.remove();
                        }
                    }
                }

                synchronized (TCCoasters.this) {
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
            }
        }.start(1, 1);

        // Load configuration
        FileConfiguration config = new FileConfiguration(this);
        config.load();
        config.setHeader("smoothness", "\nSpecifies how smoothly trains drive over the tracks, especially in curves");
        config.addHeader("smoothness", "Very high values may cause performance issues");
        this.smoothness = config.get("smoothness", DEFAULT_SMOOTHNESS);
        config.setHeader("glowing-selections", "\nSpecifies if selected nodes should be glowing.");
        config.addHeader("glowing-selections", "Glowing nodes are visible through walls.");
        this.glowingSelections = config.get("glowing-selections", DEFAULT_GLOWING_SELECTIONS);
        config.setHeader("hastebinServer", "\nThe hastebin server which is used to upload coaster tracks");
        config.addHeader("hastebinServer", "This will be used when using the /tcc export command");
        this.hastebin.setServer(config.get("hastebinServer", "https://paste.traincarts.net"));
        config.setHeader("particleViewRange", "\nMaximum block distance away from particles where players can see them");
        config.addHeader("particleViewRange", "Lowering this range may help reduce lag in the client if a lot of particles are displayed");
        this.particleViewRange = config.get("particleViewRange", DEFAULT_PARTICLE_VIEW_RANGE);
        config.setHeader("maximumParticleCount", "\nMaximum number of particles that can be visible to a player at one time");
        config.addHeader("maximumParticleCount", "When more particles are visible than this, the player sees a warning, and some particles are hidden");
        config.addHeader("maximumParticleCount", "This can be used to prevent a total lag-out of the client when accidentally creating a lot of track");
        this.maximumParticleCount = config.get("maximumParticleCount", DEFAULT_MAXIMUM_PARTICLE_COUNT);
        config.setHeader("plotSquaredEnabled", "\nWhether track editing permission integration with PlotSquared is enabled");
        config.addHeader("plotSquaredEnabled", "Players will be unable to edit coasters outside of their personal plot");
        this.plotSquaredEnabled = config.get("plotSquaredEnabled", DEFAULT_PLOTSQUARED_ENABLED);
        config.save();

        // Autosave every 30 seconds approximately
        this.autosaveTask = new AutosaveTask(this).start(30*20, 30*20);

        // Magic!
        RailType.register(this.coasterRailType, false);

        // More magic!
        SignAction.register(this.trackAnimateAction);

        // Load all coasters from csv
        for (World world : Bukkit.getWorlds()) {
            this.getCoasterWorld(world).getTracks().load();
        }

        // Update right away
        this.updateTask.run();
    }

    @Override
    public void disable() {
        this.listener.disable();
        this.interactionListener.disable();
        this.updateTask.stop();
        this.autosaveTask.stop();

        // Log off all players
        for (Player player : getPlayersWithEditStates()) {
            this.logoutPlayer(player);
        }

        // Unregister ourselves
        SignAction.unregister(this.trackAnimateAction);
        RailType.unregister(this.coasterRailType);

        // Clean up when disabling (save dirty coasters + despawn particles)
        for (World world : Bukkit.getWorlds()) {
            unloadWorld(world);
        }
    }

    @Override
    public void updateDependency(Plugin plugin, String pluginName, boolean enabled) {
        if (pluginName.equals("PlotSquared")) {
            boolean available = enabled && this.plotSquaredEnabled;
            if (available != (this.plotSquaredHandler != null)) {
                if (available) {
                    this.plotSquaredHandler = new PlotSquaredHandler(this);
                    this.register(this.plotSquaredHandler);
                    this.log(Level.INFO, "PlotSquared support enabled!");
                } else {
                    CommonUtil.unregisterListener(this.plotSquaredHandler);
                    this.plotSquaredHandler = null;
                    this.log(Level.INFO, "PlotSquared support disabled!");
                }
            }
        }
    }

    @Override
    public void permissions() {
        this.loadPermissions(TCCoastersPermissions.class);
    }

    @Override
    public void localization() {
        this.loadLocales(TCCoastersLocalization.class);
    }

    public boolean globalCommand(CommandSender sender, String label, String[] args) {
        if (args.length > 0 && LogicUtil.contains(args[0], "load", "reload")) {
            sender.sendMessage("Loading all tracks from disk now");

            // First, log out all players to guarantee their state is saved and then reset
            for (Player player : getPlayersWithEditStates()) {
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
        } else if (args.length > 0 && args[0].equals("save")) {
            sender.sendMessage("Saving all tracks to disk now");
            for (CoasterWorldAccess coasterWorld : this.getCoasterWorlds()) {
                coasterWorld.getTracks().saveForced();
            }
        } else if (args.length > 0 && args[0].equals("build")) {
            sender.sendMessage("Rebuilding tracks");
            buildAll();
        } else if (args.length > 0 && args[0].equals("smoothness")) {
            if (args.length == 1) {
                sender.sendMessage("Smoothness is currently set to " + this.smoothness);
                return true;
            }

            this.smoothness = ParseUtil.parseDouble(args[1], DEFAULT_SMOOTHNESS);

            // Update config.yml
            {
                FileConfiguration config = new FileConfiguration(this);
                config.load();
                config.set("smoothness", this.smoothness);
                config.save();
            }

            sender.sendMessage("Set smoothness to " + this.smoothness + ", rebuilding tracks");
            buildAll();
        } else if (args.length > 0 && args[0].equals("glow")) {
            if (args.length == 1) {
                sender.sendMessage("Glowing selections are currently " + (this.glowingSelections ? "enabled" : "disabled"));
                return true;
            }

            this.glowingSelections = ParseUtil.parseBool(args[1], DEFAULT_GLOWING_SELECTIONS);

            // Update config.yml
            {
                FileConfiguration config = new FileConfiguration(this);
                config.load();
                config.set("glowing-selections", this.glowingSelections);
                config.save();
            }

            sender.sendMessage((this.glowingSelections ? "Enabled" : "Disabled") + " glowing selections");
            for (Player player : Bukkit.getOnlinePlayers()) {
                PlayerEditState editState = getEditState(player);
                if (editState != null) {
                    for (TrackNode node : editState.getEditedNodes()) {
                        node.onStateUpdated(player);
                    }
                }
            }
        } else {
            return false;
        }
        return true;
    }

    @Override
    public boolean command(CommandSender sender, String command, String[] args) {
        if (!hasUsePermission(sender)) {
            TCCoastersLocalization.NO_PERMISSION.message(sender);
            return true;
        }
        if (globalCommand(sender, command, args)) {
            return true;
        } else if (!(sender instanceof Player)) {
            sender.sendMessage("This command is only for players");
            return true;
        }

        final Player p = (Player) sender;
        PlayerEditState state = this.getEditState(p);

        if (args.length > 0 && args[0].equals("create")) {
            try {
                state.createTrack();
                sender.sendMessage("Created a new track node at your position");
            } catch (ChangeCancelledException e) {
                sender.sendMessage(ChatColor.RED + "A new track node could not be created here");
            }
        } else if (args.length > 0 && args[0].equals("delete")) {
            if (!state.hasEditedNodes()) {
                sender.sendMessage("No track nodes selected, nothing has been deleted!");
            } else {
                try {
                    int numDeleted = state.getEditedNodes().size();
                    state.deleteTrack();
                    sender.sendMessage("Deleted " + numDeleted + " track nodes!");
                } catch (ChangeCancelledException e) {
                    sender.sendMessage(ChatColor.RED + "Failed to delete some of the track nodes!");
                }
            }
        } else if (args.length > 0 && args[0].equals("give")) {
            sender.sendMessage("Gave you a track editor map!");
            ItemStack item = MapDisplay.createMapItem(TCCoastersDisplay.class);
            ItemUtil.setDisplayName(item, "Track Editor");
            CommonTagCompound tag = ItemUtil.getMetaTag(item, true);
            CommonTagCompound display = tag.createCompound("display");
            display.putValue("MapColor", 0x0000FF);
            p.getInventory().addItem(item);
        } else if (args.length > 0 && args[0].equals("path")) {
            sender.sendMessage("Logging paths of all selected nodes");
            for (TrackNode node : this.getEditState(p).getEditedNodes()) {
                System.out.println("Path for: " + node.getPosition());
                for (RailPath.Point point : node.buildPath().getPoints()) {
                    System.out.println(point);
                }
            }
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
        } else if (args.length > 0 && args[0].equals("cut")) {
            state.getClipboard().copy();
            if (state.getClipboard().isFilled()) {
                try {
                    state.deleteTrack();
                    sender.sendMessage(state.getClipboard().getNodeCount() + " track nodes cut from the world and saved to the clipboard!");
                } catch (ChangeCancelledException e) {
                    sender.sendMessage(ChatColor.RED + "Some track nodes could not be cut from the world!");
                }
            } else {
                sender.sendMessage("No tracks selected, clipboard cleared!");
            }
        } else if (args.length > 0 && args[0].equals("paste")) {
            if (state.getClipboard().isFilled()) {
                try {
                    state.getClipboard().paste();
                    sender.sendMessage(state.getClipboard().getNodeCount() + " track nodes pasted from the clipboard at your position!");
                } catch (ChangeCancelledException e) {
                    sender.sendMessage(ChatColor.RED + "The track nodes could not be pasted here");
                }
            } else {
                sender.sendMessage("Clipboard is empty, nothing has been pasted!");
            }
        } else if (args.length > 0 && args[0].equals("lock")) {
            if (!TCCoastersPermissions.LOCK.has(sender)) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to lock coasters");
                return true;
            } else {
                for (TrackCoaster coaster : state.getEditedCoasters()) {
                    coaster.setLocked(true);
                }
                sender.sendMessage(ChatColor.YELLOW + "Selected coasters have been " + ChatColor.RED + "LOCKED");
            }
        } else if (args.length > 0 && args[0].equals("unlock")) {
            if (!TCCoastersPermissions.LOCK.has(sender)) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to unlock coasters");
            } else {
                for (TrackCoaster coaster : state.getEditedCoasters()) {
                    coaster.setLocked(false);
                }
                sender.sendMessage(ChatColor.YELLOW + "Selected coasters have been " + ChatColor.GREEN + "UNLOCKED");
            }
        } else if (args.length > 0 && args[0].equals("import")) {
            TCCoastersPermissions.IMPORT.handle(sender);
            if (args.length == 1) {
                sender.sendMessage(ChatColor.RED + "Please specify the URL to a Hastebin-hosted paste to download from");
                return true;
            }
            this.hastebin.download(args[1]).thenAccept(new Consumer<Hastebin.DownloadResult>() {
                @Override
                public void accept(DownloadResult t) {
                    if (!t.success()) {
                        sender.sendMessage(ChatColor.RED + "Failed to import coaster: " + t.error());
                        return;
                    }
                    TrackCoaster coaster = state.getTracks().createNewEmpty(generateNewCoasterName());
                    try {
                        coaster.loadFromStream(t.contentInputStream(), PlayerOrigin.getForPlayer(state.getPlayer()));
                        if (coaster.getNodes().isEmpty()) {
                            sender.sendMessage(ChatColor.RED + "Failed to decode any coaster nodes!");
                            coaster.remove();
                            return;
                        }
                    } catch (CoasterLoadException ex) {
                        sender.sendMessage(ChatColor.RED + ex.getMessage());
                        if (coaster.getNodes().isEmpty()) {
                            coaster.remove();
                            return;
                        }
                    }

                    // Handle event
                    if (CommonUtil.callEvent(new CoasterImportEvent(p, coaster)).isCancelled()) {
                        sender.sendMessage(ChatColor.RED + "Coaster could not be imported here!");
                        coaster.remove();
                        return;
                    }
                    if (coaster.getNodes().isEmpty()) {
                        sender.sendMessage(ChatColor.RED + "None of the nodes could be imported here!");
                        coaster.remove();
                        return;
                    }

                    sender.sendMessage(ChatColor.GREEN + "Coaster with " + coaster.getNodes().size() + " nodes imported!");
                }
            });
        } else if (args.length > 0 && args[0].equals("export")) {
            TCCoastersPermissions.EXPORT.handle(sender);
            if (!state.hasEditedNodes()) {
                sender.sendMessage(ChatColor.RED + "No track nodes selected, nothing has been exported!");
                return true;
            }

            HashSet<TrackNode> exportedNodes = new HashSet<TrackNode>(state.getEditedNodes());
            CoasterCopyEvent event = CommonUtil.callEvent(new CoasterCopyEvent(p, exportedNodes, true));
            if (event.isCancelled() || exportedNodes.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "These nodes could not be exported!");
            }

            boolean nolimits2Format = (args.length > 1 && LogicUtil.containsIgnoreCase(args[1], "nl2", "nolimits", "nolimits2"));
            String content;
            try {
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                try (TrackCoasterCSVWriter writer = new TrackCoasterCSVWriter(stream, nolimits2Format ? '\t' : ',')) {
                    if (nolimits2Format) {
                        // NoLimits2 format
                        writer.writeAllNoLimits2(exportedNodes);
                    } else {
                        // Normal TCC format
                        writer.setWriteLinksToForeignNodes(false);
                        writer.write(PlayerOrigin.getForPlayer(state.getPlayer()));
                        writer.writeAll(exportedNodes);
                    }
                }
                content = stream.toString("UTF-8");
            } catch (Throwable t) {
                t.printStackTrace();
                sender.sendMessage(ChatColor.RED + "Failed to export: " + t.getMessage());
                return true;
            }
            this.hastebin.upload(content).thenAccept(new Consumer<Hastebin.UploadResult>() {
                @Override
                public void accept(UploadResult t) {
                    if (t.success()) {
                        sender.sendMessage(ChatColor.GREEN + "Tracks exported: " + ChatColor.WHITE + ChatColor.UNDERLINE + t.url());
                    } else {
                        sender.sendMessage(ChatColor.RED + "Failed to export: " + t.error());
                    }
                }
            });
            return true;
        } else if (args.length > 0 && LogicUtil.contains(args[0], "posx", "posy", "posz", "setx", "sety", "setz")) {
            if (args.length == 1) {
                sender.sendMessage(ChatColor.RED + "/tcc " + args[0] + " (add) [value]");
                sender.sendMessage(ChatColor.RED + "/tcc " + args[0] + " average");
                return true;
            }

            boolean add = LogicUtil.contains(args[1], "add", "rel", "relative", "move", "off", "offset");
            boolean avg = LogicUtil.contains(args[1], "avg", "average");
            if (add && args.length == 2) {
                sender.sendMessage(ChatColor.RED + "/tcc " + args[0] + " add [value]");
                return true;
            }

            state.deselectLockedNodes();
            if (state.getEditedNodes().isEmpty()) {
                sender.sendMessage("You don't have any nodes selected!");
                return true;
            }

            boolean modify_x = LogicUtil.contains(args[0], "posx", "setx");
            boolean modify_y = LogicUtil.contains(args[0], "posy", "sety");
            boolean modify_z = LogicUtil.contains(args[0], "posz", "setz");

            final double value;
            if (avg) {
                // Compute average value for the selected axis
                double averageValue = 0.0;
                for (TrackNode node : state.getEditedNodes()) {
                    if (modify_x) {
                        averageValue += node.getPosition().getX();
                    } else if (modify_y) {
                        averageValue += node.getPosition().getY();
                    } else if (modify_z) {
                        averageValue += node.getPosition().getZ();
                    }
                }
                value = averageValue / state.getEditedNodes().size();
            } else {
                // User input
                value = ParseUtil.parseDouble(add ? args[2] : args[1], Double.NaN);
                if (Double.isNaN(value)) {
                    sender.sendMessage(ChatColor.RED + "Invalid value specified!");
                    return true;
                }
            }

            // Perform the actual operation on the x,y or z coordinates of the selected nodes
            try {
                if (add) {
                    if (modify_x) {
                        state.transformPosition(pos -> pos.setX(pos.getX() + value));
                        sender.sendMessage(ChatColor.GREEN + "Added "+ value + " to the X-Position of all selected nodes!");
                    } else if (modify_y) {
                        state.transformPosition(pos -> pos.setY(pos.getY() + value));
                        sender.sendMessage(ChatColor.GREEN + "Added "+ value + " to the Y-Position of all selected nodes!");
                    } else if (modify_z) {
                        state.transformPosition(pos -> pos.setZ(pos.getZ() + value));
                        sender.sendMessage(ChatColor.GREEN + "Added "+ value + " to the Z-Position of all selected nodes!");
                    }
                } else {
                    if (modify_x) {
                        state.transformPosition(pos -> pos.setX(value));
                        sender.sendMessage(ChatColor.GREEN + "The X-Position of all the selected nodes has been set to " + value + "!");
                    } else if (modify_y) {
                        state.transformPosition(pos -> pos.setY(value));
                        sender.sendMessage(ChatColor.GREEN + "The Y-Position of all the selected nodes has been set to " + value + "!");
                    } else if (modify_z) {
                        state.transformPosition(pos -> pos.setZ(value));
                        sender.sendMessage(ChatColor.GREEN + "The Z-Position of all the selected nodes has been set to " + value + "!");
                    }
                }
            } catch (ChangeCancelledException ex) {
                sender.sendMessage(ChatColor.RED + "The position of one or more nodes could not be changed");
                return true;
            }
        } else if (args.length > 0 && LogicUtil.contains(args[0], "orientation", "ori", "rot", "rotation", "rotate")) {
            state.deselectLockedNodes();
            if (state.getEditedNodes().isEmpty()) {
                sender.sendMessage("You don't have any nodes selected!");
                return true;
            }

            // Argument is specified; set it
            try {
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
            } catch (ChangeCancelledException ex) {
                sender.sendMessage(ChatColor.RED + "The orienation of this node could not be changed");
                return true;
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
        } else if (args.length > 0 && args[0].equalsIgnoreCase("animation")) {
            state.deselectLockedNodes();
            if (state.getEditedNodes().isEmpty()) {
                sender.sendMessage("You don't have any nodes selected!");
                return true;
            }
            if (args.length >= 3 && LogicUtil.containsIgnoreCase(args[1], "add", "create", "new")) {
                String animName = args[2];
                for (TrackNode node : state.getEditedNodes()) {
                    node.saveAnimationState(animName);
                }
                sender.sendMessage("Animation '" + animName + "' added to " + state.getEditedNodes().size() + " nodes!");
            } else if (args.length >= 3 && LogicUtil.containsIgnoreCase(args[1], "remove", "delete")) {
                String animName = args[2];
                int removedCount = 0;
                for (TrackNode node : state.getEditedNodes()) {
                    if (node.removeAnimationState(animName)) {
                        removedCount++;
                    }
                }
                if (removedCount == 0) {
                    sender.sendMessage(ChatColor.RED + "Animation '" + animName + "' was not added to the selected nodes!");
                } else {
                    sender.sendMessage("Animation '" + animName + "' removed for " + removedCount + " nodes!");
                }
            } else if (args.length >= 2 && LogicUtil.containsIgnoreCase(args[1], "clear")) {
                for (TrackNode node : state.getEditedNodes()) {
                    for (TrackNodeAnimationState anim_state : new ArrayList<>(node.getAnimationStates())) {
                        node.removeAnimationState(anim_state.name);
                    }
                }
                sender.sendMessage("Animations cleared for " + state.getEditedNodes().size() + " nodes!");
            } else if (args.length >= 3 && LogicUtil.containsIgnoreCase(args[1], "play", "run")) {
                String animName = args[2];
                double duration = 0.0;
                if (args.length >= 4) {
                    duration = ParseUtil.parseDouble(args[3], 0.0);
                }
                int playingCount = 0;
                for (TrackNode node : state.getEditedNodes()) {
                    if (node.playAnimation(animName, duration)) {
                        playingCount++;
                    }
                }
                if (playingCount == 0) {
                    sender.sendMessage(ChatColor.RED + "Animation '" + animName + "' was not added to the selected nodes!");
                } else {
                    sender.sendMessage("Animation '" + animName + "' is now playing for " + playingCount + " nodes!");
                }
            } else if (args.length >= 3 && LogicUtil.containsIgnoreCase(args[1], "select", "edit")) {
                String animName = args[2];
                state.setSelectedAnimation(animName);
                sender.sendMessage(ChatColor.GREEN + "Selected track animation '" + animName + "'!");
                if (state.getSelectedAnimationNodes().isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "None of your selected nodes contain this animation!");
                }
            } else {
                sender.sendMessage(ChatColor.RED + "Invalid number of arguments specified!");
                sender.sendMessage(ChatColor.RED + "/tcc animation [add/remove/select] [name]");
                sender.sendMessage(ChatColor.RED + "/tcc animation play [name] (duration)");
                sender.sendMessage(ChatColor.RED + "/tcc animation clear");
            }
        } else if (args.length > 0 && LogicUtil.contains(args[0], "rail", "rails", "railblock", "railsblock")) {
            state.deselectLockedNodes();
            if (state.getEditedNodes().isEmpty()) {
                sender.sendMessage("You don't have any nodes selected!");
                return true;
            }

            // Argument is specified; set it
            try {
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
                        IntVector3 old = state.getLastEditedNode().getRailBlock(true);
                        state.setRailBlock(old.add(parsedFace));
                        sender.sendMessage(ChatColor.YELLOW + "Rail block moved one block " + parsedFace);
                    } else if (input.equals("reset")) {
                        state.resetRailsBlocks();
                        sender.sendMessage(ChatColor.YELLOW + "Rail block position reset");
                    } else {
                        sender.sendMessage(ChatColor.RED + "Input value " + input + " not understood");
                    }
                }
            } catch (ChangeCancelledException ex) {
                sender.sendMessage(ChatColor.RED + "The rail block of this node could not be changed");
                return true;
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

    /**
     * Sends a 'no permission' message to a player, which includes debouncing to prevent spam
     * 
     * @param player
     * @param message
     */
    public void sendNoPermissionMessage(Player player, LocalizationEnum message) {
        Integer prev = noPermDebounceMap.put(player, Integer.valueOf(CommonUtil.getServerTicks() + 20));
        if (prev == null) {
            message.message(player);
        }
    }

    public boolean hasUsePermission(CommandSender sender) {
        if (!TCCoastersPermissions.USE.has(sender)) {
            if (!this.isPlotSquaredEnabled() || !TCCoastersPermissions.PLOTSQUARED_USE.has(sender)) {
                return false;
            }
        }
        return true;
    }

    public boolean isHoldingEditTool(Player player) {
        if (!hasUsePermission(player)) {
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

            TCCoasters plugin = (TCCoasters) this.getPlugin();
            synchronized (plugin) {
                Iterator<PlayerEditState> iter = plugin.editStates.values().iterator();
                while (iter.hasNext()) {
                    PlayerEditState state = iter.next();
                    state.save();
                    if (!state.getPlayer().isOnline()) {
                        iter.remove();
                    }
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

    @Override
    public int getMinimumLibVersion() {
        return Common.VERSION;
    }

}
