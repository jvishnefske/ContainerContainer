package org.artifactory.util.evidence;

import lombok.experimental.UtilityClass;

@UtilityClass
public class EvidenceSystemYamlConstants {

    public static final String EVIDENCE_ENABLED = "artifactory.evidence.enabled";
    public static final String EVIDENCE_OCI_ENABLED = "artifactory.evidence.oci.enabled";
    public static final String EVIDENCE_AQL_FILTER_IN_DATABASE = "artifactory.evidence.aql.filterInDataBase";
    public static final String EVIDENCE_NAVIGATION_ENABLED = "artifactory.evidence.navigation.enabled";
    public static final String EVIDENCE_EVENTS_BACKOFF_FACTOR = "artifactory.evidence.events.backoff.factor";
    public static final String EVIDENCE_EVENTS_BACKOFF_RETRIES = "artifactory.evidence.events.backoff.retries";

    public static final String COLD_INSTANCE_ENABLED = "shared.jfrogColdStorage.coldInstanceEnabled";

}