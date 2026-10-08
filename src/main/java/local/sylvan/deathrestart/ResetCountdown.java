package local.sylvan.deathrestart;

/** A monotonic countdown. Additional deaths cannot extend or duplicate a reset. */
public final class ResetCountdown {
    public static final int DEFAULT_SECONDS = 10;
    private static final long SECOND = 1_000_000_000L;
    private final int seconds;
    private long startedAt;
    private boolean started;
    private boolean fired;

    public ResetCountdown() {
        this(DEFAULT_SECONDS);
    }

    public ResetCountdown(int seconds) {
        if (seconds <= 0) throw new IllegalArgumentException("Countdown duration must be positive");
        this.seconds = seconds;
    }

    public boolean start(long now) {
        if (started) return false;
        started = true;
        startedAt = now;
        return true;
    }

    public int secondsRemaining(long now) {
        long remaining = seconds * SECOND - (now - startedAt);
        return (int) Math.max(0, (remaining + SECOND - 1) / SECOND);
    }

    public boolean fireIfReady(long now) {
        if (!started || fired || secondsRemaining(now) > 0) return false;
        fired = true;
        return true;
    }

    public boolean cancel() {
        if (!started || fired) return false;
        started = false;
        startedAt = 0;
        return true;
    }

    public boolean isStarted() {
        return started;
    }

    public boolean isFired() {
        return fired;
    }

    public int durationSeconds() {
        return seconds;
    }
}
