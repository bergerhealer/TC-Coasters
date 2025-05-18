package com.bergerkiller.bukkit.coasters.editor;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.common.regionflagtracker.RegionFlag;
import com.bergerkiller.bukkit.common.regionflagtracker.RegionFlagRegistry;
import com.bergerkiller.bukkit.common.regionflagtracker.RegionFlagTracker;

/**
 * Keeps track of the particle view distance for players using worldguard
 * region flags. Only enabled if the API is available in BKCommonLib.
 */
public class PlayerRegionViewRange {
    private static final RegionFlag<Integer> MAX_VIEW_DISTANCE = RegionFlag.ofInteger("tcc-max-view");

    public static void register(TCCoasters plugin) {
        RegionFlagRegistry.instance().register(plugin, MAX_VIEW_DISTANCE);
    }

    public static Integer track(PlayerEditState state) {
        RegionFlagTracker<Integer> tracker = RegionFlagTracker.track(state.getPlayer(), MAX_VIEW_DISTANCE);
        tracker.addListener(t -> {
            state.updateParticleViewRangeMaximum(t.getValue().orElse(null));
        });
        return tracker.getValue().orElse(null);
    }
}
