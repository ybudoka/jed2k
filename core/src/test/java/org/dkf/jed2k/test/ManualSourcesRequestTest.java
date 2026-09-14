package org.dkf.jed2k.test;

import org.dkf.jed2k.Time;
import org.dkf.jed2k.Transfer;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Rules behind the "find more sources" action.
 *
 * @see Transfer#requestMoreSources()
 */
public class ManualSourcesRequestTest {

    private static final long NOW = 1000000;
    private static final int LIMIT = 100;

    @Test
    public void testFirstRequestIsAllowed() {
        assertTrue(Transfer.allowManualSourcesRequest(true, 0, LIMIT, NOW, 0));
    }

    @Test
    public void testAllowedWhileSomePeersAreAttached() {
        // the whole point: the automatic schedule backs off to twenty minutes as soon as
        // a single peer attaches, and that is exactly when a user reaches for this
        assertTrue(Transfer.allowManualSourcesRequest(true, 3, LIMIT, NOW, 0));
    }

    @Test
    public void testRefusedWhenNotRunning() {
        assertFalse(Transfer.allowManualSourcesRequest(false, 0, LIMIT, NOW, 0));
    }

    @Test
    public void testRefusedAtTheConnectionLimit() {
        assertFalse(Transfer.allowManualSourcesRequest(true, LIMIT, LIMIT, NOW, 0));
        assertFalse(Transfer.allowManualSourcesRequest(true, LIMIT + 1, LIMIT, NOW, 0));
        assertTrue(Transfer.allowManualSourcesRequest(true, LIMIT - 1, LIMIT, NOW, 0));
    }

    @Test
    public void testRefusedWhenAskedTooRecently() {
        long lastRequest = NOW - Time.seconds(59);
        assertFalse(Transfer.allowManualSourcesRequest(true, 1, LIMIT, NOW, lastRequest));
    }

    @Test
    public void testAllowedAgainAfterTheInterval() {
        long lastRequest = NOW - Time.minutes(1);
        assertTrue(Transfer.allowManualSourcesRequest(true, 1, LIMIT, NOW, lastRequest));

        lastRequest = NOW - Time.minutes(5);
        assertTrue(Transfer.allowManualSourcesRequest(true, 1, LIMIT, NOW, lastRequest));
    }

    /**
     * The rate limit must not be defeated by asking while paused and then resuming: the
     * clock is what matters, not the outcome of the previous call.
     */
    @Test
    public void testNotRunningStillLosesToTheRateLimit() {
        long lastRequest = NOW - Time.seconds(1);
        assertFalse(Transfer.allowManualSourcesRequest(false, 1, LIMIT, NOW, lastRequest));
        assertFalse(Transfer.allowManualSourcesRequest(true, 1, LIMIT, NOW, lastRequest));
    }
}
