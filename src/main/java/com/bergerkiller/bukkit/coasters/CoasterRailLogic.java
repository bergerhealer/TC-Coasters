package com.bergerkiller.bukkit.coasters;

import com.bergerkiller.bukkit.coasters.rails.TrackRailsSection;
import com.bergerkiller.bukkit.coasters.world.CoasterWorld;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;

public class CoasterRailLogic extends RailLogic {
    private final CoasterWorld world;
    private final TrackRailsSection section;

    public CoasterRailLogic(CoasterWorld world, TrackRailsSection section) {
        super(section.getMovementDirection());
        this.world = world;
        this.section = section;
    }

    @Override
    public RailPath getPath() {
        return this.section.path;
    }

    @Override
    public void onPostMove(MinecartMember<?> member) {
        super.onPostMove(member);

        this.section.setPickedByMember(this.world, member);
    }
}
