package org.dkf.jed2k.protocol.client;

import org.dkf.jed2k.exception.JED2KException;
import org.dkf.jed2k.protocol.Dispatchable;
import org.dkf.jed2k.protocol.Dispatcher;
import org.dkf.jed2k.protocol.Hash;

public class FileRequest extends Hash implements Dispatchable {

    /**
     * Required by the packet combiner, which builds every incoming packet reflectively
     * with newInstance() before calling get() on it. Without this the very first thing
     * a peer sends after the handshake when it wants to download from us - a request
     * for the file name - threw InstantiationException and took the connection down
     * with it, so nobody could ever download from this client.
     */
    public FileRequest() {
        super();
    }

    public FileRequest(Hash h) {
        super(h);
    }

    @Override
    public void dispatch(Dispatcher dispatcher) throws JED2KException {
        dispatcher.onClientFileRequest(this);
    }

    @Override
    public String toString() {
        return String.format("FileRequest %s", super.toString());
    }
}
