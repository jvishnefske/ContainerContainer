package org.artifactory.projects;

import org.apache.commons.lang3.builder.DiffBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.artifactory.api.security.SecurityService;
import org.artifactory.config.CentralConfigKey;
import org.jfrog.common.config.diff.DiffMerger;
import org.mockito.Mock;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author BarakH
 */
@Test
public class ProjectAdminValidationTest {

    private static final Set<CentralConfigKey> ALLOWED_KEYS = Set.of(CentralConfigKey.repoLayouts,
            CentralConfigKey.backups,
            CentralConfigKey.localReplications,
            CentralConfigKey.remoteReplications);

    private ProjectAdminValidation projectAdminValidator = null;

    @Mock
    private SecurityService securityService;
    private AutoCloseable mocks;

    @DataProvider
    public static Object[][] provideFieldNames() {
        List<Object[]> list = Stream.of(CentralConfigKey.values())
                .map(centralConfigKey ->
                        new Object[] {
                                centralConfigKey.getKey(),
                                ALLOWED_KEYS.contains(centralConfigKey)
                        }
                )
                .collect(Collectors.toList());
        list.add(new Object[] {"", false});
        Object[][] result = new Object[list.size()][2];
        list.toArray(result);
        return result;
    }

    @BeforeClass
    public void setUp() throws SQLException {
        mocks = openMocks(this);
        projectAdminValidator = new ProjectAdminValidation(securityService);
    }

    @AfterClass
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Test(dataProvider = "provideFieldNames")
    public void testIsProjectAdminAllowedToSaveConfigChanges(String fieldName, boolean shouldBeAllowed) {
        when(securityService.isAdmin()).thenReturn(false);
        DiffBuilder<?> db = new DiffBuilder<>("a", "b", ToStringStyle.DEFAULT_STYLE, false);
        db.append(fieldName, "a", "b");
        if (!shouldBeAllowed) {
            Assert.assertThrows(IllegalArgumentException.class,
                    () -> projectAdminValidator.validate(DiffMerger.diffToDataDiff(db.build().getDiffs())));
        } else {
            projectAdminValidator.validate(DiffMerger.diffToDataDiff(db.build().getDiffs()));
        }
    }

    @Test(dataProvider = "provideFieldNames")
    public void testIsAdminAllowedToSaveConfigChanges(String fieldName, boolean shouldBeAllowed) {
        when(securityService.isAdmin()).thenReturn(true);
        DiffBuilder<?> db = new DiffBuilder<>("a", "b", ToStringStyle.DEFAULT_STYLE, false);
        db.append(fieldName, "a", "b");
        projectAdminValidator.validate(DiffMerger.diffToDataDiff(db.build().getDiffs()));
    }

    @Test
    public void testMcScopeAllowedToSaveConfigChanges() {
        when(securityService.hasMcScope()).thenReturn(true);
        DiffBuilder<?> db = new DiffBuilder<>("a", "b", ToStringStyle.DEFAULT_STYLE, false);
        db.append("", "a", "b");
        projectAdminValidator.validate(DiffMerger.diffToDataDiff(db.build().getDiffs()));
    }

}
