package org.artifactory.security.ssh.algorithms;

import org.apache.sshd.common.BaseBuilder;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.cipher.BuiltinCiphers;
import org.apache.sshd.common.cipher.Cipher;
import org.artifactory.common.ConstantValues;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class CipherAlgorithms extends AbstractAlgorithm<NamedFactory<Cipher>> {

    public static final List<NamedFactory<Cipher>> DEFAULT_CIPHERS_PREFERENCE = BaseBuilder.setUpDefaultCiphers(false);

    @Override
    public List<NamedFactory<Cipher>> setUpAlgorithms() {
        List<NamedFactory<Cipher>> ciphers = super.setUpAlgorithms();
        for (Iterator<NamedFactory<Cipher>> i = ciphers.iterator(); i.hasNext(); ) {
            final NamedFactory<Cipher> f = i.next();
            try {
                final Cipher c = f.create();
                final byte[] key = new byte[c.getCipherBlockSize()];
                final byte[] iv = new byte[c.getIVSize()];
                c.init(Cipher.Mode.Encrypt, key, iv);
            } catch (Exception e) {
                i.remove();
            }
        }
        return ciphers;
    }

    @Override
    List<String> getInclude() {
        return split(ConstantValues.sshCipherWhitelist.getString());
    }

    @Override
    List<String> getExclude() {
        return split(ConstantValues.sshCipherBlackList.getString());
    }

    @Override
    List<NamedFactory<Cipher>> getAll() {
        return Arrays.asList(BuiltinCiphers.values());
    }

    @Override
    List<NamedFactory<Cipher>> getDefaults() {
        return DEFAULT_CIPHERS_PREFERENCE;
    }
}
