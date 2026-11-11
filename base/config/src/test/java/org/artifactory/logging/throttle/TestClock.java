package org.artifactory.logging.throttle;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.TemporalUnit;

public class TestClock extends Clock {
    private final ZoneId zone;
    private Instant instant;

    public TestClock() {
        this(Instant.now(), ZoneId.systemDefault());
    }

    public TestClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    @Override
    public ZoneId getZone() {
        return this.zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new TestClock(this.instant, zone);
    }

    @Override
    public Instant instant() {
        return this.instant;
    }

    public void plus(long amount, TemporalUnit unit) {
        this.instant = this.instant.plus(amount, unit);
    }
}