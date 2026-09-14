package org.dkf.jed2k.test;

import org.dkf.jed2k.protocol.Serializable;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertTrue;

/**
 * Every packet registered with a combiner has to be constructible with no arguments.
 * <p>
 * {@link org.dkf.jed2k.protocol.PacketCombiner#unpack} builds incoming packets
 * reflectively - {@code clazz.newInstance()}, then {@code get(buffer)} - so a
 * registered class without a zero-argument constructor does not fail at build time or
 * at startup. It fails the first time a peer or server actually sends that packet, as
 * an InstantiationException wrapped in a JED2KException, which closes the connection.
 * <p>
 * That is how OP_REQUESTFILENAME went unnoticed: it is the first thing a peer sends
 * after the handshake when it wants to download from us, so it only ever fired on
 * incoming connections - which needed a working listen socket to arrive at all.
 * <p>
 * The registries are read reflectively rather than through an accessor added for the
 * test, so a packet registered tomorrow is covered without anyone remembering to
 * update a list here.
 */
public class PacketInstantiationTest {

    private static final String[][] REGISTRIES = {
            {"org.dkf.jed2k.protocol.client.PacketCombiner", "supportedPacketsClient"},
            {"org.dkf.jed2k.protocol.server.PacketCombiner", "supportedPacketsServer"},
            {"org.dkf.jed2k.protocol.kad.PacketCombiner", "supportedPacketsKad"},
    };

    @SuppressWarnings("unchecked")
    private static Map<?, Class<? extends Serializable>> registry(String owner, String field)
            throws ReflectiveOperationException {

        Class<?> combiner = Class.forName(owner);
        Field f = combiner.getDeclaredField(field);
        f.setAccessible(true);
        return (Map<?, Class<? extends Serializable>>) f.get(null);
    }

    @Test
    public void testEveryRegisteredPacketCanBeInstantiated() throws ReflectiveOperationException {
        List<String> broken = new ArrayList<>();
        int checked = 0;

        for (String[] r : REGISTRIES) {
            Map<?, Class<? extends Serializable>> packets = registry(r[0], r[1]);
            assertTrue(r[1] + " is empty, the registry was probably renamed", !packets.isEmpty());

            for (Class<? extends Serializable> packet : packets.values()) {
                checked++;
                try {
                    packet.newInstance();
                } catch (Throwable t) {
                    broken.add(packet.getName() + " -> " + t);
                }
            }
        }

        assertTrue("checked suspiciously few packets: " + checked, checked > 40);
        assertTrue("registered packets that unpack() cannot build:\n  "
                + String.join("\n  ", broken), broken.isEmpty());
    }

    /**
     * The reverse direction. A packet the app can build but never registered cannot be
     * serialised either - {@code pack()} looks the class up in the same registry - so
     * both maps have to agree.
     */
    @Test
    public void testRegistriesAgree() throws ReflectiveOperationException {
        String[][] pairs = {
                {"org.dkf.jed2k.protocol.client.PacketCombiner", "supportedPacketsClient", "struct2KeyClient"},
                {"org.dkf.jed2k.protocol.server.PacketCombiner", "supportedPacketsServer", "struct2KeyServer"},
                {"org.dkf.jed2k.protocol.kad.PacketCombiner", "supportedPacketsKad", "struct2KeyKad"},
        };

        for (String[] p : pairs) {
            Map<?, Class<? extends Serializable>> byKey = registry(p[0], p[1]);
            Map<?, ?> byClass = registry(p[0], p[2]);

            for (Class<? extends Serializable> packet : byKey.values()) {
                assertTrue(packet.getName() + " can be read but not written back", byClass.containsKey(packet));
            }
        }
    }
}
