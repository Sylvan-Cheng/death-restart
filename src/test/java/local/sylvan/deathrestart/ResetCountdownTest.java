package local.sylvan.deathrestart;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResetCountdownTest {
    @Test
    void waitsTenSecondsAndFiresExactlyOnce() {
        var timer = new ResetCountdown();
        long start = 123_000_000_000L;
        assertTrue(timer.start(start));
        assertEquals(10, timer.secondsRemaining(start));
        assertEquals(10, timer.secondsRemaining(start + 1));
        assertEquals(9, timer.secondsRemaining(start + 1_000_000_000L));
        assertEquals(1, timer.secondsRemaining(start + 9_999_999_999L));
        assertFalse(timer.fireIfReady(start + 9_999_999_999L));
        assertTrue(timer.fireIfReady(start + 10_000_000_000L));
        assertFalse(timer.fireIfReady(start + 20_000_000_000L));
    }

    @Test
    void secondDeathDoesNotRestartTheCountdown() {
        var timer = new ResetCountdown();
        assertTrue(timer.start(0));
        assertFalse(timer.start(9_000_000_000L));
        assertTrue(timer.fireIfReady(10_000_000_000L));
        assertFalse(timer.start(11_000_000_000L));
    }

    @Test
    void catchesUpAfterLagAndHandlesNegativeNanoTime() {
        var timer = new ResetCountdown();
        timer.start(-100_000_000_000L);
        assertEquals(7, timer.secondsRemaining(-96_500_000_000L));
        assertEquals(0, timer.secondsRemaining(-80_000_000_000L));
        assertTrue(timer.fireIfReady(-80_000_000_000L));
    }

    @Test
    void anUnstartedCountdownCannotFire() {
        assertFalse(new ResetCountdown().fireIfReady(Long.MAX_VALUE));
    }

    @Test
    void configuredDurationControlsWhenResetFires() {
        for (int seconds : new int[] {5, 45, 60}) {
            var timer = new ResetCountdown(seconds);
            timer.start(0);
            assertEquals(seconds, timer.durationSeconds());
            assertEquals(1, timer.secondsRemaining(seconds * 1_000_000_000L - 1));
            assertFalse(timer.fireIfReady(seconds * 1_000_000_000L - 1));
            assertTrue(timer.fireIfReady(seconds * 1_000_000_000L));
            assertFalse(timer.fireIfReady(seconds * 1_000_000_000L));
        }
    }

    @Test
    void rejectsNonPositiveDuration() {
        assertThrows(IllegalArgumentException.class, () -> new ResetCountdown(0));
        assertThrows(IllegalArgumentException.class, () -> new ResetCountdown(-10));
    }
}
