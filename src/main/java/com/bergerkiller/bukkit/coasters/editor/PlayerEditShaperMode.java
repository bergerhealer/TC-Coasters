package com.bergerkiller.bukkit.coasters.editor;

import com.bergerkiller.bukkit.coasters.editor.manipulation.NodeManipulator;
import com.bergerkiller.bukkit.coasters.editor.manipulation.modes.NodeManipulatorGridPosition;
import com.bergerkiller.bukkit.coasters.editor.manipulation.modes.NodeManipulatorPosition;
import com.bergerkiller.bukkit.coasters.editor.manipulation.modes.circle.NodeManipulatorCircleFit;

/**
 * Selectable mode in the track shaper tab in the editor.
 */
public enum PlayerEditShaperMode {
    FREEFORM("Freeform", NodeManipulatorPosition.INITIALIZER),
    GRID("Grid", NodeManipulatorGridPosition.INITIALIZER),
    CIRCULAR("Circular", NodeManipulatorCircleFit.INITIALIZER);

    private final String displayName;
    private final NodeManipulator.Initializer manipulatorInitializer;

    PlayerEditShaperMode(String displayName, NodeManipulator.Initializer manipulatorInitializer) {
        this.displayName = displayName;
        this.manipulatorInitializer = manipulatorInitializer;
    }

    public String getDisplayName() {
        return displayName;
    }

    public NodeManipulator.Initializer getManipulatorInitializer() {
        return manipulatorInitializer;
    }
}
