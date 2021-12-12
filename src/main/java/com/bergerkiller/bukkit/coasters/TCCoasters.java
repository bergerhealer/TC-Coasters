package com.bergerkiller.bukkit.coasters;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.bergerkiller.bukkit.coasters.commands.TCCoastersCommands;
import com.bergerkiller.bukkit.coasters.csv.TrackCSV;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.TCCoastersDisplay;
import com.bergerkiller.bukkit.coasters.objects.TrackObjectTypeLight;
import com.bergerkiller.bukkit.coasters.signs.SignActionTrackAnimate;
import com.bergerkiller.bukkit.coasters.tracks.TrackCoaster;
import com.bergerkiller.bukkit.coasters.util.FastIdentityHashMap;
import com.bergerkiller.bukkit.coasters.util.QueuedTask;
import com.bergerkiller.bukkit.coasters.world.CoasterWorld;
import com.bergerkiller.bukkit.coasters.world.CoasterWorldImpl;
import com.bergerkiller.bukkit.common.AsyncTask;
import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.Hastebin;
import com.bergerkiller.bukkit.common.PluginBase;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.common.io.ByteArrayIOStream;
import com.bergerkiller.bukkit.common.localization.LocalizationEnum;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.map.MapResourcePack;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.signactions.SignAction;

public class TCCoasters extends PluginBase {
    private static final double DEFAULT_SMOOTHNESS = 10000.0;
    private static final boolean DEFAULT_GLOWING_SELECTIONS = true;
    private static final int DEFAULT_PARTICLE_VIEW_RANGE = 64;
    private static final int DEFAULT_MAXIMUM_PARTICLE_COUNT = 5000;
    private static final boolean DEFAULT_PLOTSQUARED_ENABLED = false;
    private static final boolean DEFAULT_LIGHTAPI_ENABLED = true;
    private Task worldUpdateTask, runQueuedTasksTask, updatePlayerEditStatesTask, autosaveTask;
    private TCCoastersCommands commands;
    private final CoasterRailType coasterRailType = new CoasterRailType(this);
    private final SignActionTrackAnimate trackAnimateAction = new SignActionTrackAnimate();
    private final Hastebin hastebin = new Hastebin(this);
    private final TCCoastersListener listener = new TCCoastersListener(this);
    private final TCCoastersInteractionListener interactionListener = new TCCoastersInteractionListener(this);
    private final Map<Player, PlayerEditState> editStates = new HashMap<Player, PlayerEditState>();
    private final FastIdentityHashMap<World, CoasterWorldImpl> worlds = new FastIdentityHashMap<World, CoasterWorldImpl>();
    private final QueuedTask<Player> noPermDebounce = QueuedTask.create(20, QueuedTask.Precondition.none(), player -> {});
    private double smoothness = DEFAULT_SMOOTHNESS;
    private boolean glowingSelections = DEFAULT_GLOWING_SELECTIONS;
    private int particleViewRange = DEFAULT_PARTICLE_VIEW_RANGE;
    private int maximumParticleCount = DEFAULT_MAXIMUM_PARTICLE_COUNT;
    private boolean plotSquaredEnabled = DEFAULT_PLOTSQUARED_ENABLED;
    private boolean lightAPIEnabled = DEFAULT_LIGHTAPI_ENABLED;
    private boolean lightAPIFound = false;
    private Listener plotSquaredHandler = null;
    private File importFolder, exportFolder;

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
    public CoasterWorld getCoasterWorld(World world) {
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
    public Collection<CoasterWorld> getCoasterWorlds() {
        return CommonUtil.unsafeCast(this.worlds.values());
    }

    /**
     * Gets the resource pack used when drawing icons and 3d models
     * 
     * @return resource pack
     */
    public MapResourcePack getResourcePack() {
        return TCConfig.resourcePack; // return TrainCarts' RP
    }

    /**
     * Gets the hastebin configuration
     *
     * @return hastebin
     */
    public Hastebin getHastebin() {
        return this.hastebin;
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
        for (CoasterWorld world : this.worlds.values()) {
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
     * Sets a new smoothness value
     *
     * @param value Smoothness
     */
    public void setSmoothness(double value) {
        if (this.smoothness != value) {
            this.smoothness = value;

            // Update config.yml
            {
                FileConfiguration config = new FileConfiguration(this);
                config.load();
                config.set("smoothness", this.smoothness);
                config.save();
            }
        }
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
     * Sets whether selections should be glowing
     *
     * @param enabled New option
     */
    public void setGlowingSelections(boolean enabled) {
        if (this.glowingSelections != enabled) {
            this.glowingSelections = enabled;

            // Update config.yml
            {
                FileConfiguration config = new FileConfiguration(this);
                config.load();
                config.set("glowing-selections", this.glowingSelections);
                config.save();
            }
        }
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

    /**
     * Gets the folder where coasters csv files are exported to when using
     * the export command
     *
     * @return Export folder
     */
    public File getExportFolder() {
        return this.exportFolder;
    }

    @Override
    public void onLoad() {
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
        config.addHeader("plotSquaredEnabled", "Give players the 'train.coasters.plotsquared.use' permission to use TCC in their plots");
        this.plotSquaredEnabled = config.get("plotSquaredEnabled", DEFAULT_PLOTSQUARED_ENABLED);
        config.setHeader("lightAPIEnabled", "\nWhether the light track object is made available when LightAPI is detected");
        this.lightAPIEnabled = config.get("lightAPIEnabled", DEFAULT_LIGHTAPI_ENABLED);
        config.setHeader("priority", "\nWhether TC-Coasters track have priority over other rail types, like vanilla track");
        boolean priority = config.get("priority", false);
        config.save();

        // Magic! Done early before trains are initialized that need this rails.
        RailType.register(this.coasterRailType, priority);

        // More magic! Done early so signs are activated on spawn.
        SignAction.register(this.trackAnimateAction);

        // Before loading coasters, detect LightAPI
        // We don't know yet it has enabled, but we'll assume that it will.
        // Check this isnt the legacy LightAPI as it does not work at all
        {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("LightAPI");
            if (plugin != null && !plugin.getDescription().getMain().equals("ru.beykerykt.minecraft.lightapi.bukkit.impl.BukkitPlugin")) {
                this.updateDependency(plugin, plugin.getName(), true);
            }
        }
    }

    @Override
    public void enable() {
        this.listener.enable();
        this.interactionListener.enable();

        // Load all coasters from csv
        // Note that if between onLoad and enable rails are queried, these worlds
        // may have been loaded earlier. These implicit loads only have an effect
        // when there's no trains on the worlds.
        for (World world : Bukkit.getWorlds()) {
            this.getCoasterWorld(world);
        }

        // Schedule some background tasks
        this.worldUpdateTask = (new WorldUpdateTask()).start(1, 1);
        this.runQueuedTasksTask = (new RunQueuedTasksTask()).start(1, 1);
        this.updatePlayerEditStatesTask = (new UpdatePlayerEditStatesTask()).start(1, 1);

        // Import/export folders
        this.importFolder = this.getDataFile("import");
        this.exportFolder = this.getDataFile("export");
        this.importFolder.mkdirs();
        this.exportFolder.mkdirs();

        // Autosave every 30 seconds approximately
        this.autosaveTask = new AutosaveTask(this).start(30*20, 30*20);

        // LightAPI may not have enabled right, correct for this
        {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("LightAPI");
            if (plugin != null) {
                this.updateDependency(plugin, plugin.getName(), plugin.isEnabled());
            }
        }

        // Update worlds right away
        this.worldUpdateTask.run();

        // Commands
        this.commands = new TCCoastersCommands();
        this.commands.enable(this);
    }

    @Override
    public void disable() {
        this.listener.disable();
        this.interactionListener.disable();
        Task.stop(this.worldUpdateTask);
        Task.stop(this.runQueuedTasksTask);
        Task.stop(this.updatePlayerEditStatesTask);
        Task.stop(this.autosaveTask);

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
    public boolean command(CommandSender sender, String command, String[] args) {
        return false; // Not used, we use Cloud now
    }

    @Override
    public void updateDependency(Plugin plugin, String pluginName, boolean enabled) {
        if (pluginName.equals("PlotSquared")) {
            boolean available = enabled && this.plotSquaredEnabled;
            if (available != (this.plotSquaredHandler != null)) {
                if (available) {
                    // Version 4 or version 5?
                    // Could inspect plugin description, but this is more reliable
                    int plotsquared_version;
                    try {
                        Class<?> locationClass = Class.forName("com.plotsquared.core.location.Location");
                        try {
                            locationClass.getDeclaredMethod("at", String.class, int.class, int.class, int.class);
                            plotsquared_version = 6; // Immutable Location with static at method since v6
                        } catch (Throwable t) {
                            plotsquared_version = 5; // Mutable Location with constructor
                        }
                    } catch (Throwable t) {
                        plotsquared_version = 4;
                    }

                    if (plotsquared_version == 6) {
                        this.plotSquaredHandler = new PlotSquaredHandler_v6(this);
                    } else if (plotsquared_version == 5) {
                        this.plotSquaredHandler = new PlotSquaredHandler_v5(this);
                    } else {
                        this.plotSquaredHandler = new PlotSquaredHandler_v4(this);
                    }
                    this.register(this.plotSquaredHandler);
                    this.log(Level.INFO, "PlotSquared support enabled!");
                } else {
                    CommonUtil.unregisterListener(this.plotSquaredHandler);
                    this.plotSquaredHandler = null;
                    this.log(Level.INFO, "PlotSquared support disabled!");
                }
            }
        } else if (pluginName.equals("LightAPI") && ((enabled && lightAPIEnabled) != lightAPIFound)) {
            lightAPIFound = (enabled && lightAPIEnabled);
            if (lightAPIFound) {
                log(Level.INFO, "LightAPI detected, the Light track object is now available");
                TrackCSV.registerEntry(TrackObjectTypeLight.CSVEntry::new);
            } else {
                log(Level.INFO, "LightAPI disabled, the Light track object is no longer available");
                TrackCSV.unregisterEntry(TrackObjectTypeLight.CSVEntry::new);
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

    public void buildAll() {
        for (CoasterWorld coasterWorld : this.getCoasterWorlds()) {
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
        boolean scheduled = noPermDebounce.isScheduled(player);
        noPermDebounce.schedule(player);
        if (!scheduled) {
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

    public CompletableFuture<Hastebin.DownloadResult> importFileOrURL(final String fileOrURL) {
        final File importFile;
        if (fileOrURL.startsWith("import/") || fileOrURL.startsWith("import\\")) {
            importFile = (new File(this.getDataFolder(), fileOrURL)).getAbsoluteFile();
        } else if (fileOrURL.startsWith("export/") || fileOrURL.startsWith("export\\")) {
            importFile = (new File(this.getDataFolder(), fileOrURL)).getAbsoluteFile();
        } else {
            importFile = (new File(this.importFolder, fileOrURL)).getAbsoluteFile();
        }
        if (importFile.exists()) {
            // Only allow files below the TC-Coasters folder (security)
            boolean validLocation;
            try {
                File a = importFile.getCanonicalFile();
                File b = this.getDataFolder().getAbsoluteFile().getCanonicalFile();
                validLocation = a.toPath().startsWith(b.toPath());
            } catch (IOException ex) {
                validLocation = false;
            }
            if (!validLocation) {
                return CompletableFuture.completedFuture(Hastebin.DownloadResult.error(fileOrURL,
                        "File is not within the TC-Coasters plugin directory, access disallowed"));
            }

            // File exists inside the import folder, load it asynchronously
            // Ideally I'd use some async I/O api for this, but meh
            final CompletableFuture<Hastebin.DownloadResult> future = new CompletableFuture<Hastebin.DownloadResult>();
            new AsyncTask() {
                @Override
                public void run() {
                    try (FileInputStream fs = new FileInputStream(importFile)) {
                        ByteArrayIOStream contentBuffer = new ByteArrayIOStream(fs.available());
                        contentBuffer.readFrom(fs);
                        future.complete(Hastebin.DownloadResult.content(fileOrURL, contentBuffer));
                    } catch (FileNotFoundException ex) {
                        future.complete(Hastebin.DownloadResult.error(fileOrURL, "File not found"));
                    } catch (IOException ex) {
                        future.complete(Hastebin.DownloadResult.error(fileOrURL, "File I/O error: " + ex.getMessage()));
                    }
                }
            }.start();
            return future.thenApplyAsync(result -> result, CommonUtil.getPluginExecutor(this));
        }

        // Treat as Hastebin URL
        return this.hastebin.download(fileOrURL);
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

    private class WorldUpdateTask extends Task {
        public WorldUpdateTask() {
            super(TCCoasters.this);
        }

        @Override
        public void run() {
            for (CoasterWorldImpl coasterWorld : worlds.values()) {
                coasterWorld.updateAll();
            }
        }
    }

    private class RunQueuedTasksTask extends Task {
        public RunQueuedTasksTask() {
            super(TCCoasters.this);
        }

        @Override
        public void run() {
            QueuedTask.runAll();
        }
    }

    private class UpdatePlayerEditStatesTask extends Task {
        public UpdatePlayerEditStatesTask() {
            super(TCCoasters.this);
        }

        @Override
        public void run() {
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
    }
}
