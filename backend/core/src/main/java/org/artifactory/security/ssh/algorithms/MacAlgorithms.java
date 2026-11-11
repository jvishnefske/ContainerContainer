package org.artifactory.security.ssh.algorithms;

import org.apache.sshd.common.BaseBuilder;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.mac.BuiltinMacs;
import org.apache.sshd.common.mac.Mac;
import org.artifactory.common.ConstantValues;

import java.util.Arrays;
import java.util.List;

public class MacAlgorithms extends AbstractAlgorithm<NamedFactory<Mac>> {

    public static final List<NamedFactory<Mac>> DEFAULT_MAC_PREFERENCE = BaseBuilder.setUpDefaultMacs(false);

    @Override
    List<String> getInclude() {
        return split(ConstantValues.sshMacWhitelist.getString());
    }

    @Override
    List<String> getExclude() {
        return split(ConstantValues.sshMacBlackList.getString());
    }

    @Override
    List<NamedFactory<Mac>> getAll() {
        return Arrays.asList(BuiltinMacs.values());
    }

    @Override
    List<NamedFactory<Mac>> getDefaults() {
        return DEFAULT_MAC_PREFERENCE;
    }
}
