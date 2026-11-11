package org.artifactory.traffic;

import lombok.AllArgsConstructor;
import lombok.Getter;

import static org.artifactory.traffic.TrafficEntry.TrafficActor.XRAY_CREDIT;
import static org.artifactory.traffic.TrafficEntry.TrafficActor.XRAY_REPETITIVE;

@AllArgsConstructor
public enum XraySpecialSubjects {
    JFXR_CVE_APPLICABILITY_PREFIX("jfxr@cve_applicability_", XRAY_REPETITIVE.name()),
    JFXR_EXTRA_ANALYSIS("jfxr@extra_analysis_", XRAY_REPETITIVE.name()),
    JFXR_ACTIONABLE_ANALYSIS("jfxr@actionable_analysis_", XRAY_CREDIT.name());

    @Getter
    private final String subject;
    @Getter
    private final String actor;
}
