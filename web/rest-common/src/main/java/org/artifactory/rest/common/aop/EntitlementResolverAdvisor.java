package org.artifactory.rest.common.aop;

import org.artifactory.entitlement.Entitlement;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;


public class EntitlementResolverAdvisor extends DefaultPointcutAdvisor {

    public EntitlementResolverAdvisor() {
        super(new AnnotationMatchingPointcut(Entitlement.class, null),
                new EntitlementAdvice());
    }

}

