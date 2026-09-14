/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.dkf.jmule.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * In-memory ring buffer of the application's own log, so the log screen can show what
 * happened without a cable and adb.
 * <p>
 * Both the core library and the Android code log through slf4j, and the binding in use
 * is slf4j-jdk14 - every call ends up as a {@link LogRecord} on the JUL root logger.
 * Attaching a handler there therefore captures everything, core included, with no
 * changes to any call site. Levels map as slf4j does them: TRACE&rarr;FINEST,
 * DEBUG&rarr;FINE, INFO&rarr;INFO, WARN&rarr;WARNING, ERROR&rarr;SEVERE.
 * <p>
 * Nothing is written to disk: the log can contain search terms and file names, so it
 * lives only as long as the process unless the user explicitly shares it.
 */
public final class LogBuffer {

    /**
     * Roughly the last few minutes of INFO traffic, or well under a minute of a busy
     * download at DEBUG. Old lines are dropped from the front.
     */
    public static final int CAPACITY = 2000;

    private static final ArrayDeque<String> lines = new ArrayDeque<>(CAPACITY);

    private static Sink sink;

    private LogBuffer() {
    }

    /**
     * Attaches the buffer to the JUL root logger. Safe to call more than once; only the
     * first call has an effect.
     */
    public static synchronized void install() {
        if (sink != null) {
            return;
        }

        sink = new Sink();
        Logger.getLogger("").addHandler(sink);
        applyLevel(false);
    }

    /**
     * @param verbose true to capture DEBUG as well as INFO and above
     */
    public static synchronized void setVerbose(boolean verbose) {
        if (sink != null) {
            applyLevel(verbose);
        }
    }

    public static synchronized boolean isVerbose() {
        return sink != null && sink.getLevel().intValue() <= Level.FINE.intValue();
    }

    /**
     * The root logger filters before any handler sees a record, so the level has to be
     * moved in step with the sink's. It is deliberately never lowered past FINE: the
     * core logs per packet at TRACE, and merely enabling that level makes every one of
     * those call sites format its arguments during a download, whether or not anything
     * ends up keeping the result.
     */
    private static void applyLevel(boolean verbose) {
        Level level = verbose ? Level.FINE : Level.INFO;
        sink.setLevel(level);
        Logger.getLogger("").setLevel(level);
    }

    /**
     * @return a copy of the buffered lines, oldest first
     */
    public static List<String> snapshot() {
        synchronized (lines) {
            return new ArrayList<>(lines);
        }
    }

    public static void clear() {
        synchronized (lines) {
            lines.clear();
        }
    }

    private static void append(final String line) {
        synchronized (lines) {
            while (lines.size() >= CAPACITY) {
                lines.removeFirst();
            }
            lines.addLast(line);
        }
    }

    private static final class Sink extends Handler {

        @Override
        public void publish(LogRecord record) {
            if (record == null || !isLoggable(record)) {
                return;
            }

            // java.text.SimpleDateFormat is not thread safe and publish() is called from
            // every thread in the process, so the timestamp is built by hand.
            StringBuilder sb = new StringBuilder(160);
            appendTime(sb, record.getMillis());

            sb.append(' ').append(shortLevel(record.getLevel()));
            sb.append(' ').append(shortName(record.getLoggerName()));
            sb.append(": ").append(message(record));

            Throwable t = record.getThrown();
            if (t != null) {
                sb.append('\n').append(t);
                StackTraceElement[] frames = t.getStackTrace();
                // enough to place the failure, not so much that one exception fills the
                // whole buffer
                for (int i = 0; i < frames.length && i < 8; i++) {
                    sb.append("\n    at ").append(frames[i]);
                }
            }

            append(sb.toString());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        /**
         * slf4j-jdk14 interpolates its {} placeholders before building the record, so
         * for our own logging this is just getMessage(). Libraries that use JUL directly
         * pass their arguments through instead, hence the fallback.
         */
        private static String message(LogRecord record) {
            String msg = record.getMessage();
            Object[] params = record.getParameters();

            if (msg == null || params == null || params.length == 0 || msg.indexOf('{') < 0) {
                return msg;
            }

            try {
                return java.text.MessageFormat.format(msg, params);
            } catch (Exception e) {
                return msg;
            }
        }

        private static void appendTime(StringBuilder sb, long millis) {
            long ofDay = millis % 86400000L;
            two(sb, (int) (ofDay / 3600000L));
            sb.append(':');
            two(sb, (int) (ofDay / 60000L % 60));
            sb.append(':');
            two(sb, (int) (ofDay / 1000L % 60));
            sb.append('.');
            int ms = (int) (ofDay % 1000L);
            if (ms < 100) sb.append('0');
            if (ms < 10) sb.append('0');
            sb.append(ms);
        }

        private static void two(StringBuilder sb, int v) {
            if (v < 10) sb.append('0');
            sb.append(v);
        }

        private static String shortLevel(Level level) {
            if (level == null) return "?";
            int v = level.intValue();
            if (v >= Level.SEVERE.intValue()) return "E";
            if (v >= Level.WARNING.intValue()) return "W";
            if (v >= Level.INFO.intValue()) return "I";
            if (v >= Level.FINE.intValue()) return "D";
            return "T";
        }

        /**
         * org.dkf.jed2k.PeerConnection -&gt; PeerConnection. The package is the same for
         * nearly every line and would just push the message off the screen.
         */
        private static String shortName(String name) {
            if (name == null || name.isEmpty()) return "?";
            int dot = name.lastIndexOf('.');
            return (dot >= 0 && dot < name.length() - 1) ? name.substring(dot + 1) : name;
        }
    }
}
