package org.dkf.jed2k.protocol.client;

import org.dkf.jed2k.protocol.Hash;

public class StartUpload extends Hash {

    /**
     * Required by the packet combiner - see {@link FileRequest#FileRequest()}. This is
     * the packet a peer sends to join our upload queue, and it arrives moments after
     * the file request does.
     */
    public StartUpload() {
        super();
    }

    public StartUpload(Hash h) {
        super(h);
    }

    @Override
    public String toString() {
        return String.format("StartUpload %s", super.toString());
    }
}
