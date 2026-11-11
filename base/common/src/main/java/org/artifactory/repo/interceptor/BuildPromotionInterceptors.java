package org.artifactory.repo.interceptor;

import org.artifactory.sapi.interceptor.BuildPromotionInterceptor;
import org.artifactory.spring.ReloadableBean;

/**
 * @author Volodymyr Shulga
 */
public interface BuildPromotionInterceptors extends ReloadableBean, BuildPromotionInterceptor {
}
