package org.artifactory.sensitive.fields;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.crypto.CryptoService;
import org.artifactory.api.sensitive.SensitiveCustomHttpHeaderValueDecryptor;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.RepoConfigCopyFactory;
import org.artifactory.util.LogUtil;
import org.artifactory.util.StopWatchUtil;
import org.jfrog.security.crypto.EncryptionWrapper;
import org.slf4j.event.Level;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Walks object graphs for fields marked with {@link org.artifactory.sensitive.Sensitive} or
 * {@link org.artifactory.sensitive.DynamicSensitive} and delegates work to injected {@link SensitiveFieldHandler}
 * implementations. Encryption is skipped when no Artifactory key file exists (mirrors legacy interceptor behavior).
 * <p>
 * {@link CryptoService} collection methods operate on defensive copies of {@link RepoConfig} roots.
 *
 * @author Fawaz Atwa
 */
@Slf4j
@Component
public class CryptoServiceImpl implements SensitiveCustomHttpHeaderValueDecryptor, CryptoService {

    private final List<SensitiveFieldHandler> sensitiveFieldHandlers;

    private ArtifactoryHome artifactoryHome;

    public CryptoServiceImpl(List<SensitiveFieldHandler> sensitiveFieldHandlers) {
        this.sensitiveFieldHandlers = List.copyOf(sensitiveFieldHandlers);
    }

    private static ArtifactoryHome resolveArtifactoryHome() {
        if (ArtifactoryHome.isBound()) {
            return ArtifactoryHome.get();
        }
        var context = ContextHelper.get();
        return context != null ? context.getArtifactoryHome() : null;
    }

    @PostConstruct
    public void init() {
        this.artifactoryHome = resolveArtifactoryHome();
    }

    private ArtifactoryHome getArtifactoryHome() {
        if (artifactoryHome == null) {
            artifactoryHome = resolveArtifactoryHome();
        }
        return artifactoryHome;
    }

    @VisibleForTesting
    void validate(Object root) {
        validateDescriptors(discoverSensitiveFields(root));
    }

    @Override
    public void encryptSensitiveFields(Collection<?> objects) {
        Objects.requireNonNull(objects, "objects");
        for (Object object : objects) {
            if (object != null) {
                encryptSensitiveFields(object);
            }
        }
    }

    @Override
    public void encryptSensitiveFields(Object root) {
        if (!isArtifactoryKeyAvailable()) {
            log.debug("Skipping sensitive-field encryption: no Artifactory key present");
            return;
        }
        List<SensitiveFieldDescriptor> descriptors = discoverSensitiveFields(root);
        validateDescriptors(descriptors);
        if (descriptors.isEmpty()) {
            return;
        }
        for (SensitiveFieldDescriptor descriptor : descriptors) {
            resolveHandler(descriptor).encrypt(descriptor);
        }
    }

    /**
     * Re-encrypts after key rotation: validate once, decrypt with the current key, then re-encrypt with the pending
     * wrapper (avoids duplicate validation and discovery compared to separate decrypt + rotation calls).
     */
    public void reEncryptSensitiveFields(Object root, EncryptionWrapper newArtifactoryWrapper) {
        List<SensitiveFieldDescriptor> descriptors = discoverSensitiveFields(root);
        validateDescriptors(descriptors);
        if (descriptors.isEmpty()) {
            return;
        }
        Objects.requireNonNull(newArtifactoryWrapper, "newArtifactoryWrapper");
        for (SensitiveFieldDescriptor descriptor : descriptors) {
            SensitiveFieldHandler h = resolveHandler(descriptor);
            h.decrypt(descriptor);
            h.encryptForKeyRotation(newArtifactoryWrapper, descriptor);
        }
    }

    public void decryptSensitiveFields(Object root) {
        List<SensitiveFieldDescriptor> descriptors = discoverSensitiveFields(root);
        validateDescriptors(descriptors);
        for (SensitiveFieldDescriptor descriptor : descriptors) {
            resolveHandler(descriptor).decrypt(descriptor);
        }
    }

    public void maskSensitiveFields(Object root) {
        List<SensitiveFieldDescriptor> descriptors = discoverSensitiveFields(root);
        validateDescriptors(descriptors);
        for (SensitiveFieldDescriptor descriptor : descriptors) {
            resolveHandler(descriptor).mask(descriptor);
        }
    }

    @Override
    public String decryptStoredValue(String storedValue) {
        if (StringUtils.isBlank(storedValue)) {
            return storedValue;
        }
        ArtifactoryHome home = getArtifactoryHome();
        if (home == null) {
            return storedValue;
        }
        return CryptoHelper.decryptIfNeeded(home, storedValue);
    }

    @Override
    public <T> List<T> decrypt(Collection<T> objects) {
        return copyAndProcess(objects, this::decryptSensitiveFields);
    }

    @Override
    public <T> List<T> mask(Collection<T> objects) {
        return copyAndProcess(objects, this::maskSensitiveFields);
    }

    @Override
    public <T> List<T> reEncrypt(Collection<T> objects, EncryptionWrapper encryptionWrapper) {
        Objects.requireNonNull(encryptionWrapper, "encryptionWrapper");
        if (!ArtifactoryHome.isBound() || !CryptoHelper.hasArtifactoryKey(ArtifactoryHome.get())) {
            throw new IllegalStateException("Cannot reEncrypt: Artifactory encryption key not available");
        }
        StopWatch stopWatch = new StopWatch();
        List<T> result;
        try {
            result = StopWatchUtil.measureTime(stopWatch, () -> copyAndProcess(objects,
                    obj -> reEncryptSensitiveFields(obj, encryptionWrapper)));
        } catch (Exception e) {
            String errMsg = "ReEncrypt failed for objects: " + keysSummary(objects);
            log.error(errMsg, e);
            throw new IllegalStateException(errMsg, e);
        }
        LogUtil.log(log, Level.DEBUG, "Sensitive object re-encrypt took: {}ms", stopWatch.getTotalTimeMillis());
        return result;
    }

    private <T> List<T> copyAndProcess(Collection<T> objects, Consumer<T> processor) {
        if (objects == null) {
            return List.of();
        }
        List<T> result = new ArrayList<>(objects.size());
        for (T object : objects) {
            if (object == null) {
                result.add(null);
                continue;
            }
            T copy = copyForCrypto(object);
            processor.accept(copy);
            result.add(copy);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T> T copyForCrypto(T object) {
        Objects.requireNonNull(object, "object");
        if (object instanceof RepoConfig rc) {
            RepoConfig repoCopy = RepoConfigCopyFactory.copy(rc);
            if (repoCopy == null) {
                throw new IllegalStateException(
                        "RepoConfigCopyFactory.copy returned null for key: " + rc.getKey());
            }
            return (T) repoCopy;
        }
        throw new IllegalArgumentException(
                "Crypto copy is only supported for " + RepoConfig.class.getName() + ", got: "
                        + object.getClass().getName());
    }

    private static String keysSummary(Collection<?> objects) {
        if (objects == null) {
            return "[]";
        }
        return objects.stream()
                .filter(o -> o instanceof RepoConfig)
                .map(o -> ((RepoConfig) o).getKey())
                .collect(Collectors.joining(", ", "[", "]"));
    }

    /**
     * Discovers all sensitive fields by traversing the object graph from {@code root}.
     * Includes fields declared on superclasses. Does not recurse into {@code @DynamicSensitive} field values.
     */
    public List<SensitiveFieldDescriptor> discoverSensitiveFields(Object root) {
        return SensitiveFieldCollector.collect(root);
    }

    private void validateDescriptors(List<SensitiveFieldDescriptor> descriptors) {
        for (SensitiveFieldDescriptor descriptor : descriptors) {
            resolveHandler(descriptor).validate(descriptor);
        }
    }

    private SensitiveFieldHandler resolveHandler(SensitiveFieldDescriptor descriptor) {
        for (SensitiveFieldHandler handler : sensitiveFieldHandlers) {
            if (handler.supports(descriptor)) {
                return handler;
            }
        }
        throw new IllegalStateException("No handler registered for sensitive descriptor "
                + descriptor.getClass().getName() + " on field " + descriptor.getField());
    }

    private boolean isArtifactoryKeyAvailable() {
        ArtifactoryHome home = getArtifactoryHome();
        return home != null && CryptoHelper.hasArtifactoryKey(home);
    }
}
