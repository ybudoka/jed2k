package org.dkf.jed2k;

import org.dkf.jed2k.protocol.Hash;

public class Settings {
    public Hash userAgent = new Hash(Hash.EMULE);
    public String modName = "jed2k";
    public String clientName = "jed2k";
    public int listenPort = 4661;
    public int udpPort = 4662;
    public int version = 0x3c;
    public int modMajor = 0;
    public int modMinor = 0;
    public int modBuild = 0;
    public int maxFailCount = 20;
    public int maxPeerListSize = 100;
    public int minPeerReconnectTime = 10;
    public int peerConnectionTimeout = 5;

    /**
     * Maximum number of simultaneous peer connections for the whole session.
     * <p>
     * This was 20, which is very low for an ed2k client - eMule ships with a limit in the
     * hundreds - and it is the ceiling on how much of a file can be pulled in parallel.
     * Ramp-up is still paced by maxConnectionsPerSecond, so raising it does not produce a
     * burst of connections.
     */
    public int sessionConnectionsLimit = 100;
    public int bufferPoolSize = 250;            // dataSize of buffer pool in blocks of BLOCK_SIZE (190K)
    public int maxConnectionsPerSecond = 10;    // for testing purposes
    public int compressionVersion = 0;          // use 1 for activate compression
    public int serverSearchTimeout = 15;        // seconds
    public boolean reconnectoToServer = false;  // reconnect to server if connection was closed due to error

    /**
     * send ping message to server every serverPingTimeout seconds
     */
    public long serverPingTimeout = 0;

    @Override
    public String toString() {
        return "Settings{" +
                "userAgent=" + userAgent +
                ", modName='" + modName + '\'' +
                ", clientName='" + clientName + '\'' +
                ", listenPort=" + listenPort +
                ", udpPort=" + udpPort +
                ", version=" + version +
                ", modMajor=" + modMajor +
                ", modMinor=" + modMinor +
                ", modBuild=" + modBuild +
                ", maxFailCount=" + maxFailCount +
                ", maxPeerListSize=" + maxPeerListSize +
                ", minPeerReconnectTime=" + minPeerReconnectTime +
                ", peerConnectionTimeout=" + peerConnectionTimeout +
                ", sessionConnectionsLimit=" + sessionConnectionsLimit +
                ", bufferPoolSize=" + bufferPoolSize +
                ", maxConnectionsPerSecond=" + maxConnectionsPerSecond +
                ", compressionVersion=" + compressionVersion +
                ", serverSearchTimeout=" + serverSearchTimeout +
                ", serverPingTimeout=" + serverPingTimeout +
                ", reconnectToServer=" + (reconnectoToServer?"yes":"no") +
                '}';
    }
}
