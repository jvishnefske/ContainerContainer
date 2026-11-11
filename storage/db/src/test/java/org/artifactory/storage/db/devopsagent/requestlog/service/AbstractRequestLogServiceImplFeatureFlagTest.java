package org.artifactory.storage.db.devopsagent.requestlog.service;

import org.artifactory.common.ConstantValues;
import org.artifactory.storage.db.devopsagent.requestlog.entity.RequestLogIdentifiable;
import org.artifactory.storage.db.devopsagent.requestlog.model.SearchCriteria;
import org.artifactory.test.ArtifactoryHomeStub;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.Collections;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Direction-agnostic base for feature-flag tests shared by inbound and outbound request-log service impls.
 * Concrete subclasses supply the service under test and direction-specific hook methods.
 *
 * @param <S> the concrete service type under test
 * @param <R> the row type produced/consumed by the service
 */
abstract class AbstractRequestLogServiceImplFeatureFlagTest<
        S extends AbstractRequestLogServiceImpl<R>,
        R extends RequestLogIdentifiable> {

    static final int QUEUE_CAPACITY = 10;

    ArtifactoryHomeStub homeStub;
    S service;

    @BeforeMethod
    public void setUp() {
        homeStub = new ArtifactoryHomeStub(getClass().getName());
        homeStub.bind();
        service = createService();
    }

    @AfterMethod
    public void tearDown() {
        homeStub.setProperty(ConstantValues.devopsTroubleshootingEnabled, "false");
        ArtifactoryHomeStub.unbind();
    }

    /** Factory: create the concrete service with a mocked DAO and {@link #QUEUE_CAPACITY}. */
    abstract S createService();

    /** Build a row with the given HTTP status code. */
    abstract R rowWithStatus(int statusCode);

    /** Call the direction-specific enqueue method (enqueueInbound or enqueueOutbound). */
    abstract void enqueue(R row);

    /** Call the direction-specific search method (searchInbound or searchOutbound). */
    abstract void doSearch(SearchCriteria criteria);

    /** Stub DAO's {@code insertBatch} to return 1 when called. */
    abstract void stubInsertBatch() throws SQLException;

    /** Stub DAO's {@code search} to return an empty list when called. */
    abstract void stubSearch() throws SQLException;

    /** Verify the DAO's {@code insertBatch} was never called. */
    abstract void verifyInsertBatchNever() throws SQLException;

    /**
     * Verify the DAO's {@code insertBatch} was called exactly once, and return the captured batch size.
     */
    abstract int verifyInsertBatchCalledOnce() throws SQLException;

    /** Verify the DAO's {@code search} was never called. */
    abstract void verifySearchNever() throws SQLException;

    /** Verify the DAO's {@code search} was called at least once. */
    abstract void verifySearchCalled() throws SQLException;

    @Test
    public void isFeatureEnabled_falseWhenPropertyFalse() {
        homeStub.setProperty(ConstantValues.devopsTroubleshootingEnabled, "false");
        assertFalse(service.isFeatureEnabled());
    }

    @Test
    public void isFeatureEnabled_falseByDefaultWhenPropertyUnset() {
        assertFalse(service.isFeatureEnabled());
    }

    @Test
    public void assertFeatureEnabled_throws501WhenDisabled() {
        homeStub.setProperty(ConstantValues.devopsTroubleshootingEnabled, "false");
        RequestLogException ex = expectThrows(RequestLogException.class, service::assertFeatureEnabled);
        assertEquals(ex.getStatusCode(), 501);
        assertTrue(ex.getMessage().contains("disabled by configuration"));
    }

    @Test
    public void enqueueAndFlush_doesNotCallDaoWhenDisabled() throws SQLException {
        homeStub.setProperty(ConstantValues.devopsTroubleshootingEnabled, "false");
        enqueue(rowWithStatus(500));
        service.flush();
        verifyInsertBatchNever();
    }

    @Test
    public void search_throws501WhenDisabled() throws SQLException {
        homeStub.setProperty(ConstantValues.devopsTroubleshootingEnabled, "false");
        RequestLogException ex = expectThrows(RequestLogException.class,
                () -> doSearch(minimalSearchCriteria()));
        assertEquals(ex.getStatusCode(), 501);
        verifySearchNever();
    }

    @Test
    public void isFeatureEnabled_trueWhenPropertyTrue() {
        homeStub.setProperty(ConstantValues.devopsTroubleshootingEnabled, "true");
        assertTrue(service.isFeatureEnabled());
    }

    @Test
    public void enqueueAndFlush_callsDaoWhenEnabled() throws SQLException {
        homeStub.setProperty(ConstantValues.devopsTroubleshootingEnabled, "true");
        stubInsertBatch();
        enqueue(rowWithStatus(500));
        service.flush();
        assertEquals(verifyInsertBatchCalledOnce(), 1);
    }

    @Test
    public void search_delegatesToDaoWhenEnabled() throws SQLException {
        homeStub.setProperty(ConstantValues.devopsTroubleshootingEnabled, "true");
        stubSearch();
        doSearch(minimalSearchCriteria());
        verifySearchCalled();
    }

    @Test
    public void enqueueAndFlush_dropsRowBelow400() throws SQLException {
        homeStub.setProperty(ConstantValues.devopsTroubleshootingEnabled, "true");
        enqueue(rowWithStatus(200));
        service.flush();
        verifyInsertBatchNever();
    }

    static SearchCriteria minimalSearchCriteria() {
        return SearchCriteria.builder()
                .timeFrom(0L)
                .timeTo(1L)
                .minDurationMs(0L)
                .repoNames(Collections.emptyList())
                .limit(100)
                .offset(0)
                .build();
    }
}
