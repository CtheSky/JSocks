package Protocol.AuthMethod;

import Protocol.SocksProtocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public interface AuthMethod {
    byte bytecode();
    void sendRequest(DataInputStream in, DataOutputStream out) throws IOException;
    void handleRequest(DataInputStream in, DataOutputStream out) throws IOException;

    static void sendRequest(DataInputStream in, DataOutputStream out, AuthMethod[] supported) throws IOException {
        out.writeByte(5);
        out.writeByte(supported.length);
        for (AuthMethod method : supported)
            out.writeByte(method.bytecode());
        out.flush();

        byte version = SocksProtocol.checkVersion(in);
        byte methodByte = SocksProtocol.checkMethod(in);

        for (AuthMethod method : supported) {
            if (method.bytecode() == methodByte) {
                method.sendRequest(in, out);
                return;
            }
        }
        throw new IllegalStateException("Server authentication methods are not supported");
    }
}
