package org.artifactory.event;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.event.migration.EventTableRepoKeyMigrationImpl;
import org.artifactory.storage.db.event.dao.eventTableRepoKeyMigration.EventTableRepoKeyMigrationDao;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.common.RetryException;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.quartz.JobExecutionException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.common.ConstantValues.ageOfEventsLogEntriesToDiscardDays;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Test
public class EventTableRepoKeyMigrationTest extends ArtifactoryHomeBoundTest {

    private EventTableRepoKeyMigrationDao eventTableRepoKeyMigrationDao;

    @Mock
    private AddonsManager addonsManager;

    @Mock
    HaAddon haAddon;
    private AutoCloseable mocks;

    @BeforeMethod
    public void init() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryHome
                .get().getArtifactoryProperties()
                .setProperty(ageOfEventsLogEntriesToDiscardDays.getPropertyName(), "0");

        eventTableRepoKeyMigrationDao = mock(EventTableRepoKeyMigrationDao.class);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
        SecurityContextHolder.clearContext();
        mocks.close();
    }

    @Test(description = "eventTableRepoKeyMigrationEnable is true and isHaEnabled is true checkIfMigrationFinished = " +
            "true")
    public void testEventTableRepoKeyMigrationHandlePositive()
            throws SQLException, RetryException {
        addonsManager.addonByType(HaAddon.class);
        when(addonsManager.addonByType(eq(HaAddon.class))).thenReturn(haAddon);
        when(haAddon.isHaEnabled()).thenReturn(true);
        when(eventTableRepoKeyMigrationDao.checkIfMigrationFinished(anyString())).thenReturn(true);
        EventTableRepoKeyMigrationImpl eventTableRepoKeyMigration1 = new EventTableRepoKeyMigrationImpl(addonsManager);
        eventTableRepoKeyMigration1.setEventTableRepoKeyMigrationDaoImpl(eventTableRepoKeyMigrationDao);

        EventTableRepoKeyMigrationImpl spyEventTableRepoKeyMigration = Mockito.spy(eventTableRepoKeyMigration1);
        spyEventTableRepoKeyMigration.eventTableRepoKeyMigrationHandle();
        Mockito.verify(spyEventTableRepoKeyMigration, Mockito.times(0)).doMigration();
    }

    @Test(description = "eventTableRepoKeyMigrationEnable is true and isHaEnabled is true checkIfMigrationFinished = " +
            "false")
    public void testEventTableRepoKeyMigrationHandleNegative()
            throws SQLException, RetryException {
        addonsManager.addonByType(HaAddon.class);
        when(addonsManager.addonByType(eq(HaAddon.class))).thenReturn(haAddon);
        when(haAddon.isHaEnabled()).thenReturn(true);
        when(eventTableRepoKeyMigrationDao.checkIfMigrationFinished(anyString())).thenReturn(false);
        EventTableRepoKeyMigrationImpl eventTableRepoKeyMigration1 = new EventTableRepoKeyMigrationImpl(addonsManager);
        eventTableRepoKeyMigration1.setEventTableRepoKeyMigrationDaoImpl(eventTableRepoKeyMigrationDao);

        EventTableRepoKeyMigrationImpl spyEventTableRepoKeyMigration = Mockito.spy(eventTableRepoKeyMigration1);
        spyEventTableRepoKeyMigration.eventTableRepoKeyMigrationHandle();
        Mockito.verify(spyEventTableRepoKeyMigration, Mockito.times(1)).doMigration();
    }

    @Test(description = "eventTableRepoKeyMigrationEnable is true and isHaEnabled is false checkIfMigrationFinished =" +
            " true")
    public void testEventTableRepoKeyMigrationHandleNegative1()
            throws SQLException, RetryException {
        addonsManager.addonByType(HaAddon.class);
        when(addonsManager.addonByType(eq(HaAddon.class))).thenReturn(haAddon);
        when(haAddon.isHaEnabled()).thenReturn(false);
        when(eventTableRepoKeyMigrationDao.checkIfMigrationFinished(anyString())).thenReturn(true);
        EventTableRepoKeyMigrationImpl eventTableRepoKeyMigration1 = new EventTableRepoKeyMigrationImpl(addonsManager);
        eventTableRepoKeyMigration1.setEventTableRepoKeyMigrationDaoImpl(eventTableRepoKeyMigrationDao);

        EventTableRepoKeyMigrationImpl spyEventTableRepoKeyMigration = Mockito.spy(eventTableRepoKeyMigration1);
        spyEventTableRepoKeyMigration.eventTableRepoKeyMigrationHandle();
        Mockito.verify(spyEventTableRepoKeyMigration, Mockito.times(0)).doMigration();
    }

    @Test(description = "eventTableRepoKeyMigrationEnable is true and isHaEnabled is false checkIfMigrationFinished =" +
            " false")
    public void testEventTableRepoKeyMigrationHandleNegative2()
            throws SQLException, RetryException {
        addonsManager.addonByType(HaAddon.class);
        when(addonsManager.addonByType(eq(HaAddon.class))).thenReturn(haAddon);
        when(haAddon.isHaEnabled()).thenReturn(false);
        when(eventTableRepoKeyMigrationDao.checkIfMigrationFinished(anyString())).thenReturn(false);
        EventTableRepoKeyMigrationImpl eventTableRepoKeyMigration1 = new EventTableRepoKeyMigrationImpl(addonsManager);
        eventTableRepoKeyMigration1.setEventTableRepoKeyMigrationDaoImpl(eventTableRepoKeyMigrationDao);

        EventTableRepoKeyMigrationImpl spyEventTableRepoKeyMigration = Mockito.spy(eventTableRepoKeyMigration1);
        spyEventTableRepoKeyMigration.eventTableRepoKeyMigrationHandle();
        Mockito.verify(spyEventTableRepoKeyMigration, Mockito.times(1)).doMigration();
    }

    @Test(description = "eventTableRepoKeyMigrationEnable is false")
    public void testEventTableRepoKeyMigrationHandleNegative3()
            throws SQLException, RetryException {
        ArtifactoryHome homeMock = mock(ArtifactoryHome.class);
        ArtifactoryHome.bind(homeMock);
        ArtifactorySystemProperties props = mock(ArtifactorySystemProperties.class);
        when(homeMock.getArtifactoryProperties()).thenReturn(props);
        when(props.getBooleanProperty(eq(ConstantValues.eventTableRepoKeyMigrationEnable))).thenReturn(false);

        addonsManager.addonByType(HaAddon.class);
        when(addonsManager.addonByType(eq(HaAddon.class))).thenReturn(haAddon);
        when(haAddon.isHaEnabled()).thenReturn(false);
        when(eventTableRepoKeyMigrationDao.checkIfMigrationFinished(anyString())).thenReturn(false);
        EventTableRepoKeyMigrationImpl eventTableRepoKeyMigration1 = new EventTableRepoKeyMigrationImpl(addonsManager);
        eventTableRepoKeyMigration1.setEventTableRepoKeyMigrationDaoImpl(eventTableRepoKeyMigrationDao);

        EventTableRepoKeyMigrationImpl spyEventTableRepoKeyMigration = Mockito.spy(eventTableRepoKeyMigration1);
        spyEventTableRepoKeyMigration.eventTableRepoKeyMigrationHandle();
        Mockito.verify(spyEventTableRepoKeyMigration, Mockito.times(0)).doMigration();
    }

    @Test(description = "eventTableRepoKeyMigrationEnable is true and throw exception", expectedExceptions =
            RetryException.class)
    public void testEventTableRepoKeyMigrationHandleNegative4()
            throws SQLException, RetryException {
        ArtifactoryHome homeMock = mock(ArtifactoryHome.class);
        ArtifactoryHome.bind(homeMock);
        ArtifactorySystemProperties props = mock(ArtifactorySystemProperties.class);
        when(homeMock.getArtifactoryProperties()).thenReturn(props);
        when(props.getBooleanProperty(eq(ConstantValues.eventTableRepoKeyMigrationEnable))).thenReturn(true);
        addonsManager.addonByType(HaAddon.class);
        when(addonsManager.addonByType(eq(HaAddon.class))).thenReturn(haAddon);
        when(haAddon.isHaEnabled()).thenAnswer(
                (Answer<Boolean>) invocation -> {
                    throw new JobExecutionException();
                });
        when(eventTableRepoKeyMigrationDao.checkIfMigrationFinished(anyString())).thenReturn(false);
        EventTableRepoKeyMigrationImpl eventTableRepoKeyMigration1 = new EventTableRepoKeyMigrationImpl(addonsManager);
        eventTableRepoKeyMigration1.setEventTableRepoKeyMigrationDaoImpl(eventTableRepoKeyMigrationDao);
        EventTableRepoKeyMigrationImpl spyEventTableRepoKeyMigration = Mockito.spy(eventTableRepoKeyMigration1);
        spyEventTableRepoKeyMigration.eventTableRepoKeyMigrationHandle();
        Mockito.verify(spyEventTableRepoKeyMigration, Mockito.times(0)).doMigration();
    }

    @Test(description = "isHaEnabled false, do migration should run all the method")
    public void testDoMigrationPositive() throws SQLException {
        addonsManager.addonByType(HaAddon.class);
        when(addonsManager.addonByType(eq(HaAddon.class))).thenReturn(haAddon);
        when(haAddon.isHaEnabled()).thenReturn(false);
        EventTableRepoKeyMigrationImpl eventTableRepoKeyMigration1 = new EventTableRepoKeyMigrationImpl(addonsManager);
        eventTableRepoKeyMigration1.setEventTableRepoKeyMigrationDaoImpl(eventTableRepoKeyMigrationDao);
        EventTableRepoKeyMigrationImpl spyEventTableRepoKeyMigration = Mockito.spy(eventTableRepoKeyMigration1);
        spyEventTableRepoKeyMigration.doMigration();
        Mockito.verify(eventTableRepoKeyMigrationDao, Mockito.times(1)).addIndexOnRepoAndEventId();
        Mockito.verify(eventTableRepoKeyMigrationDao, Mockito.times(1)).updateRepoInNodeEvent();
        Mockito.verify(eventTableRepoKeyMigrationDao, Mockito.times(1)).finishedMigration();
        Mockito.verify(addonsManager.addonByType(HaAddon.class), Mockito.times(0)).propagateAddColumnMonitorMigration();
    }

    @Test(description = "isHaEnabled true, do migration should run all the method")
    public void testDoMigrationPositive2() throws SQLException {
        addonsManager.addonByType(HaAddon.class);
        when(addonsManager.addonByType(eq(HaAddon.class))).thenReturn(haAddon);
        when(haAddon.isHaEnabled()).thenReturn(true);
        EventTableRepoKeyMigrationImpl eventTableRepoKeyMigration1 = new EventTableRepoKeyMigrationImpl(addonsManager);
        eventTableRepoKeyMigration1.setEventTableRepoKeyMigrationDaoImpl(eventTableRepoKeyMigrationDao);
        EventTableRepoKeyMigrationImpl spyEventTableRepoKeyMigration = Mockito.spy(eventTableRepoKeyMigration1);
        spyEventTableRepoKeyMigration.doMigration();
        Mockito.verify(eventTableRepoKeyMigrationDao, Mockito.times(1)).addIndexOnRepoAndEventId();
        Mockito.verify(eventTableRepoKeyMigrationDao, Mockito.times(1)).updateRepoInNodeEvent();
        Mockito.verify(eventTableRepoKeyMigrationDao, Mockito.times(1)).finishedMigration();
        Mockito.verify(addonsManager.addonByType(HaAddon.class), Mockito.times(1)).propagateAddColumnMonitorMigration();
    }

    @Test
    public void testUpdateDaoRepoColumnAddedToEventTable() {
        addonsManager.addonByType(HaAddon.class);
        when(addonsManager.addonByType(eq(HaAddon.class))).thenReturn(haAddon);
        when(haAddon.isHaEnabled()).thenReturn(true);
        EventTableRepoKeyMigrationImpl eventTableRepoKeyMigration1 = new EventTableRepoKeyMigrationImpl(addonsManager);
        eventTableRepoKeyMigration1.setEventTableRepoKeyMigrationDaoImpl(eventTableRepoKeyMigrationDao);
        EventTableRepoKeyMigrationImpl spyEventTableRepoKeyMigration = Mockito.spy(eventTableRepoKeyMigration1);
        spyEventTableRepoKeyMigration.updateDaoRepoColumnAddedToEventTable();
        Mockito.verify(eventTableRepoKeyMigrationDao, Mockito.times(1)).updateDaoRepoColumnAddedToEventTable();
    }
}