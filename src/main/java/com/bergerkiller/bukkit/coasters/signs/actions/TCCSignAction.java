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

    @Override
    public boolean match(SignActionEvent event) {
        String firstLine = event.getLine(1).toLowerCase(Locale.ENGLISH);
        if (firstLine.length() <= 3
                || firstLine.charAt(0) != 't'
                || firstLine.charAt(1) != 'c'
                || firstLine.charAt(2) != 'c')
        {
            return false;
        }

        int tOffset = 3;
        while (tOffset < firstLine.length()) {
            char c = firstLine.charAt(tOffset);
            if (c == '-' || c == ' ' || c == '_' || c == '.') {
                tOffset++;
            } else {
                break;
            }
        }
        return firstLine.startsWith(getPrefix(), tOffset);
    }
}
