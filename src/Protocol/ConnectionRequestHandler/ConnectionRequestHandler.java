package Protocol.ConnectionRequestHandler;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public interface ConnectionRequestHandler {
    Socket handleConnectionRequest(DataInputStream in, DataOutputStream out) throws IOException;
}
