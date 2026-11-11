package org.artifactory.storage.db.upgrades.common;

import org.artifactory.aol.AolService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.storage.db.init.CommonDataSourceFactory;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.CompoundVersionDetails;
import org.mockito.Mock;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.testng.Assert.fail;

/**
 * @author Alexei Vainshtein
 */
public abstract class AsyncUpgradeBaseTest extends UpgradeBaseTest {
    @Mock
    protected ArtifactoryContext context;
    @Mock
    private ArtifactoryTracer tracer;
    @Mock
    private AolService aolService;
    private DatabaseTestConverterAdapter asyncDatabaseConverterAdapter;
    private AutoCloseable autoCloseable;

    @BeforeClass
    @Override
    protected void springTestContextPrepareTestInstance() throws Exception {
        super.springTestContextPrepareTestInstance();
        asyncDatabaseConverterAdapter = new DatabaseTestConverterAdapter(ArtifactoryHome.get());
        autoCloseable = openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        rollBackToInitialState();
        runAsyncConversion();
        waitForAsyncToFinish();
    }

    @AfterClass
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }

    protected void runAsyncConversion() {
        when(tracer.getCurrentThreadActiveSpan()).thenReturn(null);
        when(context.beanForType(ArtifactoryTracer.class)).thenReturn(tracer);
        when(aolService.isAol()).thenReturn(false);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        when(context.beanForType(org.jfrog.storage.JdbcHelper.class))
                .thenReturn(new ArtifactoryJdbcHelper(CommonDataSourceFactory.createDataSource(dbProperties, false)));
        when(context.getArtifactoryHome()).thenReturn(ArtifactoryHome.get());
        final CompoundVersionDetails source =
                new CompoundVersionDetails(ArtifactoryServiceVersion.getCurrent(), "", System.currentTimeMillis());
        asyncDatabaseConverterAdapter.convert(source, source);
    }

    protected void waitForAsyncToFinish() throws InterruptedException {
        if (!asyncDatabaseConverterAdapter.getScheduledExecutorService().awaitTermination(1, TimeUnit.MINUTES)) {
            fail("Executor wasn't terminated after 1 minute. Failing the test.");
        }
    }

    /**
     * Sets SaaS (AOL) mode for converter execution
     */
    protected void setSaaSMode(boolean saas) {
        when(aolService.isAol()).thenReturn(saas);
    }

}
