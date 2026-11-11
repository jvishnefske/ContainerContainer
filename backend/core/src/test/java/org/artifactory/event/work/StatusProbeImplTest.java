package org.artifactory.event.work;

import com.google.common.util.concurrent.MoreExecutors;
import org.apache.commons.lang3.mutable.MutableInt;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

/**
 * @author Uriah Levy
 */
@Test
public class StatusProbeImplTest {

    public void testProbeUntilSuccess() {
        StatusProbeImpl externalServiceStatusProbe = new StatusProbeImpl(100, 11_000, "aService");
        Boolean[] results = {false, false, false, true};
        MutableInt mutableInt = new MutableInt();
        doProbe(externalServiceStatusProbe, results, mutableInt);
        assertEquals(mutableInt.intValue(), 4);
    }

    public void testProbeAttemptsExhausted() {
        StatusProbeImpl externalServiceStatusProbe = new StatusProbeImpl(100, 2, "aService");
        Boolean[] results = {false, false};
        MutableInt mutableInt = new MutableInt();
        doProbe(externalServiceStatusProbe, results, mutableInt);
        assertEquals(mutableInt.intValue(), 2);
    }

    public void testIsNotProbingDuringOnSuccessCallback() {
        StatusProbeImpl probe = new StatusProbeImpl(100, 5, "aService");
        probe.startProbing(MoreExecutors.newDirectExecutorService(), () -> true, () -> {
            assertFalse(probe.isProbing(),
                    "isProbing() must be false during onSuccess callback so that " +
                            "re-init failures can trigger a new probe");
        });
    }

    private void doProbe(StatusProbeImpl externalServiceStatusProbe, Boolean[] results, MutableInt mutableInt) {
        externalServiceStatusProbe.startProbing(MoreExecutors.newDirectExecutorService(), () -> {
            int i = mutableInt.getValue();
            mutableInt.increment();
            return results[i];
        }, () -> {});
    }
}