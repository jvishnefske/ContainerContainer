package org.artifactory.version.converter.v3126;


import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Content;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.Text;

/**
 * @author Shahaf Golan
 */
@Slf4j
public class NugetSymbolServerUrlConverter implements XmlConverter {

    private static final String NUGET_OBJECT_DESCRIPTOR = "nuget";
    private static final String SYMBOL_SERVER_URL_FIELD = "symbolServerUrl";
    private static final String REMOTE_REPOSITORIES = "remoteRepositories";
    private static final String KEY_FIELD = "key";
    private static final String TYPE_FIELD = "type";
    private static final String V3_FEED_FIELD = "v3FeedUrl";
    private static final String SYMBOL_SERVER_URL = "https://symbols.nuget.org/download/symbols";
    private static final String SEPARATE_SPACE = "    ";
    private static final String SEPARATE_LINE_SPACE = "\n            ";

    @Override
    public void convert(Document doc) {
        log.info("Starting NuGet 'SymbolServerUrlConverter' conversion");
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element remoteRepos = rootElement.getChild(REMOTE_REPOSITORIES, namespace);
        if (remoteRepos == null || remoteRepos.getChildren() == null || remoteRepos.getChildren().isEmpty()) {
            log.info("Finished NuGet 'SymbolServerUrlConverter' conversion (no changed made)");
            return;
        }
        remoteRepos.getChildren().stream().filter(repoElement -> StringUtils
                .equals(repoElement.getChildText(TYPE_FIELD, namespace), NUGET_OBJECT_DESCRIPTOR))
                .forEach(nugetRepo -> addSymbolServerUrl(namespace, nugetRepo));
        log.info("Finished NuGet 'SymbolServerUrlConverter' conversion");
    }

    private void addSymbolServerUrl(Namespace namespace, Element nugetRepo) {
        String repoKey = nugetRepo.getChildText(KEY_FIELD, namespace);
        log.info("Repo {} doesn't have 'SymbolServerUrl' config. Adding.", repoKey);
        Element nugetRepoConfig = nugetRepo.getChild(NUGET_OBJECT_DESCRIPTOR, namespace);
        if (nugetRepoConfig == null) {
            log.info("Couldn't find <nuget> section. Skipping repo {}", repoKey);
            return;
        }
        int index = findLastLocation(nugetRepoConfig, V3_FEED_FIELD);
        nugetRepoConfig.addContent(index + 1, new Text(SEPARATE_SPACE));
        Content symbolServerUrl = buildSymbolServerUrl(namespace);
        nugetRepoConfig.addContent(index + 2, symbolServerUrl);
        nugetRepoConfig.addContent(index + 3, new Text(SEPARATE_LINE_SPACE));
    }

    private Content buildSymbolServerUrl(Namespace namespace) {
        Element symbolServerElement = new Element(SYMBOL_SERVER_URL_FIELD, namespace);
        symbolServerElement.addContent(SYMBOL_SERVER_URL);
        return symbolServerElement;
    }
}