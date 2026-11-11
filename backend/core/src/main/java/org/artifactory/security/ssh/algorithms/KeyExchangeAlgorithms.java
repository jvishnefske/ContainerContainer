package org.artifactory.security.ssh.algorithms;

import org.apache.sshd.common.kex.BuiltinDHFactories;
import org.apache.sshd.common.kex.DHFactory;
import org.apache.sshd.common.kex.KeyExchangeFactory;
import org.apache.sshd.server.kex.DHGEXServer;
import org.apache.sshd.server.kex.DHGServer;
import org.artifactory.common.ConstantValues;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.sshd.common.BaseBuilder.DEFAULT_KEX_PREFERENCE;

public class KeyExchangeAlgorithms extends AbstractAlgorithm<KeyExchangeFactory> {

    @Override
    List<String> getInclude() {
        return split(ConstantValues.sshKeyExchangeWhitelist.getString());
    }

    @Override
    List<String> getExclude() {
        return split(ConstantValues.sshKeyExchangeBlackList.getString());
    }

    @Override
    List<KeyExchangeFactory> getAll() {
        return Arrays.stream(BuiltinDHFactories.values())
                .map(KeyExchangeAlgorithms::toKexFactory)
                .collect(Collectors.toList());
    }

    @Override
    List<KeyExchangeFactory> getDefaults() {
        return DEFAULT_KEX_PREFERENCE.stream()
                .filter(BuiltinDHFactories::isSupported)
                .map(KeyExchangeAlgorithms::toKexFactory)
                .collect(Collectors.toList());
    }

    private static KeyExchangeFactory toKexFactory(DHFactory factory) {
        if (factory == null) {
            return null;
        }
        return (factory.isGroupExchange()) ? DHGEXServer.newFactory(factory) : DHGServer.newFactory(factory);
    }
}
