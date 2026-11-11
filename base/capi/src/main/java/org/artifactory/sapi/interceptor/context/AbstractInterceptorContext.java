package org.artifactory.sapi.interceptor.context;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class AbstractInterceptorContext {
    private boolean triggeredByMove;

}
