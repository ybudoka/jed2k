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

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds a Wi-Fi multicast lock for as long as UPnP discovery needs one.
 * <p>
 * Android's Wi-Fi driver drops multicast and broadcast packets that are not addressed
 * to the device unless something holds a {@link WifiManager.MulticastLock}. UPnP gateway
 * discovery is SSDP: an M-SEARCH sent to 239.255.255.250:1900, with the router's reply
 * arriving the same way. Without the lock the search goes out and the answer is filtered
 * before the app ever sees it, which surfaces as "No gateway device found" on a network
 * that has a perfectly good UPnP gateway - and that is the difference between a High ID
 * and a Low ID, so it is also the difference between being able to download from
 * firewalled sources and not.
 * <p>
 * The lock is genuinely expensive - it stops the Wi-Fi chip filtering in hardware and
 * wakes the CPU for traffic meant for other devices - so it is taken for the length of a
 * discovery attempt and dropped again, never held for the session.
 */
public final class MulticastLease {

    private static final Logger log = LoggerFactory.getLogger(MulticastLease.class);

    /**
     * Generous next to the SSDP MX window (a few seconds) and to weupnp's own socket
     * timeouts, without being long enough to matter for battery.
     */
    private static final long LEASE_MS = 15000;

    private static final Object GUARD = new Object();

    private static WifiManager.MulticastLock lock;

    private MulticastLease() {
    }

    /**
     * Takes the lock if it is not already held and schedules its release. Calling this
     * again while a lease is running extends it rather than nesting.
     */
    public static void acquire(Context context) {
        if (context == null) {
            return;
        }

        synchronized (GUARD) {
            try {
                if (lock == null) {
                    WifiManager wifi = (WifiManager) context.getApplicationContext()
                            .getSystemService(Context.WIFI_SERVICE);

                    if (wifi == null) {
                        log.warn("no wifi service, UPnP discovery may not see the gateway");
                        return;
                    }

                    lock = wifi.createMulticastLock("jmule-ssdp");
                    lock.setReferenceCounted(false);
                }

                if (!lock.isHeld()) {
                    lock.acquire();
                    log.info("multicast lock acquired for UPnP discovery");
                }
            } catch (Throwable t) {
                // A missing CHANGE_WIFI_MULTICAST_STATE, or a device without Wi-Fi, must
                // not take the session down - discovery simply carries on without it,
                // exactly as it did before.
                log.warn("unable to acquire multicast lock {}", t.toString());
                return;
            }
        }

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                release();
            }
        }, LEASE_MS);
    }

    public static void release() {
        synchronized (GUARD) {
            try {
                if (lock != null && lock.isHeld()) {
                    lock.release();
                    log.info("multicast lock released");
                }
            } catch (Throwable t) {
                log.warn("unable to release multicast lock {}", t.toString());
            }
        }
    }
}
