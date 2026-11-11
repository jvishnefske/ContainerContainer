package org.artifactory.security.ssh;

import org.apache.sshd.common.BaseBuilder;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.cipher.Cipher;
import org.apache.sshd.common.kex.BuiltinDHFactories;
import org.apache.sshd.common.kex.DHFactory;
import org.apache.sshd.common.kex.KeyExchangeFactory;
import org.apache.sshd.common.mac.BuiltinMacs;
import org.apache.sshd.common.mac.Mac;
import org.apache.sshd.common.signature.BuiltinSignatures;
import org.apache.sshd.common.signature.Signature;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.security.ssh.algorithms.CipherAlgorithms;
import org.artifactory.security.ssh.algorithms.KeyExchangeAlgorithms;
import org.artifactory.security.ssh.algorithms.MacAlgorithms;
import org.artifactory.security.ssh.algorithms.SignatureAlgorithms;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.apache.sshd.common.BaseBuilder.DEFAULT_SIGNATURE_PREFERENCE;
import static org.apache.sshd.common.cipher.BuiltinCiphers.Constants.AES128_CTR;
import static org.apache.sshd.common.cipher.BuiltinCiphers.Constants.AES192_CTR;
import static org.apache.sshd.common.cipher.BuiltinCiphers.Constants.AES256_CTR;
import static org.apache.sshd.common.cipher.BuiltinCiphers.Constants.ARCFOUR128;
import static org.apache.sshd.common.cipher.BuiltinCiphers.Constants.ARCFOUR256;
import static org.apache.sshd.common.kex.BuiltinDHFactories.Constants.DIFFIE_HELLMAN_GROUP14_SHA1;
import static org.apache.sshd.common.kex.BuiltinDHFactories.Constants.DIFFIE_HELLMAN_GROUP14_SHA256;
import static org.apache.sshd.common.kex.BuiltinDHFactories.Constants.DIFFIE_HELLMAN_GROUP1_SHA1;
import static org.apache.sshd.common.mac.BuiltinMacs.Constants.HMAC_MD5;
import static org.apache.sshd.common.mac.BuiltinMacs.Constants.HMAC_SHA1;
import static org.apache.sshd.common.mac.BuiltinMacs.Constants.HMAC_SHA1_96;
import static org.artifactory.security.ssh.algorithms.MacAlgorithms.DEFAULT_MAC_PREFERENCE;

class SshAuthServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Test
    void testCipherFiltering() {
        List<NamedFactory<Cipher>> ciphers = new CipherAlgorithms().setUpAlgorithms();
        List<String> filteredCipherNames = ciphers.stream().map(NamedFactory::getName).collect(Collectors.toList());
        Assertions.assertFalse(filteredCipherNames.contains(ARCFOUR256));
    }

    @Test
    void testCipherIncludes() {
        List<String> expectedAlgorithms = Arrays.asList(AES128_CTR, AES192_CTR, AES256_CTR, ARCFOUR128);
        String includedByName = String.join(", ", expectedAlgorithms);
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.sshCipherWhitelist.getPropertyName(), includedByName);
        List<NamedFactory<Cipher>> ciphers = new CipherAlgorithms().setUpAlgorithms();
        List<String> includedCiphers = ciphers.stream().map(NamedFactory::getName).collect(Collectors.toList());
        Assertions.assertEquals(includedCiphers, expectedAlgorithms);
    }

    @Test
    void testKeyExchangeFilterDHG1() {
        assertKeyExchange(Collections.singletonList(DIFFIE_HELLMAN_GROUP1_SHA1));
    }

    @Test
    void testKeyExchangeFilterDHG1AndDHG14() {
        assertKeyExchange(Arrays.asList(DIFFIE_HELLMAN_GROUP1_SHA1, DIFFIE_HELLMAN_GROUP14_SHA1));
    }

    @Test
    void testKeyExchangeFilteringDefaults() {
        assertKeyExchange(Collections.singletonList(ConstantValues.sshKeyExchangeBlackList.getString()));
    }

    @Test
    void testKeyExchangeIncludeOnly() {
        assertKeyExchangeIncludes(Arrays.asList(DIFFIE_HELLMAN_GROUP14_SHA256, DIFFIE_HELLMAN_GROUP1_SHA1));
    }

    @Test
    void testMacFilterHMACMD5() {
        assertMessageAuthenticationCodes(Collections.singletonList(HMAC_MD5));
    }

    @Test
    void testMacFilterHMACSHA1AndHMACSHA196() {
        assertMessageAuthenticationCodes(Arrays.asList(HMAC_SHA1, HMAC_SHA1_96));
    }

    @Test
    void testMacFilteringDefaults() {
        assertMessageAuthenticationCodes(Collections.singletonList(ConstantValues.sshMacBlackList.getString()));
    }

    @Test
    void testMacIncludesOnly() {
        assertMessageAuthenticationCodesIncludes(Arrays.asList(HMAC_SHA1, HMAC_SHA1_96));
    }

    @Test
    void testSignatureFilterHMACMD5() {
        assertSignatures(Collections.singletonList(BuiltinSignatures.rsaSHA256.getName()));
    }

    @Test
    void testSignatureFilterHMACSHA1AndHMACSHA196() {
        assertSignatures(Arrays.asList(BuiltinSignatures.rsaSHA256.getName(),
                BuiltinSignatures.rsa.getName()));
    }

    @Test
    void testSignatureFilteringDefaults() {
        assertSignatures(Collections.singletonList(ConstantValues.sshSignatureBlackList.getString()));
    }

    @Test
    void testSignatureIncludesOnly() {
        assertSignaturesIncludes(Arrays.asList(BuiltinSignatures.rsaSHA256.getName(),
                BuiltinSignatures.rsa_cert.getName()));
    }

    private void assertKeyExchange(List<String> excludedMethods) {
        String excludedString;
        if (excludedMethods != null) {
            excludedString = String.join(",", excludedMethods);
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.sshKeyExchangeBlackList.getPropertyName(), excludedString);
        }
        Map<String, DHFactory> avail = BaseBuilder.DEFAULT_KEX_PREFERENCE.stream()
                .collect(Collectors.toMap(BuiltinDHFactories::getName, Function.identity()));

        List<KeyExchangeFactory> keyExchange = new KeyExchangeAlgorithms().setUpAlgorithms();
        List<String> expectedKeyExchangeNames = avail.keySet().stream()
                .filter(r -> excludedMethods == null || !excludedMethods.contains(r))
                .collect(Collectors.toList());
        List<String> actualKeyExchangeNames =
                keyExchange.stream().map(NamedResource::getName).collect(Collectors.toList());
        Assertions.assertEquals(actualKeyExchangeNames, expectedKeyExchangeNames);
    }

    private void assertKeyExchangeIncludes(List<String> includeOnly) {
        String includedString = String.join(",", includeOnly);
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.sshKeyExchangeWhitelist.getPropertyName(), includedString);
        List<KeyExchangeFactory> keyExchange = new KeyExchangeAlgorithms().setUpAlgorithms();
        List<String> actualKeyExchangeNames =
                keyExchange.stream().map(NamedResource::getName).collect(Collectors.toList());
        Assertions.assertEquals(actualKeyExchangeNames, includeOnly);
    }

    private void assertMessageAuthenticationCodes(List<String> excludedMethods) {
        String excludedString = null;
        if (excludedMethods != null) {
            excludedString = String.join(",", excludedMethods);
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.sshMacBlackList.getPropertyName(), excludedString);
        }
        Map<String, NamedFactory<Mac>> avail = DEFAULT_MAC_PREFERENCE.stream()
                .collect(Collectors.toMap(NamedFactory::getName, Function.identity()));
        List<NamedFactory<Mac>> keyExchange = new MacAlgorithms().setUpAlgorithms();
        List<String> expectedMacs = avail.keySet().stream()
                .filter(r -> excludedMethods == null || !excludedMethods.contains(r))
                .collect(Collectors.toList());
        List<String> actualMacs =
                keyExchange.stream().map(NamedFactory::getName).collect(Collectors.toList());
        Assertions.assertEquals(actualMacs, expectedMacs);
    }

    private void assertMessageAuthenticationCodesIncludes(List<String> includeOnly) {
        String includedString = String.join(",", includeOnly);
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.sshMacWhitelist.getPropertyName(), includedString);
        Map<String, NamedFactory<Mac>> avail = Arrays.stream(BuiltinMacs.values())
                .collect(Collectors.toMap(NamedFactory::getName, Function.identity()));
        List<NamedFactory<Mac>> macs = new MacAlgorithms().setUpAlgorithms();
        List<String> actualMacs = macs.stream().map(NamedFactory::getName).collect(Collectors.toList());
        Assertions.assertEquals(actualMacs, includeOnly);
    }

    private void assertSignatures(List<String> excludedMethods) {
        String excludedString;
        if (excludedMethods != null) {
            excludedString = String.join(",", excludedMethods);
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.sshSignatureBlackList.getPropertyName(), excludedString);
        }
        Map<String, BuiltinSignatures> avail = DEFAULT_SIGNATURE_PREFERENCE.stream()
                .filter(BuiltinSignatures::isSupported)
                .collect(Collectors.toMap(BuiltinSignatures::getName, Function.identity()));
        List<NamedFactory<Signature>> signatures = new SignatureAlgorithms().setUpAlgorithms();
        List<String> expectedMacs = avail.entrySet().stream()
                .filter(r -> excludedMethods == null || !excludedMethods.contains(r.getKey()))
                .filter(r -> r.getValue().isSupported())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        List<String> actualMacs =
                signatures.stream().map(NamedFactory::getName).collect(Collectors.toList());
        Assertions.assertEquals(actualMacs, expectedMacs);
    }

    private void assertSignaturesIncludes(List<String> includeOnly) {
        String includedString = String.join(",", includeOnly);
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.sshSignatureWhitelist.getPropertyName(), includedString);
        List<NamedFactory<Signature>> signatures = new SignatureAlgorithms().setUpAlgorithms();
        List<String> actualMacs = signatures.stream().map(NamedFactory::getName).collect(Collectors.toList());
        Assertions.assertEquals(actualMacs, includeOnly);
    }

    @AfterEach
    public void cleanConstants() {
        ArtifactoryHome.get().getArtifactoryProperties().removeProperty(ConstantValues.sshCipherBlackList);
        ArtifactoryHome.get().getArtifactoryProperties().removeProperty(ConstantValues.sshCipherWhitelist);
        ArtifactoryHome.get().getArtifactoryProperties().removeProperty(ConstantValues.sshKeyExchangeBlackList);
        ArtifactoryHome.get().getArtifactoryProperties().removeProperty(ConstantValues.sshKeyExchangeWhitelist);
        ArtifactoryHome.get().getArtifactoryProperties().removeProperty(ConstantValues.sshMacBlackList);
        ArtifactoryHome.get().getArtifactoryProperties().removeProperty(ConstantValues.sshMacWhitelist);
        ArtifactoryHome.get().getArtifactoryProperties().removeProperty(ConstantValues.sshSignatureBlackList);
        ArtifactoryHome.get().getArtifactoryProperties().removeProperty(ConstantValues.sshSignatureWhitelist);
    }

}