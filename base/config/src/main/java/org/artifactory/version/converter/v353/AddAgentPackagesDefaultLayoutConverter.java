package org.artifactory.version.converter.v353;

import org.artifactory.version.converter.BasicAddDefaultLayoutConverter;
import org.artifactory.version.converter.RepoLayoutTypeMigrationHelper;
import org.jdom2.Document;
import org.jdom2.Element;

import static org.artifactory.util.RepoLayoutUtils.AGENTPACKAGES_DEFAULT_NAME;

/**
 * @author Linoy Levi
 */
public class AddAgentPackagesDefaultLayoutConverter extends BasicAddDefaultLayoutConverter {

    private static final String AGENTPACKAGES_TYPE = "agentpackages";

    public AddAgentPackagesDefaultLayoutConverter() {
        super(AGENTPACKAGES_DEFAULT_NAME, "[scope]/[package-name]/[package-name]-[version].zip", "false", null,
                ".*", ".*");
    }

    @Override
    public void convert(Document doc) {
        super.convert(doc);
        Element rootElement = doc.getRootElement();
        RepoLayoutTypeMigrationHelper.migrateExistingRepos(rootElement, rootElement.getNamespace(), AGENTPACKAGES_TYPE,
                AGENTPACKAGES_DEFAULT_NAME);
    }
}
