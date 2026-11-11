package org.artifactory.addon.onboarding.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OnBoardingRecord {

    private OnBoardingCacheKey key;
    private String repoKey;
    private String downloadPackageName;
    private String downloadVersion;
    private String uploadPackageName;
    private String uploadVersion;
    private long startTime;
    private boolean isAuthenticated;
    private boolean isDownloaded;
    private boolean isUploaded;

    public OnBoardingRecord(OnBoardingCacheKey key, String repoKey, String downloadPackageName, String downloadVersion,
            String uploadPackageName, String uploadVersion) {
        this.key = key;
        this.repoKey = repoKey;
        this.downloadPackageName = downloadPackageName;
        this.downloadVersion = downloadVersion;
        this.uploadPackageName = uploadPackageName;
        this.uploadVersion = uploadVersion;
        this.startTime = System.currentTimeMillis();
    }
}
