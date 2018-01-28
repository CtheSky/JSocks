package Protocol.BindHandler;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public interface BindHandler {
    /**
     * Handle bind request and return the
     * established socket to next hop, either another
     * socks server or accepted socket from destination server
     *
     * @param in DataInputStream of client socket
     * @param out  DataOutputStream of client socket
     * @return Socket established to next server
     * @throws IOException
     */
    Socket handle(DataInputStream in, DataOutputStream out) throws IOException;
}
