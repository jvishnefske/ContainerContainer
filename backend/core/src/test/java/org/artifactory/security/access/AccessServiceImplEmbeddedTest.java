/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access;

import org.artifactory.addon.AddonsManager;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.access.UserTokenSpec;
import org.artifactory.common.ConstantValues;
import org.artifactory.security.access.embedded.DerbyMemoryDatabase;
import org.artifactory.security.access.embedded.EmbeddedAccess;
import org.artifactory.security.access.embedded.EmbeddedAccessFactory;
import org.artifactory.security.access.embedded.bootstrap.AdminBootstrapService;
import org.artifactory.security.access.embedded.client.EmbeddedAccessClient;
import org.artifactory.security.access.embedded.core.EaOutcome;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.access.client.AccessClientHttpException;
import org.jfrog.access.common.ServiceId;
import org.jfrog.security.crypto.EncryptionWrapperFactory;
import org.jfrog.storage.DbType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * {@link AccessServiceImpl} in embedded mode (the default): {@code init()} installs the embedded client without
 * any Access server, stores its certificate as {@code root.crt}, publishes the bootstrap service in the application
 * context where {@link AdminBootstrapService#lookup} and {@link AccessServiceImpl#isInitialSetupRequired()} find
 * it, refuses the proxy token path, and keeps the same instance across a re-initialization.
 */
class AccessServiceImplEmbeddedTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String SERVICE_ID = "jfrt@01wiringtest";

    @TempDir
    Path security;

    private DerbyMemoryDatabase database;
    private AccessServiceImpl service;
    private DefaultListableBeanFactory beanFactory;
    private ArtifactoryContext context;

    @BeforeEach
    void setUp() {
        homeStub.getArtifactoryProperties().setProperty(ConstantValues.accessEmbeddedEnabled.getPropertyName(),
                "true");
        database = new DerbyMemoryDatabase("ea-wiring");
        beanFactory = new DefaultListableBeanFactory();
        context = mock(ArtifactoryContext.class, withSettings().extraInterfaces(ConfigurableApplicationContext.class,
                ApplicationContext.class));
        when(((ConfigurableApplicationContext) context).getBeanFactory()).thenReturn(beanFactory);
        doAnswer(inv -> beanFactory.getBeansOfType(inv.getArgument(0))).when(context).beansForType(any());
        ConfigsService configs = mock(ConfigsService.class);
        when(configs.getConfig("artifactory.service_id")).thenReturn(SERVICE_ID);
        service = spy(new AccessServiceImpl(mock(ApplicationEventPublisher.class), configs,
                (ApplicationContext) context));
        AddonsManager addons = mock(AddonsManager.class);
        service.setAddonsManager(addons);
        service.setSecurityService(mock(SecurityService.class));
        doAnswer(inv -> EmbeddedAccessFactory.create(new EmbeddedAccessFactory.Settings(
                database.dataSource(), DbType.DERBY, ServiceId.fromFormattedName(SERVICE_ID),
                EncryptionWrapperFactory.aesKeyWrapperFromString("d".repeat(32)), Clock.systemUTC(),
                Duration.ofHours(1),
                port -> AdminBootstrapService.builder(port, security).env(name -> null).audit(c -> { }).build())))
                .when(service).createEmbeddedAccess();
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
        service.destroy();
        database.close();
    }

    @Test
    void initInstallsTheEmbeddedClientAndPublishesTheBootstrapService() {
        service.init();

        EmbeddedAccessClient client = assertInstanceOf(EmbeddedAccessClient.class, service.getAccessClient());
        assertTrue(service.isEmbeddedAccess());
        assertTrue(homeStub.getAccessClientDir().toPath().resolve("keys/root.crt").toFile().isFile(),
                "the embedded certificate is the Access root certificate");
        AdminBootstrapService bootstrap = AdminBootstrapService.lookup(context).orElseThrow();
        assertSame(bootstrap, beanFactory.getBean(AccessServiceImpl.ADMIN_BOOTSTRAP_BEAN_NAME));
        assertFalse(bootstrap.isClaimed());
        assertTrue(service.isInitialSetupRequired(), "an unclaimed server requires the initial setup");

        // stock-only paths are inert or refused
        service.unregisterFromRouterIfNeeded();
        assertThrows(IllegalStateException.class, () -> service.createTokenAsProxy(
                UserTokenSpec.create("admin"), "admin", "password"));
        service.ping();
        service.readiness();

        // a re-initialization with the same service id keeps the instance
        service.initAccessService(null);
        assertSame(client, service.getAccessClient());
        assertSame(bootstrap, AdminBootstrapService.lookup(context).orElseThrow());
    }

    @Test
    void failedCredentialsAreNotRetriedInEmbeddedMode() {
        AtomicInteger calls = new AtomicInteger();
        long start = System.nanoTime();
        assertThrows(AccessClientHttpException.class, () -> service.ensureAuth(() -> {
            calls.incrementAndGet();
            throw new AccessClientHttpException(401, "bad credentials");
        }));
        assertEquals(1, calls.get(), "a 401 means wrong credentials, not an admin token to renew");
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 1000);
    }

    @Test
    void setupTokenFileIsNotInTheMirroredSecurityDirectory() {
        Path dir = AdminBootstrapService.tokenDirectory(homeStub);
        assertEquals(Path.of(homeStub.getSysConfig().helper().getMasterKeyFile()).toAbsolutePath().getParent(), dir);
        assertNotEquals(homeStub.getSecurityDir().toPath().toAbsolutePath(), dir);
    }

    @Test
    void refusedEmbeddedStartStopsStartupWithTheReason() {
        doReturn(EaOutcome.<EmbeddedAccess>refused("Embedded access supports only Derby and PostgreSQL"))
                .when(service).createEmbeddedAccess();
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> service.init());
        assertTrue(failure.getMessage().contains("supports only Derby and PostgreSQL"), failure.getMessage());
    }
}
