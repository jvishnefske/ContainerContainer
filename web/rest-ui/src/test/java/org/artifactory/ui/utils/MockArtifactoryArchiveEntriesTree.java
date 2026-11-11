package org.artifactory.ui.utils;

import com.jfrog.sysconf.SysConfig;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.mime.MimeTypes;
import org.artifactory.mime.MimeTypesReader;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.RestTreeNode;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import jakarta.xml.bind.JAXBException;
import java.io.IOException;

import static org.artifactory.util.evidence.EvidenceSystemYamlConstants.EVIDENCE_NAVIGATION_ENABLED;
import static org.mockito.Mockito.when;

/**
 * @author Alexei Vainshtein
 */
public class MockArtifactoryArchiveEntriesTree {

    @Mock
    protected RepositoryService repoService;

    @Mock
    protected ArtifactoryRestRequest<RestTreeNode> artifactoryRestRequest;

    @Mock
    protected AddonsManager addonsManager;

    @Mock
    protected ArtifactoryContext context;

    @Mock
    protected ArtifactoryHome artifactoryHomeMock;

    @Mock
    protected SysConfig sysConfig;

    private AutoCloseable mocks;

    @BeforeMethod
    public void setup() throws IOException, JAXBException {
        mocks = MockitoAnnotations.openMocks(this);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(context.beanForType(RepositoryService.class)).thenReturn(repoService);

        ArtifactoryContextThreadBinder.bind(context);
        MimeTypes mimeTypes = new MimeTypesReader().read("<mimetypes version=\"12\">\n" +
                "<mimetype type=\"application/x-gzip\" extensions=\"tgz, tar.gz, gz\" archive=\"true\" " +
                "index=\"false\"/>\n" +
                "<mimetype type=\"application/zip\" extensions=\"zip\" archive=\"true\" index=\"true\" " +
                "css=\"jar\"/>\n" +
                "</mimetypes>");
        when(artifactoryHomeMock.getMimeTypes()).thenReturn(mimeTypes);

        when(sysConfig.getBoolean(EVIDENCE_NAVIGATION_ENABLED, false)).thenReturn(false);
        when(artifactoryHomeMock.getSysConfig()).thenReturn(sysConfig);

        ArtifactoryHome.bind(artifactoryHomeMock);
    }

    @AfterMethod(alwaysRun = true)
    public void cleanup() throws Exception {
        ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }
}
