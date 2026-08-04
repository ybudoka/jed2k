package org.dkf.jed2k.test;

import org.dkf.jed2k.util.PortBinder;
import org.junit.Test;

import java.io.IOException;
import java.net.BindException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Exercised against real sockets: the whole point of the helper is what the OS does
 * with an occupied port, and a mock of that proves nothing.
 */
public class PortBinderTest {

    /**
     * @return a port that was free a moment ago
     */
    private static int freePort() throws IOException {
        ServerSocketChannel probe = ServerSocketChannel.open();
        try {
            probe.socket().bind(new InetSocketAddress(0));
            return probe.socket().getLocalPort();
        } finally {
            probe.close();
        }
    }

    @Test
    public void testBindsPreferredPortWhenFree() throws IOException {
        final int preferred = freePort();
        ServerSocketChannel ssc = ServerSocketChannel.open();

        try {
            PortBinder.bind(preferred, new PortBinder.Bind() {
                @Override
                public void bind(int port) throws IOException {
                    ssc.socket().bind(new InetSocketAddress(port));
                }
            });

            assertEquals(preferred, ssc.socket().getLocalPort());
        } finally {
            ssc.close();
        }
    }

    @Test
    public void testStepsOverAnOccupiedPort() throws IOException {
        final int preferred = freePort();

        ServerSocketChannel squatter = ServerSocketChannel.open();
        ServerSocketChannel ssc = ServerSocketChannel.open();

        try {
            squatter.socket().bind(new InetSocketAddress(preferred));

            PortBinder.bind(preferred, new PortBinder.Bind() {
                @Override
                public void bind(int port) throws IOException {
                    ssc.socket().bind(new InetSocketAddress(port));
                }
            });

            int bound = ssc.socket().getLocalPort();
            assertTrue("expected a port near " + preferred + ", got " + bound,
                    bound > preferred && bound < preferred + PortBinder.DEFAULT_ATTEMPTS);
        } finally {
            squatter.close();
            ssc.close();
        }
    }

    @Test
    public void testDatagramSocketToo() throws IOException {
        final int preferred = freePort();

        DatagramChannel squatter = DatagramChannel.open();
        DatagramChannel dc = DatagramChannel.open();

        try {
            squatter.socket().bind(new InetSocketAddress(preferred));

            PortBinder.bind(preferred, new PortBinder.Bind() {
                @Override
                public void bind(int port) throws IOException {
                    dc.socket().bind(new InetSocketAddress(port));
                }
            });

            DatagramSocket socket = dc.socket();
            assertTrue(socket.getLocalPort() > 0);
            assertTrue(socket.getLocalPort() != preferred);
        } finally {
            squatter.close();
            dc.close();
        }
    }

    /**
     * When the whole range is occupied the helper must still produce a usable socket -
     * an arbitrary port is fine, because the port we end up on is the one announced to
     * the server at login.
     */
    @Test
    public void testFallsBackToAnEphemeralPort() throws IOException {
        final int preferred = freePort();
        final int attempts = 3;

        List<ServerSocketChannel> squatters = new ArrayList<>();
        ServerSocketChannel ssc = ServerSocketChannel.open();

        try {
            for (int i = 0; i < attempts; i++) {
                ServerSocketChannel s = ServerSocketChannel.open();
                squatters.add(s);
                s.socket().bind(new InetSocketAddress(preferred + i));
            }

            PortBinder.bind(preferred, attempts, new PortBinder.Bind() {
                @Override
                public void bind(int port) throws IOException {
                    ssc.socket().bind(new InetSocketAddress(port));
                }
            });

            int bound = ssc.socket().getLocalPort();
            assertTrue("expected a bound socket, got port " + bound, bound > 0);
            assertTrue(bound < preferred || bound >= preferred + attempts);
        } finally {
            for (ServerSocketChannel s : squatters) {
                s.close();
            }
            ssc.close();
        }
    }

    /**
     * Only the ports actually attempted, in order, and no more than asked for.
     */
    @Test
    public void testAttemptOrderAndCount() throws IOException {
        final List<Integer> attempted = new ArrayList<>();

        try {
            PortBinder.bind(4000, 4, new PortBinder.Bind() {
                @Override
                public void bind(int port) throws IOException {
                    attempted.add(port);
                    throw new BindException("occupied");
                }
            });
            fail("expected the failure to propagate once nothing could be bound");
        } catch (IOException expected) {
            // the four candidates, then 0 for the ephemeral fallback
            assertEquals(java.util.Arrays.asList(4000, 4001, 4002, 4003, 0), attempted);
        }
    }

    /**
     * A candidate past the top of the range is not attempted.
     */
    @Test
    public void testDoesNotWalkPastTheLastPort() throws IOException {
        final List<Integer> attempted = new ArrayList<>();

        try {
            PortBinder.bind(PortBinder.MAX_PORT - 1, 5, new PortBinder.Bind() {
                @Override
                public void bind(int port) throws IOException {
                    attempted.add(port);
                    throw new BindException("occupied");
                }
            });
            fail("expected the failure to propagate once nothing could be bound");
        } catch (IOException expected) {
            assertEquals(java.util.Arrays.asList(PortBinder.MAX_PORT - 1, PortBinder.MAX_PORT, 0), attempted);
        }
    }
}
