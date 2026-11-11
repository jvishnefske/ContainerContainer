package org.artifactory.metadata.service.indexer;

import com.jfrog.artifactory.packageindexed.CreatePackageIndexEventOuterClass;
import com.jfrog.artifactory.packageindexed.DeletePackageIndexEventOuterClass;
import com.jfrog.artifactory.packageindexed.FileInfoOuterClass;
import com.jfrog.artifactory.packageindexed.PackageIndexEventOuterClass.PackageIndexEvent;
import com.jfrog.artifactory.packageindexed.QualifierOuterClass;
import com.jfrog.artifactory.packageindexed.UserPropertyOuterClass;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.update.md.MetadataConverterUtils;
import org.jfrog.metadata.client.model.MetadataPackage;
import org.jfrog.metadata.client.model.MetadataVersion;
import org.jfrog.metadata.client.model.MetadataVersionRepo;

import java.util.Collection;
import java.util.Map;

/**
 * Converts MetadataPackage objects to PackageIndexEvent protobuf messages.
 *
 * @author IgorG
 */
@UtilityClass
public class PackageIndexEventConverter {

    public static PackageIndexEvent convert(
            MetadataPackage metadataPackage, Map<String, String> applicationProperties) {
        PackageIndexEvent.Builder envelope = PackageIndexEvent.newBuilder();
        CreatePackageIndexEventOuterClass.CreatePackageIndexEvent.Builder eventBuilder =
                CreatePackageIndexEventOuterClass.CreatePackageIndexEvent.newBuilder();

        setPackageLevelProperties(envelope, eventBuilder, metadataPackage);
        setApplicationProperties(eventBuilder, applicationProperties);
        setVersionData(envelope, eventBuilder, metadataPackage);

        envelope.setCreatePackageIndexEvent(eventBuilder);
        return envelope.build();
    }

    private static void setPackageLevelProperties(
            PackageIndexEvent.Builder envelope,
            CreatePackageIndexEventOuterClass.CreatePackageIndexEvent.Builder eventBuilder,
            MetadataPackage metadataPackage) {
        String storePackageType = MetadataConverterUtils.getPackageUniqueKey(metadataPackage)[0];
        if (storePackageType != null) {
            eventBuilder.setStoredPackageType(storePackageType);
        }

        if (metadataPackage.getName() != null) {
            eventBuilder.setName(metadataPackage.getName());
        }
        if (StringUtils.isNotBlank(metadataPackage.getDescription())) {
            eventBuilder.setDescription(metadataPackage.getDescription());
        }
        if (metadataPackage.getPackageType() != null) {
            envelope.setRepoPackageType(metadataPackage.getPackageType());
        }

        eventBuilder.setRating(metadataPackage.getRating());

        setLicenses(eventBuilder, metadataPackage);

        if (metadataPackage.getIssuesUrl() != null) {
            eventBuilder.setIssuesUrl(metadataPackage.getIssuesUrl());
        }
    }

    private static void setLicenses(
            CreatePackageIndexEventOuterClass.CreatePackageIndexEvent.Builder eventBuilder,
            MetadataPackage metadataPackage) {
        if (metadataPackage.getLicenses() != null) {
            metadataPackage.getLicenses().forEach(lic -> {
                if (lic != null && lic.getName() != null) {
                    eventBuilder.addLicenses(lic.getName());
                }
            });
        }
    }

    private static void setApplicationProperties(
            CreatePackageIndexEventOuterClass.CreatePackageIndexEvent.Builder eventBuilder,
            Map<String, String> applicationProperties) {
        applicationProperties.forEach((k, v) -> {
            if (k != null && v != null) {
                eventBuilder.addInternalPropertiesBuilder().setName(k).addValues(v);
            }
        });
    }

    private static void setVersionData(
            PackageIndexEvent.Builder envelope,
            CreatePackageIndexEventOuterClass.CreatePackageIndexEvent.Builder eventBuilder,
            MetadataPackage metadataPackage) {
        if (metadataPackage.getVersions() == null || metadataPackage.getVersions().isEmpty()) {
            // If no versions, still set package-level modified if present
            if (metadataPackage.getModified() != null) {
                eventBuilder.setModified(metadataPackage.getModified());
            }
            return;
        }

        MetadataVersion version = metadataPackage.getVersions().getFirst();
        if (version == null) {
            return;
        }

        setQualifiers(eventBuilder, version);
        setTags(eventBuilder, version);
        setUserProperties(eventBuilder, version);
        setVersionAndModified(eventBuilder, version, metadataPackage);
        setRepoInfo(envelope, eventBuilder, version);
        setFiles(eventBuilder, version);
    }

    private static void setQualifiers(
            CreatePackageIndexEventOuterClass.CreatePackageIndexEvent.Builder eventBuilder,
            MetadataVersion version) {
        if (version.getQualifiers() == null || version.getQualifiers().isEmpty()) {
            return;
        }

        version.getQualifiers().forEach(q -> {
            if (q != null && q.getName() != null && q.getValue() != null) {
                eventBuilder.addQualifiers(
                        QualifierOuterClass.Qualifier.newBuilder()
                                .setName(q.getName())
                                .setValue(q.getValue())
                                .build());
            }
        });
    }

    private static void setTags(
            CreatePackageIndexEventOuterClass.CreatePackageIndexEvent.Builder eventBuilder,
            MetadataVersion version) {
        if (version.getTags() == null) {
            return;
        }

        version.getTags().forEach(tag -> {
            if (tag != null && tag.getValue() != null) {
                eventBuilder.addTags(tag.getValue());
            }
        });
    }

    private static void setUserProperties(
            @NonNull CreatePackageIndexEventOuterClass.CreatePackageIndexEvent.Builder eventBuilder,
            @NonNull MetadataVersion version) {
        if (version.getUserProperties() == null) {
            return;
        }

        version.getUserProperties().forEach(userProperty -> {
            if (userProperty != null && userProperty.getName() != null && userProperty.getValues() != null) {
                UserPropertyOuterClass.UserProperty.Builder userPropBuilder =
                        UserPropertyOuterClass.UserProperty.newBuilder().setName(userProperty.getName());
                userProperty.getValues().forEach(val -> {
                    if (val != null) {
                        userPropBuilder.addValues(val);
                    }
                });
                // Only add if we actually have values
                if (userPropBuilder.getValuesCount() > 0) {
                    eventBuilder.addUserProperties(userPropBuilder.build());
                }
            }
        });
    }

    private static void setVersionAndModified(
            CreatePackageIndexEventOuterClass.CreatePackageIndexEvent.Builder eventBuilder,
            MetadataVersion version,
            MetadataPackage metadataPackage) {
        if (version.getName() != null) {
            eventBuilder.setVersion(version.getName());
        }
        if (version.getModified() != null) {
            eventBuilder.setModified(version.getModified());
        } else if (metadataPackage.getModified() != null) {
            eventBuilder.setModified(metadataPackage.getModified());
        }
    }

    private static void setRepoInfo(
            PackageIndexEvent.Builder envelope,
            CreatePackageIndexEventOuterClass.CreatePackageIndexEvent.Builder eventBuilder,
            MetadataVersion version) {
        if (version.getRepos() == null || version.getRepos().isEmpty()) {
            return;
        }

        MetadataVersionRepo repo = version.getRepos().getFirst();
        if (repo == null) {
            return;
        }

        if (repo.getName() != null) {
            envelope.setRepoName(repo.getName());
        }

        if (repo.getLeadFilePath() != null) {
            envelope.setLeadFilePath(repo.getLeadFilePath());
        }

        // If repo type is available in the model, set it; otherwise skip (no defaults)
        try {
            String type = repo.getType(); // may not exist in some implementations
            if (type != null) {
                eventBuilder.setRepoType(type);
            }
        } catch (Exception ignore) {
            // skip if no getter in current model
        }
    }

    private static void setFiles(CreatePackageIndexEventOuterClass.CreatePackageIndexEvent.Builder eventBuilder,
            MetadataVersion version) {
        if (version.getFiles() == null) {
            return;
        }

        version.getFiles().forEach(f -> {
            if (f.getName() != null) {
                FileInfoOuterClass.FileInfo.Builder fb = FileInfoOuterClass.FileInfo.newBuilder()
                        .setName(f.getName());
                // Read optional fields once; if an unexpected implementation throws, catch here
                try {
                    long length = f.getLength();
                    fb.setLength(length);
                    fb.setLead(f.isLead());

                    String sha1 = f.getSha1();
                    if (sha1 != null) {
                        fb.setSha1(sha1);
                    }
                    String sha256 = f.getSha256();
                    if (sha256 != null) {
                        fb.setSha256(sha256);
                    }
                    String md5 = f.getMd5();
                    if (md5 != null) {
                        fb.setMd5(md5);
                    }
                } catch (Exception ignore) {
                    // ignore missing fields/getters for some model implementations
                }

                eventBuilder.addFiles(fb.build());
            }
        });
    }

    public static void setUserProperties(
            @NonNull DeletePackageIndexEventOuterClass.DeletePackageIndexEvent.Builder eventBuilder,
            Map<String, Collection<String>> propsMap) {
        if (propsMap == null || propsMap.isEmpty()) {
            return;
        }

        propsMap.forEach((name, values) -> {
            if (name != null && values != null && !values.isEmpty()) {
                UserPropertyOuterClass.UserProperty.Builder userPropBuilder =
                        UserPropertyOuterClass.UserProperty.newBuilder().setName(name);
                values.forEach(val -> {
                    if (val != null) {
                        userPropBuilder.addValues(val);
                    }
                });
                if (userPropBuilder.getValuesCount() > 0) {
                    eventBuilder.addUserProperties(userPropBuilder.build());
                }
            }
        });
    }
}

