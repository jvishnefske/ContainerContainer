package org.artifactory.version.converter.v353;

import org.artifactory.version.converter.BasicAddDefaultLayoutConverter;
import org.artifactory.version.converter.RepoLayoutTypeMigrationHelper;
import org.jdom2.Document;
import org.jdom2.Element;

import static org.artifactory.util.RepoLayoutUtils.AGENTPLUGINS_DEFAULT_NAME;

/**
 * @author Linoy Levi
 */
public class AddAgentPluginsDefaultLayoutConverter extends BasicAddDefaultLayoutConverter {

    private static final String AGENTPLUGINS_TYPE = "agentplugins";

    public AddAgentPluginsDefaultLayoutConverter() {
        super(AGENTPLUGINS_DEFAULT_NAME, "[module]/[version]/[module]-[version].[ext]", "false", null, ".*", ".*");
    }

    @Override
    public void convert(Document doc) {
        super.convert(doc);
        Element rootElement = doc.getRootElement();
        RepoLayoutTypeMigrationHelper.migrateExistingRepos(rootElement, rootElement.getNamespace(), AGENTPLUGINS_TYPE,
                AGENTPLUGINS_DEFAULT_NAME);
    }
}
