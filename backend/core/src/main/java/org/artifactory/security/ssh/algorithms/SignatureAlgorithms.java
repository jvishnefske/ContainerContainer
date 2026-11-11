package org.artifactory.security.ssh.algorithms;

import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.signature.BuiltinSignatures;
import org.apache.sshd.common.signature.Signature;
import org.artifactory.common.ConstantValues;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.sshd.common.BaseBuilder.DEFAULT_SIGNATURE_PREFERENCE;

public class SignatureAlgorithms extends AbstractAlgorithm<NamedFactory<Signature>> {

    @Override
    List<String> getInclude() {
        return split(ConstantValues.sshSignatureWhitelist.getString());
    }

    @Override
    List<String> getExclude() {
        return split(ConstantValues.sshSignatureBlackList.getString());
    }

    @Override
    List<NamedFactory<Signature>> getAll() {
        return Arrays.stream(BuiltinSignatures.values())
                .collect(Collectors.toList());
    }

    @Override
    List<NamedFactory<Signature>> getDefaults() {
        return DEFAULT_SIGNATURE_PREFERENCE.stream()
                .filter(BuiltinSignatures::isSupported)
                .collect(Collectors.toList());
    }
}
