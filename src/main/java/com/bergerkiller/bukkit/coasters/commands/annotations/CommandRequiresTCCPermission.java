package com.bergerkiller.bukkit.coasters.commands.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a cloud-annotated method requires the sender has
 * the TC-Coasters or plotsquared use permissions.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CommandRequiresTCCPermission {
}
