package org.artifactory.version.converter.v319;

import jakarta.annotation.Nonnull;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.util.Files;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jfrog.common.StreamSupportUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.artifactory.descriptor.repo.KeyPairDescriptor.DEFAULT_GPG_KEY;
import static org.artifactory.descriptor.repo.KeyPairDescriptor.GPG_REPO_TYPES;
import static org.artifactory.descriptor.repo.KeyPairDescriptor.GPG_TYPE;

/**
 * @author Omri Ziv
 */
public class GpgToDefaultGpgKeyPairConverter implements XmlConverter {

    private static final Logger log = LoggerFactory.getLogger(GpgToDefaultGpgKeyPairConverter.class);
    private static final String PRIMARY_KEY_PAIR_REF_KEY = "primaryKeyPairRef";
    private static final String KEY_PAIR_REF_KEY = "keyPairRef";
    private static final String PASSPHRASE_KEY = "passphrase";

    @Override
    public void convert(Document doc) {
        log.info("Started {}", this.getClass().getSimpleName());
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        File securityDir = ArtifactoryHome.get().getSecurityDir();
        File publicKey = new File(securityDir, "artifactory.gpg.public");
        File privateKey = new File(securityDir, "artifactory.gpg.private");
        List<Element> repos = getRepositoriesElements(rootElement, namespace);
        renameRepositoriesKeyRef(repos, namespace);
        if (publicKey.exists() || privateKey.exists()) {
            createDefaultKeyPairElement(rootElement, namespace, publicKey, privateKey);
            addPrimaryKeyPairToRepositories(repos, namespace);
        }

        deletePassphraseFromOldPlace(rootElement, namespace);
        log.info("Finished {}", this.getClass().getSimpleName());
    }


    private void renameRepositoriesKeyRef(List<Element> repos, Namespace namespace) {
        log.debug("Converting local repositories");
        StreamSupportUtils.stream(repos)
                .forEach(repository -> {
                    Element keyPairRef = repository.getChild(KEY_PAIR_REF_KEY, namespace);
                    if (keyPairRef != null && StringUtils.isNotBlank(keyPairRef.getText())) {
                        keyPairRef.setName(PRIMARY_KEY_PAIR_REF_KEY);
                    }
                });
    }

    private void addPrimaryKeyPairToRepositories(List<Element> repos, Namespace namespace) {
        log.debug("Converting local and virtual repositories");
        List<String> supportedTypes = GPG_REPO_TYPES.stream()
                .map(RepoType::getType)
                .collect(Collectors.toList());

        StreamSupportUtils.stream(repos)
                .forEach(repository -> {
                    Element keyPairRef = repository.getChild(PRIMARY_KEY_PAIR_REF_KEY, namespace);
                    String type = repository.getChild("type", namespace).getText();
                    if (supportedTypes.contains(type)) {
                        if (keyPairRef == null) {
                            keyPairRef = new Element(PRIMARY_KEY_PAIR_REF_KEY, namespace);
                            appendInPlace(repository, keyPairRef, namespace);
                        }
                        if (CollectionUtils.isEmpty(keyPairRef.getChildren())) {
                            keyPairRef.setText(DEFAULT_GPG_KEY);
                        }
                    }
                });
    }

    @Nonnull
    private List<Element> getRepositoriesElements(Element rootElement, Namespace namespace) {
        Element localRepositories = rootElement.getChild("localRepositories", namespace);
        Element virtualRepositories = rootElement.getChild("virtualRepositories", namespace);
        List<Element> repos = new ArrayList<>();
        if (localRepositories != null) {
            List<Element> children = localRepositories.getChildren("localRepository", namespace);
            if (CollectionUtils.isNotEmpty(children)) {
                repos.addAll(children);
            }
        }
        if (virtualRepositories != null) {
            List<Element> children = virtualRepositories.getChildren("virtualRepository", namespace);
            if (CollectionUtils.isNotEmpty(children)) {
                repos.addAll(children);
            }
        }
        return repos;
    }

    private void createDefaultKeyPairElement(Element rootElement, Namespace namespace, File publicKey,
            File privateKey) {
        String passphrase = Optional.ofNullable(rootElement.getChild("security", namespace))
                .map(element -> element.getChild("signingKeysSettings", namespace))
                .map(element -> element.getChild(PASSPHRASE_KEY, namespace))
                .map(Element::getText)
                .orElse(null);
        Element keyPairsElement = rootElement.getChild("keyPairs", namespace);
        Element keyPairElement = new Element("keyPair", namespace);
        if (keyPairsElement == null) {
            keyPairsElement = new Element("keyPairs", namespace);
            rootElement.addContent(keyPairsElement);
        }
        keyPairElement.addContent(new Element("key", namespace).setText(DEFAULT_GPG_KEY));
        keyPairElement.addContent(new Element("alias", namespace).setText(DEFAULT_GPG_KEY));
        keyPairElement.addContent(new Element("keyType", namespace).setText(GPG_TYPE));
        if (privateKey.exists()) {
            String privateKeyStr = Files.readFileToString(privateKey);
            keyPairElement.addContent(new Element("privateKey", namespace)
                    .setText(CryptoHelper.encryptIfNeeded(ArtifactoryHome.get(), privateKeyStr)));
        } else {
            keyPairElement.addContent(new Element("privateKey", namespace)
                    .setText(StringUtils.EMPTY));
        }
        if (publicKey.exists()) {
            String publicKeyStr = Files.readFileToString(publicKey);
            keyPairElement.addContent(new Element("publicKey", namespace).setText(publicKeyStr));
        } else {
            keyPairElement.addContent(new Element("publicKey", namespace)
                    .setText(StringUtils.EMPTY));
        }
        if (StringUtils.isNotBlank(passphrase)) {
            keyPairElement.addContent(new Element(PASSPHRASE_KEY, namespace)
                    .setText(CryptoHelper.encryptIfNeeded(ArtifactoryHome.get(), passphrase)));
        }
        keyPairsElement.addContent(keyPairElement);
    }

    private void deletePassphraseFromOldPlace(Element rootElement, Namespace namespace) {
        Optional.ofNullable(rootElement.getChild("security", namespace))
                .map(element -> element.getChild("signingKeysSettings", namespace))
                .ifPresent(element -> element.removeChild(PASSPHRASE_KEY, namespace));
    }

    private void appendInPlace(Element repository, Element primaryKeyPairElement, Namespace namespace) {
        appendElementAfterAll(repository, primaryKeyPairElement, namespace,
                "type", "description", "notes", "includesPattern", "excludesPattern",
                "repoLayoutRef", "dockerApiVersion", "forceNugetAuthentication");
    }

    private void appendElementAfterAll(Element appendTo, Element toAppend, Namespace namespace,
            String... elementNamesToAppendAfter) {
        int indexToAppendAfter = getIndexOfLastFoundElement(appendTo, namespace, elementNamesToAppendAfter);
        appendTo.addContent(indexToAppendAfter + 1, toAppend);
    }

    private int getIndexOfLastFoundElement(Element repositoryElement, Namespace namespace, String... elementNames) {
        int index = -1;
        for (String elementName : elementNames) {
            Element child = repositoryElement.getChild(elementName, namespace);
            if (child != null) {
                index = Math.max(index, repositoryElement.indexOf(child));
            }
        }
        return index;
    }


}
