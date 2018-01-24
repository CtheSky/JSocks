package Protocol.AuthMethod;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class NoAuth implements AuthMethod {
    @Override
    public byte bytecode() {
        return 0;
    }

    @Override
    public void sendRequest(DataInputStream in, DataOutputStream out) {

    }

    @Override
    public void handleRequest(DataInputStream in, DataOutputStream out) throws IOException {

    }
}
