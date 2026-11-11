package org.artifactory.rest.compression;

import jakarta.ws.rs.NameBinding;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Used for annotate REST endpoints for output compression
 *
 * @author shaharz
 */
@NameBinding
@Retention(RetentionPolicy.RUNTIME)
public @interface Compress {
}
