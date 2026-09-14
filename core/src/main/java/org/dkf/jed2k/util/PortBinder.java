package org.dkf.jed2k.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.BindException;

/**
 * Binds a socket to a configured port, falling back to a nearby one when it is taken.
 * <p>
 * ed2k needs a listening socket for anything to reach us: sources behind a NAT get a
 * Low ID and can only be downloaded from by asking the server to have them call back,
 * which lands on our listen port. A failed bind therefore does not degrade the client,
 * it stops downloads from ever starting - and the usual cause is mundane, another copy
 * of the app (or the same one, mid-restart) still holding the port.
 * <p>
 * Any port works for that, because the one we end up on is what gets announced to the
 * server at login. A port that was manually forwarded on a router is the exception, so
 * the configured port is always tried first and a move is logged as a warning.
 */
public final class PortBinder {

    private static final Logger log = LoggerFactory.getLogger(PortBinder.class);

    /**
     * Configured port plus nine neighbours. Enough to step over a stale instance or two
     * without wandering far from where the user pointed their router.
     */
    public static final int DEFAULT_ATTEMPTS = 10;

    public static final int MAX_PORT = 65535;

    /**
     * Binds one socket. Implementations are expected to throw {@link BindException} when
     * the port is unavailable, which is what every JDK socket does.
     */
    public interface Bind {
        void bind(int port) throws IOException;
    }

    private PortBinder() {
    }

    /**
     * Tries {@code preferredPort}, then the following {@code attempts - 1} ports, then
     * an ephemeral one.
     * <p>
     * The caller is expected to read the port actually in use from its own socket
     * afterwards - it is the only authoritative answer, and the only one available when
     * the ephemeral fallback is what succeeded.
     *
     * @throws IOException the last bind failure, when nothing could be bound at all
     */
    public static void bind(int preferredPort, int attempts, final Bind target) throws IOException {
        IOException last = null;

        for (int i = 0; i < attempts; i++) {
            int port = preferredPort + i;
            if (port > MAX_PORT) {
                break;
            }

            try {
                target.bind(port);
                if (port != preferredPort) {
                    log.warn("port {} is taken, bound {} instead", preferredPort, port);
                }
                return;
            } catch (BindException e) {
                last = e;
                log.debug("port {} unavailable: {}", port, e.getMessage());
            }
        }

        // Nothing in the range was free. An arbitrary port still gets callbacks, since
        // the port is announced at login rather than assumed by the other side.
        try {
            target.bind(0);
            log.warn("ports {}..{} are all taken, bound an arbitrary one instead"
                    , preferredPort, Math.min(preferredPort + attempts - 1, MAX_PORT));
            return;
        } catch (IOException e) {
            last = e;
        }

        throw (last != null) ? last : new BindException("unable to bind any port");
    }

    public static void bind(int preferredPort, final Bind target) throws IOException {
        bind(preferredPort, DEFAULT_ATTEMPTS, target);
    }
}
