package org.artifactory.addon.common.keys;

import org.artifactory.api.rest.keys.KeyPairModel;
import org.artifactory.descriptor.repo.KeyPairDescriptor;
import org.artifactory.spring.ReloadableBean;

import java.util.Collection;
import java.util.List;

/**
 * @author Omri Ziv
 */
public interface KeyPairService extends ReloadableBean {

    /**
     * Creating a key Pair and adding it to the Artifactory config descriptor
     * @param keyPairModel is the data structure holds the key pair data
     */
    void createKeyPair(KeyPairModel keyPairModel);

    /**
     * Creating a key Pair and adding it to the Artifactory config descriptor
     * @return a data structure holds the key pair data
     */
    KeyPairDescriptor createDefaultKeyPair();

    /**
     * Updating a key Pair on Artifactory config descriptor
     * @param keyPairModel is the data structure holds the key pair data
     */
    void updateKeyPair(KeyPairModel keyPairModel);

    /**
     * Removing a key Pair from Artifactory
     * @param name is name of the Key Pair to remove
     */
    void deleteKeyPair(String name);

    /**
     * Removing multiple key Pairs from Artifactory
     * @param names is names of the Key Pairs to remove
     */
    void deleteKeyPairs(Collection<String> names);

    /**
     * Retrieve a key pair stored in artifactory
     * @param name is names of the Key Pairs to get
     * @return a data structure holds the key pair data
     */
    KeyPairDescriptor getKeyPairByName(String name);

    /**
     * Checking if a repository has private key
     * @param repoKey if the name of the repository
     * @return true if the repo contains primary key pair
     */
    boolean hasPrivateKey(String repoKey);

    /**
     * Verifying If key pair is valid
     * @param keyPairModel is the model
     * @return true if valid
     */
    boolean verifyKeyPairModel(KeyPairModel keyPairModel);

    /**
     * Verifying If key pair is valid
     * @param keyPairDescriptor is the key pair descriptor
     * @return true if valid
     */
    boolean verifyKeyPair(KeyPairDescriptor keyPairDescriptor);

    /**
     * Verifying If key pair is valid
     * @param keyPairDescriptor is the key pair descriptor
     * @param passphrase is the given passphrase
     * @return true if valid
     */
    boolean verifyKeyPair(KeyPairDescriptor keyPairDescriptor, String passphrase);

    /**
     * Verifying If key pair is valid with given passphrase
     * @param keyPairName is the name of the key pair.
     * @param passphrase is the given passphrase
     * @return true if valid
     */
    boolean verifyKeyPairByName(String keyPairName, String passphrase);

    /**
     * Getting the primary Key Pair of a repository
     * @param repoKey is the repository name
     * @return a descriptor of the primary key pair.
     */
    KeyPairDescriptor getPrimaryKeyPairDescriptorByRepoKey(String repoKey);

    /**
     * Getting all the Key Pairs in artifactory
     * @return list of key pair descriptors
     */
    List<KeyPairDescriptor> getKeyPairs(Collection<String> types);

    KeyPairDescriptor getOrCreateDefaultKeyPair();
}
