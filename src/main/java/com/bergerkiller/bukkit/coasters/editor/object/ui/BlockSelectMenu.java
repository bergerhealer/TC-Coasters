package com.bergerkiller.bukkit.coasters.editor.object.ui;

import java.util.function.Supplier;

import com.bergerkiller.bukkit.coasters.objects.TrackObjectTypeBlock;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.object.ui.block.MapWidgetBlockGrid;
import com.bergerkiller.bukkit.coasters.editor.object.ui.block.MapWidgetBlockStateList;
import com.bergerkiller.bukkit.coasters.editor.object.ui.block.MapWidgetBlockVariantList;
import com.bergerkiller.bukkit.coasters.objects.TrackObjectType;
import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;

/**
 * Shows a simple menu for selecting a Block and matching
 * block state (variant list), allowing for selecting
 * any possible BlockData type.
 */
public class BlockSelectMenu extends MapWidgetMenu {
    private final MapWidgetBlockStateList blockStateList;
    private final MapWidgetBlockVariantList variantList;
    private final MapWidgetBlockGrid blockSelector;
    private boolean initializing;

    public BlockSelectMenu(Supplier<PlayerEditState> stateSupplier) {
        this.setBounds(0, 0, 118, 103);
        this.setBackgroundColor(MapColorPalette.COLOR_BLUE);

        this.initializing = true;

        this.blockStateList = this.addWidget(new MapWidgetBlockStateList());
        this.blockStateList.setBounds(0, 25, 118, 0);

        this.variantList = this.addWidget(new MapWidgetBlockVariantList() {
            @Override
            public void onBlockChanged(BlockData block) {
                blockStateList.setBlock(block);
                if (!initializing) {
                    stateSupplier.get().getObjects().transformSelectedType(TrackObjectTypeBlock.class,
                            type -> type.setBlockData(block));
                }
            }

            @Override
            public void onKeyPressed(MapKeyEvent event) {
                if (event.getKey() == MapPlayerInput.Key.DOWN ||
                    event.getKey() == MapPlayerInput.Key.UP ||
                    event.getKey() == MapPlayerInput.Key.ENTER)
                {
                    blockStateList.setVisible(false);
                    blockSelector.activate();
                } else {
                    super.onKeyPressed(event);
                }
            }

            @Override
            public boolean onItemDrop(Player player, ItemStack item) {
                return BlockSelectMenu.this.onItemDrop(player, item);
            }
        });
        this.variantList.setPosition(7, 7);

        this.blockSelector = this.addWidget(new MapWidgetBlockGrid() {
            @Override
            public void onSelectionChanged() {
                if (!initializing) {
                    variantList.setBlock(BlockData.fromMaterial(getSelectedBlock()));
                }
            }

            @Override
            public void onKeyPressed(MapKeyEvent event) {
                if (event.getKey() == MapPlayerInput.Key.BACK || event.getKey() == MapPlayerInput.Key.ENTER) {
                    blockStateList.setVisible(true);
                    variantList.focus();
                } else {
                    super.onKeyPressed(event);
                }
            }

            @Override
            public boolean onItemDrop(Player player, ItemStack item) {
                return BlockSelectMenu.this.onItemDrop(player, item);
            }
        }).setDimensions(6, 4);
        this.blockSelector.addAllBlocks();
        this.blockSelector.setPosition(7, 30);

        TrackObjectType<?> type = stateSupplier.get().getObjects().getSelectedType();
        if (type instanceof TrackObjectTypeBlock) {
            BlockData current = ((TrackObjectTypeBlock<?>) type).getBlockData();
            this.variantList.setBlock(current);
            this.blockSelector.setSelectedBlock(current.getType());
        }

        this.initializing = false;
    }

    @Override
    public void onAttached() {
        super.onAttached();
        display.playSound(SoundEffect.PISTON_EXTEND);
    }

    @Override
    public void onDetached() {
        display.playSound(SoundEffect.PISTON_CONTRACT);
    }

    @Override
    public boolean onItemDrop(Player player, ItemStack item) {
        BlockData data = BlockData.fromItemStack(item);
        if (data != null && data != BlockData.AIR) {
            this.initializing = true;
            this.blockSelector.setSelectedBlock(data.getType());
            this.initializing = false;
            this.variantList.setBlock(data);
            display.playSound(SoundEffect.CLICK_WOOD);
            return true;
        }
        return false;
    }
}
