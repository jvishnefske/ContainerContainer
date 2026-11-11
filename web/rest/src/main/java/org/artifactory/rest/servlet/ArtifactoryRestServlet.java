/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.rest.servlet;


import org.apache.commons.beanutils.BeanUtilsBean;
import org.apache.commons.beanutils.BeanUtilsBean2;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.schedule.CachedThreadPoolTaskExecutor;
import org.artifactory.webapp.servlet.DelayedInit;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.Filter;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;

/**
 * We use our own rest servlet for the initialization using ArtifactoryContext.
 *
 * @author Yossi Shaul
 */
public class ArtifactoryRestServlet extends ServletContainer implements DelayedInit {
    private ServletConfig config;
    private boolean throwError = false;
    private static final Logger log = LoggerFactory.getLogger(ArtifactoryRestServlet.class);
    private static int threadsNum = 1;
    private Throwable exception = null;
    private CountDownLatch latch = new CountDownLatch(threadsNum);

    @SuppressWarnings({"unchecked"})
    @Override
    public void init(ServletConfig config) throws ServletException {
        this.config = config;
        log.debug("Getting waiters");
        BlockingQueue<Filter> waiters = (BlockingQueue<Filter>) config.getServletContext()
                .getAttribute(APPLICATION_CONTEXT_LOCK_KEY);
        if (waiters != null) {
            log.debug("Setting 'ArtifactoryRestServlet' delayedInit");
            config.getServletContext().setAttribute(ARTIFACTORY_REST_SERVLET_INSTANCE, this);
            waiters.add(this);
        } else {
            log.debug("Waiters are null");
            //Servlet 2.5 lazy filter initing
            delayedInit();
        }
        // register the bean utils converter to fail if encounters a type mismatch between the destination
        // and the original, see RTFACT-3610
        BeanUtilsBean instance = BeanUtilsBean2.getInstance();
        instance.getConvertUtils().register(true, false, 0);
    }

    @Override
    public void startAsyncDelayedInit() {
        CachedThreadPoolTaskExecutor executor = ContextHelper.get().beanForType(CachedThreadPoolTaskExecutor.class);
        executor.submit(() -> {
            try {
                call();
            } catch (Exception | NoClassDefFoundError e) {
                throwError = true;
                exception = e;
                log.error("Failed trying to start HttpServlet", e);
            } finally {
                latch.countDown();
            }
        });
    }

    @Override
    public void delayedInit() {
        try {
            latch.await();
            log.info("Removing 'ArtifactoryRestServlet' delayedInit");
            config.getServletContext().removeAttribute(ARTIFACTORY_REST_SERVLET_INSTANCE);
            if (throwError && exception != null) {
                throwError = false;
                throw new IllegalStateException(exception);
            }
        } catch (InterruptedException e) {
            log.error("delayedInit error", e);
            Thread.currentThread().interrupt();
        }
    }

    private void call() throws ServletException, NoClassDefFoundError {
        super.init(config);
        latch.countDown();
    }
}
