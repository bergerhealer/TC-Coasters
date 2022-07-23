package com.bergerkiller.bukkit.coasters.editor;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.common.internal.CommonCapabilities;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;

/**
 * A type of tool a Player is holding
 */
public enum PlayerEditTool {
    /** Player is not holding any sort of editor tool */
    NONE {
        @Override
        public boolean isItem(Player player, ItemStack mainItem) {
            return false;
        }

        @Override
        public boolean handleClick(PlayerEditState state, boolean isLeftClick, boolean isRightClick) {
            return false;
        }
    },
    /** Player is holding a TC-Coasters editor map */
    MAP {
        @Override
        public boolean isItem(Player player, ItemStack mainItem) {
            if (MapDisplay.getViewedDisplay(player, mainItem) instanceof TCCoastersDisplay) {
                return true;
            } else if (!ItemUtil.isEmpty(mainItem)) {
                return false;
            }

            ItemStack offItem = HumanHand.getItemInOffHand(player);
            return MapDisplay.getViewedDisplay(player, offItem) instanceof TCCoastersDisplay;
        }

        @Override
        public boolean handleClick(PlayerEditState state, boolean isLeftClick, boolean isRightClick) {
            return (isLeftClick && state.onLeftClick()) || (isRightClick && state.onRightClick());
        }
    },
    /** Player is holding a sign item. Used to add signs to nodes. */
    SIGN {
        @Override
        public boolean isItem(Player player, ItemStack mainItem) {
            if (mainItem == null) {
                return false;
            } else if (CommonCapabilities.MATERIAL_ENUM_CHANGES) {
                // Modern API re-purposes the block sign type.
                return MaterialUtil.ISSIGN.get(mainItem.getType());
            } else {
                // Legacy sign material type is the only sign type. Other types are for blocks only.
                return mainItem.getType().name().equals("SIGN");
            }
        }

        @Override
        public boolean handleClick(PlayerEditState state, boolean isLeftClick, boolean isRightClick) {
            TrackNode lookingAt = state.getWorld().getTracks().findNodeLookingAt(
                    state.getPlayer().getEyeLocation(), 1.0, 10.0);
            if (lookingAt != null) {
                if (isLeftClick && state.getSigns().onSignLeftClick(lookingAt)) {
                    return true;
                }
                if (isRightClick && state.getSigns().onSignRightClick(lookingAt, HumanHand.getItemInMainHand(state.getPlayer()))) {
                    return true;
                }
            }
            return false;
        }
    },
    /** Player is holding a redstone torch item. Used to assign power input channels */
    TORCH {
        @Override
        public boolean isItem(Player player, ItemStack mainItem) {
            return mainItem != null && MaterialUtil.ISREDSTONETORCH.get(mainItem.getType());
        }

        @Override
        public boolean handleClick(PlayerEditState state, boolean isLeftClick, boolean isRightClick) {
            TrackNode lookingAt = state.getWorld().getTracks().findNodeLookingAt(
                    state.getPlayer().getEyeLocation(), 1.0, 10.0);
            if (lookingAt != null) {
                if (isLeftClick && state.getSigns().onTorchLeftClick(lookingAt)) {
                    return true;
                }
                if (isRightClick && state.getSigns().onTorchRightClick(lookingAt)) {
                    return true;
                }
            }
            return false;
        }
    },
    /** Player is holding a lever item. Used to assign power output channels */
    LEVER {
        final Material lever_type = MaterialUtil.getFirst("LEVER", "LEGACY_LEVER");

        @Override
        public boolean isItem(Player player, ItemStack mainItem) {
            return mainItem != null && mainItem.getType() == lever_type;
        }

        @Override
        public boolean handleClick(PlayerEditState state, boolean isLeftClick, boolean isRightClick) {
            return false;
        }
    };

    /**
     * Gets whether the given Item held by a Player matches this edit tool type
     *
     * @param player Player
     * @param mainItem Item held in the player's main hand
     * @return True if it matches
     */
    public abstract boolean isItem(Player player, ItemStack mainItem);

    /**
     * Called when the player left-or-right-clicks while holding the tool
     *
     * @param state PlayerEditState of the Player that clicked
     * @param isLeftClick Whether this is a left-click
     * @param isRightClick Whether this is a right-click
     * @return Whether the original click interaction should be cancelled, or in other words, that the
     *         click was handled. Return true to handle the click. False to ignore it.
     */
    public abstract boolean handleClick(PlayerEditState state, boolean isLeftClick, boolean isRightClick);
}
