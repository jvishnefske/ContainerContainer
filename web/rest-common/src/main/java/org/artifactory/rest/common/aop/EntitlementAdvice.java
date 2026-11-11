package org.artifactory.rest.common.aop;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.Joinpoint;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.entitlement.Entitlement;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.springframework.stereotype.Component;

import java.util.Optional;


@Slf4j
@Component
public class EntitlementAdvice implements MethodInterceptor {

    @Nullable
    @Override
    public Object invoke(@Nonnull MethodInvocation invocation) throws Throwable {
        EntitlementType entitlement = Optional.of(invocation)
                .map(Joinpoint::getThis)
                .map(Object::getClass)
                .map(l -> l.getAnnotation(Entitlement.class))
                .map(Entitlement::value)
                .orElse(null);
        if (null != entitlement && ContextHelper.get().beanForType(AddonsManager.class)
                .isEntitled(entitlement)) {
            return invocation.proceed();
        }
        return new ArtifactoryRestResponse<>()
                .responseCode(HttpStatus.SC_FORBIDDEN)
                .buildResponse();
    }
}

