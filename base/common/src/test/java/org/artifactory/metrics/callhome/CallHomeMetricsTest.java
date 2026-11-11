package org.artifactory.metrics.callhome;
import com.jfrog.sysconf.SysConfig;
import com.jfrog.sysconf.SysConfigConstants;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.test.TestUtils;
import org.testng.annotations.Test;
import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
public class CallHomeMetricsTest extends ArtifactoryHomeBoundTest {
    CallHomeMetrics callHomeMetrics;
    @Test
    public void testDefaultConstruction() {
        callHomeMetrics = new CallHomeMetrics(10);
        assertThat(callHomeMetrics.getTraceStartTime()).isPositive();
    }
    @Test
    public void maxElementsEnforcedAndAlsoRemovedFromRegistry() {
        callHomeMetrics = new CallHomeMetrics(0);
        callHomeMetrics.send(
                CallHomeDataModel.builder().action("upload").service("RT").packageType("maven")
                        .requestContentLength(1000L)
                        .responseContentLength(299L).statusCode(200).tool("CLI").build());
        assertThat(callHomeMetrics.getMetricsRegistry().getHistograms()).isEmpty();
    }
    @Test
    public void createMetric() {
        callHomeMetrics = new CallHomeMetrics(10);
        callHomeMetrics.send(
                CallHomeDataModel.builder().action("download").service("RT").packageType("maven")
                        .requestContentLength(1000L).repoType("local")
                        .responseContentLength(299L).statusCode(200).statusFamily("2xx").tool("CLI").build());
        assertThat(callHomeMetrics.getCallHomeData().containsKey("RT_maven_local_CLI_download_2xx")).isTrue();
        assertThat(callHomeMetrics.getMetricsRegistry().getHistograms()).isNotEmpty();
    }
    @Test
    public void createMetricNoRepoType() {
        callHomeMetrics = new CallHomeMetrics(10);
        callHomeMetrics.send(
                CallHomeDataModel.builder().action("download").service("RT").packageType("maven")
                        .requestContentLength(1000L)
                        .responseContentLength(299L).statusCode(200).statusFamily("2xx").tool("CLI").build());
        assertThat(callHomeMetrics.getCallHomeData().containsKey("RT_maven_NA_CLI_download_2xx")).isTrue();
        assertThat(callHomeMetrics.getMetricsRegistry().getHistograms()).isNotEmpty();
    }
    @Test
    public void testMetricsWithSysYaml() {
        SysConfig sysConfig = mock(SysConfig.class);
        SysConfig sysConfigBackUp = TestUtils.getField(ArtifactoryHome.get(), "sysConfig", SysConfig.class);
        TestUtils.setField(ArtifactoryHome.get(), "sysConfig", sysConfig);
        when(sysConfig.getInt(SysConfigConstants.KEY_CALLHOME_MAX_CALLS_TRACK, 100)).thenReturn(1);
        callHomeMetrics = new CallHomeMetrics();
        callHomeMetrics.send(
                CallHomeDataModel.builder().action("download").service("RT").packageType("maven").repoType("remote")
                        .requestContentLength(1000L)
                        .responseContentLength(299L).statusCode(200).statusFamily("2xx").tool("CLI").build());
        assertThat(callHomeMetrics.getCallHomeData().containsKey("RT_maven_remote_CLI_download_2xx")).isTrue();
        callHomeMetrics.send(
                CallHomeDataModel.builder().action("upload").service("RT").packageType("generic")
                        .requestContentLength(1000L).repoType("virtual")
                        .responseContentLength(299L).statusCode(200).statusFamily("2xx").tool("CLI").build());
        assertThat(callHomeMetrics.getCallHomeData().size()).isEqualTo(1);
        assertThat(callHomeMetrics.getCallHomeData().containsKey("RT_generic_virtual_CLI_upload_2xx")).isTrue();
        assertThat(callHomeMetrics.getCallHomeData().containsKey("RT_maven_remote_CLI_download_200")).isFalse();
    }
}