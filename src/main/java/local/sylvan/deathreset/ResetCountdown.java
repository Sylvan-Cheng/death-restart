package local.sylvan.deathreset;

/** A monotonic countdown. Additional deaths cannot extend or duplicate a reset. */
public final class ResetCountdown {
    public static final int SECONDS = 10;
    private static final long SECOND = 1_000_000_000L;
    private long startedAt;
    private boolean started;
    private boolean fired;

    public boolean start(long now) {
        if (started) return false;
        started = true;
        startedAt = now;
        return true;
    }

    public int secondsRemaining(long now) {
        long remaining = SECONDS * SECOND - (now - startedAt);
        return (int) Math.max(0, (remaining + SECOND - 1) / SECOND);
    }

    public boolean fireIfReady(long now) {
        if (!started || fired || secondsRemaining(now) > 0) return false;
        fired = true;
        return true;
    }

    public boolean isStarted() {
        return started;
    }
}
