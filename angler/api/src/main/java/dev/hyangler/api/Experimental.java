package dev.hyangler.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks what may still change in a minor version, unlike the rest of the api. Kept at runtime so tools can find it.
 *
 * @since 1.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.PACKAGE})
public @interface Experimental {}
