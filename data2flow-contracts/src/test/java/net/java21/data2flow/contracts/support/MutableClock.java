package net.java21.data2flow.contracts.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** 테스트 시계(design/testing: Thread.sleep 대신 시간을 앞으로 돌린다) */
public final class MutableClock extends Clock {

    private Instant now;

    private MutableClock(Instant now) {
        this.now = now;
    }

    public static MutableClock atUtc(String isoInstant) {
        return new MutableClock(Instant.parse(isoInstant));
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
