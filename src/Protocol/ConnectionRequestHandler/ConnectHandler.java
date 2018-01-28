package Protocol.ConnectionRequestHandler;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public interface ConnectHandler {
    /**
     * Handle connection request and return the
     * established socket to next hop, either another
     * socks server or the destination server
     *
     * @param in DataInputStream of client socket
     * @param out  DataOutputStream of client socket
     * @return Socket established to next server
     * @throws IOException
     */
    Socket handle(DataInputStream in, DataOutputStream out) throws IOException;
}
