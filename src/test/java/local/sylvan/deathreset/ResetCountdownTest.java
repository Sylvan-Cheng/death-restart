package local.sylvan.deathreset;

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
}
