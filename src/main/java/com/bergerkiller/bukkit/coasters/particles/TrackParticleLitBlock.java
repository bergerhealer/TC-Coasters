package com.bergerkiller.bukkit.coasters.particles;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.util.VirtualFallingBlock;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.collections.octree.DoubleOctree;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;

public class TrackParticleLitBlock extends TrackParticle {
    protected static final int FLAG_POSITION_CHANGED  = (1<<2);
    private DoubleOctree.Entry<TrackParticle> position;
    private int holderEntityId = -1;
    private int entityId = -1;

    public TrackParticleLitBlock(IntVector3 block) {
        this.position = DoubleOctree.Entry.create(block.midX(), block.y, block.midZ(), this);
    }

    @Override
    protected void onAdded() {
        addPosition(this.position);
    }

    @Override
    protected void onRemoved() {
        removePosition(this.position);
    }

    public void setBlock(IntVector3 block) {
        if (block != null && !this.position.equalsBlockCoord(block)) {
            this.position = updatePosition(this.position, DoubleOctree.Entry.create(block.midX(), block.y, block.midZ(), this));
            this.setFlag(FLAG_POSITION_CHANGED);
            this.scheduleUpdateAppearance();
        }
    }

    @Override
    public boolean isVisible(Player viewer) {
        return getState(viewer) != TrackParticleState.HIDDEN;
    }

    @Override
    public double distanceSquared(Vector viewerPosition) {
        return this.position.distanceSquared(viewerPosition);
    }

    @Override
    public void makeVisibleFor(Player viewer) {
        VirtualFallingBlock block = VirtualFallingBlock.create(this.holderEntityId, this.entityId)
            .position(this.position)
            .smoothMovement(false)
            .material(getMat(viewer))
            .spawn(viewer);

        this.holderEntityId = block.holderEntityId();
        this.entityId = block.entityId();
    }

    @Override
    public void makeHiddenFor(Player viewer) {
        VirtualFallingBlock.create(this.holderEntityId, this.entityId).destroy(viewer);
    }

    @Override
    public void onStateUpdated(Player viewer) {
        super.onStateUpdated(viewer);
        if (this.getViewers().contains(viewer)) {
            this.makeHiddenFor(viewer);
            this.makeVisibleFor(viewer);
        }
    }

    @Override
    public void updateAppearance() {
        if (this.clearFlag(FLAG_POSITION_CHANGED)) {
            VirtualFallingBlock.create(this.holderEntityId, this.entityId)
                .position(this.position)
                .smoothMovement(false)
                .updatePosition(this.getViewers());
        }
    }

    @Override
    public boolean usesEntityId(int entityId) {
        return this.entityId == entityId;
    }

    private final BlockData getMat(Player viewer) {
        if (getState(viewer) == TrackParticleState.SELECTED) {
            return BlockData.fromMaterial(MaterialUtil.getFirst("GOLD_BLOCK", "LEGACY_GOLD_BLOCK"));
        } else {
            return BlockData.fromMaterial(MaterialUtil.getFirst("GLASS", "LEGACY_GLASS"));
        }
    }
}
