package org.artifactory.storage.db.aql.service.decorator;

import org.artifactory.aql.AqlException;
import org.artifactory.aql.model.AqlComparatorEnum;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.model.AqlField;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.model.AqlQueryRepoType;
import org.artifactory.aql.model.AqlRepoProvider;
import org.artifactory.aql.model.AqlValue;
import org.artifactory.aql.model.AqlVariableTypeEnum;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.storage.db.aql.itest.service.AqlAbstractServiceTest;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.aql.Criterion;
import org.artifactory.storage.db.aql.sql.builder.query.aql.SimpleCriterion;
import org.artifactory.storage.db.aql.sql.builder.query.aql.TransitiveMetadata;
import org.artifactory.storage.db.aql.sql.builder.query.sql.SqlTable;
import org.artifactory.storage.db.aql.sql.model.SqlTableEnum;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * @author Tomer Slutsky
 */
public class RemoteRepoDecoratorTest extends ArtifactoryHomeBoundTest {
    private final RemoteRepoDecorator decorator = new RemoteRepoDecorator();
    private AqlQuery aqlQuery;

    @BeforeMethod
    public void before() {
        bindArtifactoryHome();
        this.aqlQuery = new AqlQuery<>();
        Criterion originalCriterion = new SimpleCriterion(
                Collections.singletonList(AqlDomainEnum.items),
                new AqlField(AqlPhysicalFieldEnum.itemRepo),
                new SqlTable(SqlTableEnum.nodes),
                AqlComparatorEnum.equals.getSignature(),
                new AqlValue(AqlVariableTypeEnum.string, "repo1"),
                new SqlTable(SqlTableEnum.nodes), false);
        aqlQuery.getElementsForNative().add(originalCriterion);
        TransitiveMetadata details = new TransitiveMetadata();
        details.setTransitiveDepth(1);
        details.setTransitive(true);
        aqlQuery.setTransitiveMetadata(details);
    }

    @AfterMethod
    public void after() {
        unbindArtifactoryHome();
    }

    @Test
    public void testRemoteRepoNotFound() {
        AqlRepoProvider providerMock = Mockito.mock(AqlRepoProvider.class);
        when(providerMock.getRemoteRepoDescriptor("repo1")).thenReturn(null);
        this.decorator.decorate(this.aqlQuery,
                new AqlQueryDecoratorContext(providerMock, new AqlAbstractServiceTest.AdminPermissions()));
        assertEquals(this.aqlQuery.getTransitiveMetadata().getTransitiveDepth(),
                1);
        assertEquals(this.aqlQuery.getQueryRepoType(), AqlQueryRepoType.local);
    }

    @Test
    public void testRemoteRepoOffline() {
        AqlRepoProvider providerMock = Mockito.mock(AqlRepoProvider.class);
        RemoteRepoDescriptor descriptorMock = Mockito.mock(RemoteRepoDescriptor.class);
        when(providerMock.getRemoteRepoDescriptor("repo1")).thenReturn(descriptorMock);
        when(providerMock.isRemoteRepoOffline("repo1")).thenReturn(true);
        this.decorator.decorate(this.aqlQuery,
                new AqlQueryDecoratorContext(providerMock, new AqlAbstractServiceTest.AdminPermissions()));
        assertEquals(this.aqlQuery.getTransitiveMetadata().getTransitiveDepth(),
                1);
        assertEquals(this.aqlQuery.getQueryRepoType(), AqlQueryRepoType.local);
    }

    @Test(expectedExceptions = AqlException.class)
    public void testRemoteRepoNotTransitiveValid() {
        AqlRepoProvider providerMock = Mockito.mock(AqlRepoProvider.class);
        RemoteRepoDescriptor descriptorMock = Mockito.mock(RemoteRepoDescriptor.class);
        when(providerMock.getRemoteRepoDescriptor("repo1")).thenReturn(descriptorMock);
        when(providerMock.isRemoteRepoOffline("repo1")).thenReturn(false);
        when(providerMock.isRepoTransitiveValid("repo1")).thenReturn(false);
        this.decorator.decorate(this.aqlQuery,
                new AqlQueryDecoratorContext(providerMock, new AqlAbstractServiceTest.AdminPermissions()));
    }

    @Test
    public void testRemoteTransitiveIsValid() {
        AqlRepoProvider providerMock = Mockito.mock(AqlRepoProvider.class);
        RemoteRepoDescriptor descriptorMock = Mockito.mock(RemoteRepoDescriptor.class);
        when(providerMock.getRemoteRepoDescriptor("repo1")).thenReturn(descriptorMock);
        when(providerMock.isRemoteRepoOffline("repo1")).thenReturn(false);
        when(providerMock.isRepoTransitiveValid("repo1")).thenReturn(true);
        this.decorator.decorate(this.aqlQuery,
                new AqlQueryDecoratorContext(providerMock, new AqlAbstractServiceTest.AdminPermissions()));
        assertEquals(this.aqlQuery.getTransitiveMetadata().getTransitiveDepth(),
                0);
        assertEquals(this.aqlQuery.getQueryRepoType(), AqlQueryRepoType.remote);
    }

    @Test
    public void testRemoteTransitiveIsFalse() {
        AqlRepoProvider providerMock = Mockito.mock(AqlRepoProvider.class);
        this.aqlQuery.getTransitiveMetadata().setTransitive(false);
        this.decorator.decorate(this.aqlQuery,
                new AqlQueryDecoratorContext(providerMock, new AqlAbstractServiceTest.AdminPermissions()));
        assertEquals(this.aqlQuery.getTransitiveMetadata().getTransitiveDepth(),
                1);
        assertEquals(this.aqlQuery.getQueryRepoType(), AqlQueryRepoType.local);
    }
}
