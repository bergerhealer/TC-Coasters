package com.bergerkiller.bukkit.coasters.signs.actions;

import java.util.Locale;

import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignAction;

public abstract class TCCSignAction extends SignAction {

    /**
     * Gets the text prefix for this sign action.
     * This is the word put right after tcc-.
     * Word should be all-lowercase characters.
     * 
     * @return prefix
     */
    public abstract String getPrefix();

    protected boolean matchSecondLine(String line) {
        line = line.toLowerCase(Locale.ENGLISH);
        if (line.length() <= 3
                || line.charAt(0) != 't'
                || line.charAt(1) != 'c'
                || line.charAt(2) != 'c')
        {
            return false;
        }

        int tOffset = 3;
        while (tOffset < line.length()) {
            char c = line.charAt(tOffset);
            if (c == '-' || c == ' ' || c == '_' || c == '.') {
                tOffset++;
            } else {
                break;
            }
        }
        return line.startsWith(getPrefix(), tOffset);
    }

    @Override
    public final boolean match(SignActionEvent event) {
        return matchSecondLine(event.getLine(1));
    }
}
