package com.bergerkiller.bukkit.coasters.editor.signs;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

import com.bergerkiller.bukkit.common.block.InputDialogSubmitText;
import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.coasters.TCCoastersLocalization;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChange;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChangeCollection;
import com.bergerkiller.bukkit.coasters.signs.power.NamedPowerChannel;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeAnimationState;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeSign;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeState;
import com.bergerkiller.bukkit.common.block.SignEditDialog;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.wrappers.ChatText;
import com.bergerkiller.bukkit.tc.Util;

/**
 * Everything about editing signs and their power channels
 */
public class SignEditState {
    private final PlayerEditState editState;
    private int lastTickClicked = -1;
    private boolean hasOpenDialog = false;

    public SignEditState(PlayerEditState editState) {
        this.editState = editState;
    }

    private boolean handlePreClick(TrackNode node) {
        int ticks = CommonUtil.getServerTicks();
        if (lastTickClicked > ticks) {
            return false;
        }
        lastTickClicked = ticks + 5;

        // Deny if locked
        if (node.isLocked()) {
            TCCoastersLocalization.LOCKED.message(editState.getPlayer());
            return false;
        }

        return true;
    }

    public boolean onSignLeftClick(TrackNode node) {
        // If there are no signs, act as if the node wasn't clicked
        // This allows a player to still break the blocks behind a node
        TrackNodeSign[] signs = node.getSigns();
        if (signs.length == 0) {
            return false;
        }

        // Prevent spam-clicks and editing locked coasters
        if (!handlePreClick(node)) {
            return true;
        }

        // Try to remove the last sign
        try {
            setSignsForNode(editState.getHistory(), node, Arrays.copyOf(signs, signs.length - 1));
            updateSignInSelectedAnimationStates(node, signs[signs.length - 1], null);
        } catch (ChangeCancelledException e) {
            TCCoastersLocalization.SIGN_REMOVE_FAILED.message(editState.getPlayer());
            return false;
        }

        TCCoastersLocalization.SIGN_REMOVE_SUCCESS.message(editState.getPlayer());
        return true;
    }

    public boolean onSignRightClick(TrackNode node, ItemStack signItem) {
        // Prevent spam-clicks and editing locked coasters
        if (!handlePreClick(node)) {
            return true;
        }

        final boolean appendToSign = editState.getPlayer().isSneaking();
        new SignEditDialog() {
            @Override
            public void onClosed(Player player, String[] lines) {
                try {
                    if (appendToSign && node.getSigns().length > 0) {
                        // Append lines to last sign
                        TrackNodeSign[] new_signs = node.getSigns().clone();
                        TrackNodeSign old_sign = new_signs[new_signs.length - 1];
                        TrackNodeSign updated = old_sign.clone();
                        for (String line : lines) {
                            if (!line.isEmpty()) {
                                updated.appendLine(line);
                            }
                        }
                        new_signs[new_signs.length - 1] = updated;

                        // Update the signs
                        setSignsForNode(editState.getHistory(), node, new_signs);;
                        updateSignInSelectedAnimationStates(node, old_sign, updated);
                        TCCoastersLocalization.SIGN_ADD_APPEND.message(player);
                    } else {
                        // Add a new sign to the node
                        addSignToNode(editState.getHistory(), node, new TrackNodeSign(lines), true);
                        TCCoastersLocalization.SIGN_ADD_SUCCESS.message(player);
                    }
                } catch (ChangeCancelledException e) {
                    TCCoastersLocalization.SIGN_ADD_FAILED.message(player);
                }
            }
        }.open(editState.getPlayer(), findInitialLines(signItem));
        return true;
    }

    public boolean onTorchLeftClick(TrackNode node) {
        TrackNodeSign[] old_signs = node.getSigns();
        if (old_signs.length == 0) {
            return false;
        }
        TrackNodeSign old_sign = old_signs[old_signs.length - 1];
        if (old_sign.getInputPowerChannels().length == 0) {
            return false;
        }

        // Prevent spam-clicks and editing locked coasters
        if (!handlePreClick(node)) {
            return true;
        }

        // Remove the last power channel
        TrackNodeSign new_sign = old_sign.clone();
        new_sign.removeInputPowerChannel(new_sign.getInputPowerChannels()[new_sign.getInputPowerChannels().length - 1]);

        // Try to update the signs of this node
        TrackNodeSign[] new_signs = old_signs.clone();
        new_signs[new_signs.length - 1] = new_sign;
        try {
            setSignsForNode(editState.getHistory(), node, new_signs);
            updateSignInSelectedAnimationStates(node, old_sign, new_sign);
            TCCoastersLocalization.SIGN_POWER_REMOVED.message(editState.getPlayer());
        } catch (ChangeCancelledException e) {
            TCCoastersLocalization.SIGN_POWER_FAILED.message(editState.getPlayer());
        }

        return true;
    }

    public boolean onTorchRightClick(TrackNode node) {
        TrackNodeSign[] old_signs = node.getSigns();
        if (old_signs.length == 0) {
            return false;
        }

        // Prevent spam-clicks and editing locked coasters
        if (!handlePreClick(node) || hasOpenDialog) {
            return true;
        }

        // Decide the face to assign to the sign
        // When player sneaks, use the opposite-face from where the player looks
        final BlockFace face = editState.getPlayer().isSneaking()
                ? Util.vecToFace(editState.getPlayer().getEyeLocation().getDirection(), false).getOppositeFace() : BlockFace.SELF;

        // Show a dialog asking for a power channel name. On completion, add it to the sign/node
        hasOpenDialog = true;
        InputDialogSubmitText dialog = new InputDialogSubmitText(editState.getPlugin(), editState.getPlayer()) {
            @Override
            public void onAccept(String channel_name) {
                try {
                    TrackNodeSign[] old_signs = node.getSigns();
                    if (old_signs.length == 0) {
                        TCCoastersLocalization.SIGN_MISSING.message(editState.getPlayer());
                    } else {
                        TrackNodeSign[] new_signs = old_signs.clone();
                        TrackNodeSign old_sign = old_signs[old_signs.length - 1];
                        TrackNodeSign new_sign = old_sign.clone();
                        new_sign.addInputPowerChannel(channel_name, false, face);
                        new_signs[new_signs.length - 1] = new_sign;
                        setSignsForNode(editState.getHistory(), node, new_signs);
                        updateSignInSelectedAnimationStates(node, old_sign, new_sign);

                        String faceStr = (face == null || face == BlockFace.SELF)
                                ? "All sides" : ("The " + face.name().toLowerCase(Locale.ENGLISH) + " side");
                        TCCoastersLocalization.SIGN_POWER_ASSIGNED.message(editState.getPlayer(), channel_name, faceStr, "1");
                    }
                } catch (ChangeCancelledException e) {
                    TCCoastersLocalization.SIGN_POWER_FAILED.message(editState.getPlayer());
                    return;
                }
            }

            @Override
            public void onOpen() {
                super.onOpen();
                this.setDescription("Enter power channel");
            }

            @Override
            public void onClose() {
                hasOpenDialog = false;
                super.onClose();
            }
        };
        CommonUtil.nextTick(dialog::open);

        return true;
    }

    public boolean onLeverLeftClick(TrackNode node) {
        TrackNodeSign[] old_signs = node.getSigns();
        if (old_signs.length == 0) {
            return false;
        }
        TrackNodeSign old_sign = old_signs[old_signs.length - 1];
        if (old_sign.getOutputPowerChannels().length == 0) {
            return false;
        }

        // Prevent spam-clicks and editing locked coasters
        if (!handlePreClick(node)) {
            return true;
        }

        // Remove the last power output channel
        TrackNodeSign new_sign = old_sign.clone();
        new_sign.removeOutputPowerChannel(new_sign.getOutputPowerChannels()[new_sign.getOutputPowerChannels().length - 1]);

        // Try to update the signs of this node
        TrackNodeSign[] new_signs = old_signs.clone();
        new_signs[new_signs.length - 1] = new_sign;
        try {
            setSignsForNode(editState.getHistory(), node, new_signs);
            updateSignInSelectedAnimationStates(node, old_sign, new_sign);
            TCCoastersLocalization.SIGN_POWER_REMOVED.message(editState.getPlayer());
        } catch (ChangeCancelledException e) {
            TCCoastersLocalization.SIGN_POWER_FAILED.message(editState.getPlayer());
        }

        return true;
    }

    public boolean onLeverRightClick(TrackNode node) {
        TrackNodeSign[] old_signs = node.getSigns();
        if (old_signs.length == 0) {
            return false;
        }

        // Prevent spam-clicks and editing locked coasters
        if (!handlePreClick(node) || hasOpenDialog) {
            return true;
        }

        // Show a dialog asking for a power channel name. On completion, add it to the sign/node
        hasOpenDialog = true;
        InputDialogSubmitText dialog = new InputDialogSubmitText(editState.getPlugin(), editState.getPlayer()) {
            @Override
            public void onAccept(String channel_name) {
                try {
                    TrackNodeSign[] old_signs = node.getSigns();
                    if (old_signs.length == 0) {
                        TCCoastersLocalization.SIGN_MISSING.message(editState.getPlayer());
                    } else if (NamedPowerChannel.checkPermission(editState.getPlayer(), channel_name)) {
                        TrackNodeSign[] new_signs = old_signs.clone();
                        TrackNodeSign old_sign = old_signs[old_signs.length - 1];
                        TrackNodeSign new_sign = old_sign.clone();
                        new_sign.addOutputPowerChannel(channel_name, false);
                        new_signs[new_signs.length - 1] = new_sign;
                        setSignsForNode(editState.getHistory(), node, new_signs);
                        updateSignInSelectedAnimationStates(node, old_sign, new_sign);

                        TCCoastersLocalization.SIGN_POWER_ASSIGNED.message(editState.getPlayer(), channel_name, "OUTPUT", "1");
                    }
                } catch (ChangeCancelledException e) {
                    TCCoastersLocalization.SIGN_POWER_FAILED.message(editState.getPlayer());
                    return;
                }
            }

            @Override
            public void onOpen() {
                super.onOpen();
                this.setDescription("Enter power channel");
            }

            @Override
            public void onClose() {
                hasOpenDialog = false;
                super.onClose();
            }
        };
        CommonUtil.nextTick(dialog::open);

        return true;
    }

    private static String[] findInitialLines(ItemStack signItem) {
        return findInitialLines(CommonItemStack.of(signItem));
    }

    private static String[] findInitialLines(CommonItemStack signItem) {
        if (!signItem.isEmpty() && signItem.hasCustomData()) {
            try {
                CommonTagCompound nbt = signItem.getCustomData();
                if (nbt.containsKey("BlockEntityTag")) {
                    CommonTagCompound blockTag = nbt.getCompoundOrEmpty("BlockEntityTag");
                    if ("minecraft:sign".equals(blockTag.getValue("id", String.class))) {
                        return new String[] {
                                ChatText.fromJson(blockTag.getValue("Text1", "")).getMessage(),
                                ChatText.fromJson(blockTag.getValue("Text2", "")).getMessage(),
                                ChatText.fromJson(blockTag.getValue("Text3", "")).getMessage(),
                                ChatText.fromJson(blockTag.getValue("Text4", "")).getMessage()
                        };
                    }
                }
            } catch (Throwable t) {
                // Probably failed to decode something
                // Ignore. Corrupt item?
            }
        }

        return new String[] { "", "", "", "" };
    }

    /**
     * Updates the last sign of all selected nodes in some way
     *
     * @param function Function applied to a clone of the last sign of all selected nodes
     */
    public void updateLastSign(Consumer<TrackNodeSign> function) throws ChangeCancelledException {
        updateLastSign(s -> {
            TrackNodeSign copy = s.clone();
            function.accept(copy);
            return copy;
        });
    }

    /**
     * Updates the last sign of all selected nodes in some way
     *
     * @param function Function applied to the last sign of all selected nodes
     */
    public void updateLastSign(Function<TrackNodeSign, TrackNodeSign> function) throws ChangeCancelledException {
        // Deselect nodes we cannot edit
        editState.deselectLockedNodes();

        HistoryChange changes = null;
        for (TrackNode node : editState.getEditedNodes()) {
            TrackNodeSign[] old_signs = node.getSigns();
            if (old_signs.length > 0) {
                TrackNodeSign[] new_signs = old_signs.clone();
                TrackNodeSign old_sign = old_signs[old_signs.length - 1];
                TrackNodeSign new_sign = function.apply(old_sign);
                if (old_sign != new_sign) {
                    new_signs[new_signs.length - 1] = new_sign;
                    if (changes == null) {
                        changes = editState.getHistory().addChangeGroup();
                    }
                    setSignsForNode(changes, node, new_signs);
                    updateSignInSelectedAnimationStates(node, old_sign, new_sign);
                }
            }
        }
    }

    public boolean removeLastSign() throws ChangeCancelledException {
        // Deselect nodes we cannot edit
        editState.deselectLockedNodes();

        HistoryChange changes = editState.getHistory().addChangeGroup();
        for (TrackNode node : editState.getEditedNodes()) {
            TrackNodeSign[] old_signs = node.getSigns();
            if (old_signs.length > 0) {
                this.setSignsForNode(changes, node, Arrays.copyOf(old_signs, old_signs.length - 1));
                this.updateSignInSelectedAnimationStates(node, old_signs[old_signs.length - 1], null);
            }
        }

        return changes.hasChanges();
    }

    public void scrollSigns() throws ChangeCancelledException {
        // Deselect nodes we cannot edit
        editState.deselectLockedNodes();

        HistoryChange changes = editState.getHistory().addChangeGroup();
        for (TrackNode node : editState.getEditedNodes()) {
            TrackNodeSign[] old_signs = node.getSigns();
            if (old_signs.length > 1) {
                TrackNodeSign[] new_signs = new TrackNodeSign[old_signs.length];
                for (int n = 0; n < old_signs.length; n++) {
                    new_signs[(n == 0) ? old_signs.length - 1 : (n-1)] = old_signs[n];
                }
                setSignsForNode(changes, node, new_signs);
            }
        }
    }

    public void clearSigns() throws ChangeCancelledException {
        // Deselect nodes we cannot edit
        editState.deselectLockedNodes();

        HistoryChange changes = editState.getHistory().addChangeGroup();
        for (TrackNode node : editState.getEditedNodes()) {
            TrackNodeSign[] old_signs = node.getSigns();
            if (old_signs.length > 0) {
                setSignsForNode(changes, node, TrackNodeSign.EMPTY_ARR);
                for (TrackNodeSign sign : old_signs) {
                    updateSignInSelectedAnimationStates(node, sign, null);
                }
            }
        }
    }

    /**
     * Replaces the sign in all selected nodes matching the sign with a replacement
     *
     * @param toReplace Sign whose lines to match and replace
     * @param sign Sign replacement
     * @return True if one or more signs were replaced, False if none matched
     * @throws ChangeCancelledException
     */
    public boolean replaceSign(TrackNodeSign toReplace, TrackNodeSign sign) throws ChangeCancelledException {
        // Deselect nodes we cannot edit
        editState.deselectLockedNodes();

        boolean hasChanges = false;
        HistoryChange changes = editState.getHistory().addChangeGroup();
        for (TrackNode node : editState.getEditedNodes()) {
            TrackNodeSign[] old_signs = node.getSigns();
            if (old_signs.length == 0) {
                continue;
            }

            TrackNodeSign addedSign = null;
            TrackNodeSign[] new_signs = node.getSigns().clone();
            for (int i = 0; i < new_signs.length; i++) {
                if (new_signs[i].hasSameLines(toReplace)) {
                    new_signs[i] = addedSign = sign.clone();
                }
            }
            if (addedSign == null) {
                continue;
            }

            // Update signs first
            setSignsForNode(changes, node, new_signs);

            // Fire build event the first time
            // Handle events/changes/perms
            if (!hasChanges) {
                hasChanges = true;
                if (!addedSign.fireBuildEvent(editState.getPlayer(), true)) {
                    node.setSigns(old_signs);
                    throw new ChangeCancelledException();
                }
            }

            // Update animations too
            for (int i = 0; i < new_signs.length; i++) {
                updateSignInSelectedAnimationStates(node, old_signs[i], new_signs[i]);
            }
        }
        return hasChanges;
    }

    public void addSign(TrackNodeSign sign) throws ChangeCancelledException {
        // Deselect nodes we cannot edit
        editState.deselectLockedNodes();

        // Failure if no nodes are selected
        if (!editState.hasEditedNodes()) {
            return;
        }

        boolean firedEvent = false;
        HistoryChange changes = editState.getHistory().addChangeGroup();
        for (TrackNode node : editState.getEditedNodes()) {
            addSignToNode(changes, node, sign, !firedEvent);
            firedEvent = true;
        }
    }

    /**
     * Adds a sign to this node and checks for permissions for building this type of sign
     *
     * @param changes Historic changes to which a new entry is added
     * @param node Node to which a sign is added
     * @param sign Sign that was added
     * @param interactive Whether this is an interactive build. If true, a successful build message is sent.
     *                    If false, only permission/error related messages are sent.
     * @throws ChangeCancelledException
     */
    private void addSignToNode(HistoryChangeCollection changes, TrackNode node, TrackNodeSign sign, boolean interactive) throws ChangeCancelledException {
        TrackNodeSign node_sign = sign.clone();
        TrackNodeSign[] old_signs = node.getSigns();
        setSignsForNode(changes, node, LogicUtil.appendArrayElement(node.getSigns(), node_sign));

        // Fire a sign build event with the sign's custom sign
        if (!node_sign.fireBuildEvent(editState.getPlayer(), interactive)) {
            node.setSigns(old_signs);
            throw new ChangeCancelledException();
        }

        // All successful, now add this sign to an animation state if one was set to be edited
        updateSignInSelectedAnimationStates(node, null, sign);
    }

    private void setSignsForNode(HistoryChangeCollection changes, TrackNode node, TrackNodeSign[] new_signs) throws ChangeCancelledException {
        TrackNodeSign[] old_signs = node.getSigns();
        changes.addChangeBeforeSetSigns(editState.getPlayer(), node, new_signs);
        node.setSigns(new_signs);
        try {
            changes.handleChangeAfterSetSigns(editState.getPlayer(), node, old_signs);
        } catch (ChangeCancelledException ex) {
            node.setSigns(old_signs);
            throw ex;
        }
    }

    private void updateSignInSelectedAnimationStates(TrackNode node, TrackNodeSign old_sign, TrackNodeSign new_sign) {
        TrackNodeAnimationState animState = node.findAnimationState(editState.getSelectedAnimation());
        if (animState != null) {
            // Refresh selected animation state of node too, if one is selected for this node
            // Leave other animation states alone
            node.setAnimationState(animState.name, updateSignInNodeState(animState.state, old_sign, new_sign), animState.connections);
        } else {
            // No animation is selected for this node, presume the sign should be added to all animation states
            for (TrackNodeAnimationState state : node.getAnimationStates()) {
                node.setAnimationState(state.name, updateSignInNodeState(state.state, old_sign, new_sign), state.connections);
            }
        }
    }

    private TrackNodeState updateSignInNodeState(TrackNodeState state, TrackNodeSign old_sign, TrackNodeSign new_sign) {
        if (old_sign == null) {
            return state.changeAddSign(new_sign.clone());
        } else if (new_sign == null) {
            return state.changeRemoveSign(old_sign);
        } else {
            return state.changeUpdateSign(old_sign, new_sign.clone());
        }
    }
}
