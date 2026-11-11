package org.artifactory.storage.db.converter.markers;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.converter.ArtifactoryConverterAdapter;
import org.artifactory.converter.ConverterType;
import org.artifactory.storage.db.fs.dao.VersionConfigDao;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.ArtifactoryServiceVersionImpl;
import org.artifactory.version.CompoundVersionDetails;
import org.artifactory.version.InstallationType;
import org.artifactory.version.VersionProvider;
import org.jfrog.config.ifc.ConfigurationManager;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Alexei Vainshtein
 */
public class ConverterManagerImplTest extends ArtifactoryHomeBoundTest {

    @Test
    public void testServiceConvertAsync() {
        ConfigurationManager configurationManager = Mockito.mock(ConfigurationManager.class);
        VersionProvider vp = Mockito.mock(VersionProvider.class);
        CompoundVersionDetails compoundVersionDetails = Mockito.mock(CompoundVersionDetails.class);
        Mockito.when(compoundVersionDetails.isCurrent()).thenReturn(true);
        Mockito.when(vp.getOriginalDbVersion()).thenReturn(compoundVersionDetails);
        ConverterManagerImpl converterManager =
                Mockito.spy(new ConverterManagerImpl(homeStub, vp, configurationManager));
        Map<ConverterType, ArrayList<ArtifactoryConverterAdapter>> converters = converterManager.getConverters();
        converters.get(ConverterType.ASYNC).removeAll(converters.get(ConverterType.ASYNC));

        ArtifactoryConverterAdapter converterAdapter = Mockito.mock(ArtifactoryConverterAdapter.class);
        converters.get(ConverterType.ASYNC).add(converterAdapter);

        converterManager.convertAsync();
        Mockito.verify(converterAdapter, Mockito.times(1)).convert(Mockito.any(), Mockito.any());
        Mockito.verify(converterManager, Mockito.times(0)).shouldBlockConvert();

        Mockito.clearInvocations(converterAdapter);
        Mockito.when(compoundVersionDetails.isCurrent()).thenReturn(false);
        converterManager.convertAsync();
        Mockito.verify(converterAdapter, Mockito.times(1)).convert(Mockito.any(), Mockito.any());
        Mockito.verify(converterManager, Mockito.times(1)).shouldBlockConvert();
    }

    @Test
    public void testInit() {
        ConfigurationManager configurationManager = Mockito.mock(ConfigurationManager.class);
        VersionProvider vp = Mockito.mock(VersionProvider.class);
        ConverterManagerImpl converterManager = new ConverterManagerImpl(homeStub, vp, configurationManager);
        Map<ConverterType, ArrayList<ArtifactoryConverterAdapter>> converters = converterManager.getConverters();
        Assert.assertNotNull(converters.get(ConverterType.ASYNC));
    }

    @Test
    public void test() throws SQLException {
        ArtifactoryServiceVersion fromVersion = new ArtifactoryServiceVersionImpl("FROM", 1l, null);
        CompoundVersionDetails version = new CompoundVersionDetails(fromVersion, null, 0L);

        VersionProvider vp = Mockito.mock(VersionProvider.class);
        Mockito.when(vp.getInstallationType()).thenReturn(InstallationType.ROLLBACK);
        Mockito.when(vp.getRunningServiceVersion()).thenReturn(version);

        try (MockedStatic<ContextHelper> contextHelper = Mockito.mockStatic(ContextHelper.class)) {
            ArtifactoryContext artifactoryContext = Mockito.mock(ArtifactoryContext.class);
            VersionConfigDao versionConfigDao = Mockito.mock(VersionConfigDao.class);
            contextHelper.when(ContextHelper::get).thenReturn(artifactoryContext);
            when(artifactoryContext.beanForType(VersionConfigDao.class)).thenReturn(versionConfigDao);

            ConverterManagerImpl converterManager = new ConverterManagerImpl(homeStub, vp, configurationManager);
            converterManager.convertPostInit();

            verify(versionConfigDao, Mockito.times(1))
                    .update("FROM");
        }
    }
}