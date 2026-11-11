package org.artifactory.rest.filter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;

/**
 * @author Igor Usenko
 */
@Slf4j
public class DisabledApiFilter implements ContainerRequestFilter {
    private final ConstantValues flag;
    private final String message;

    public DisabledApiFilter(ConstantValues flag, String message) {
        this.flag = flag;
        this.message = message;
    }

    @Override
    public void filter(ContainerRequestContext ctx) {
        if (flag.getBoolean()) {
            log.debug("Disabled API blocked: {} {} - {}",
                    ctx.getMethod(), ctx.getUriInfo().getPath(), message);
            ctx.abortWith(Response.status(Response.Status.GONE)
                    .entity(message)
                    .type(MediaType.TEXT_PLAIN)
                    .build());
        }
    }
}
