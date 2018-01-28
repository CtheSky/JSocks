package Protocol.ConnectionRequestHandler;

import Protocol.SocksProtocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class ConnectDirectly implements ConnectHandler {

    /**
     * Establish socket to destination ip and port
     *
     * @param in DataInputStream of client socket
     * @param out  DataOutputStream of client socket
     * @return Socket established to destination server
     * @throws IOException
     */
    @Override
    public Socket handle(DataInputStream in, DataOutputStream out) throws IOException{
        byte reserved = SocksProtocol.checkReserved(in);
        byte addressType = SocksProtocol.checkAddressType(in);
        String host = SocksProtocol.getHostAddressByString(in, addressType);
        int port = SocksProtocol.getPort(in);

        Socket proxySocket = establishSocket(host, port);
        out.writeByte(5);
        out.writeByte(proxySocket != null ? 0 : 1);
        out.writeByte(0);
        out.writeByte(1); // dummy bind address port
        for (int i = 0; i < 6; ++i)
            out.writeByte(0);
        out.flush();

        if (proxySocket == null)
            throw new IllegalStateException("Unable to establish socket to [" + host + ":" + port + "]");

        return proxySocket;
    }

    private Socket establishSocket(String host, int port) {
        Socket socket;
        try {
            socket = new Socket(host, port);
            socket.setSoTimeout(100000);
            return socket;
        } catch (IOException ex) {
            return null;
        }
    }
}
