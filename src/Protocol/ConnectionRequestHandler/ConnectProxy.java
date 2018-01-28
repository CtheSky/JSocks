package Protocol.ConnectionRequestHandler;

import Protocol.AuthMethod.AuthMethod;
import Protocol.SocksProtocol;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ConnectProxy implements ConnectHandler {
    private final SSLSocketFactory factory;
    private final String remoteHost;
    private final short port;
    private final AuthMethod[] supportedAuth;

    public ConnectProxy(String remoteHost, short port, AuthMethod[] supported, SSLSocketFactory factory) {
        this.remoteHost = remoteHost;
        this.port = port;
        this.supportedAuth = supported;
        this.factory = factory;
    }

    /**
     * Establish socket to next server, do authentication and
     * relay the connect request
     *
     * @param in DataInputStream of client socket
     * @param out  DataOutputStream of client socket
     * @return Socket established to next server
     * @throws IOException
     */
    @Override
    public Socket handle(DataInputStream in, DataOutputStream out) throws IOException {
        byte reserved = SocksProtocol.checkReserved(in);
        byte addressType = SocksProtocol.checkAddressType(in);
        List<Byte> addressBytes = SocksProtocol.getHostAddressBytes(in, addressType);
        List<Byte> portBytes = SocksProtocol.getPortBytes(in);

        List<Byte> connectionRequest = new ArrayList<>();
        connectionRequest.add((byte) 5);
        connectionRequest.add((byte) 1);
        connectionRequest.add(reserved);
        connectionRequest.add(addressType);
        connectionRequest.addAll(addressBytes);
        connectionRequest.addAll(portBytes);

        Socket proxySocket = startProxySocket(connectionRequest);
        out.writeByte(5);
        out.writeByte(proxySocket != null ? 0 : 1);
        out.writeByte(0);
        out.writeByte(1); // dummy bind address port
        for (int i = 0; i < 6; ++i)
            out.writeByte(0);
        out.flush();

        if (proxySocket == null)
            throw new IllegalStateException("Unable to establish proxy socket");

        return proxySocket;
    }

    private Socket startProxySocket(List<Byte> connectionRequest) {
        SSLSocket socket;
        try {
            socket = (SSLSocket) factory.createSocket(remoteHost, port);

            // enable all supported cipher suites
            String[] supported = socket.getSupportedCipherSuites();
            socket.setEnabledCipherSuites(supported);

            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            // Authenticate with next server
            AuthMethod.sendRequest(in, out, supportedAuth);

            // Relay connect request
            for (Byte b : connectionRequest)
                out.writeByte(b);
            out.flush();

            // Handle request reply
            SocksProtocol.checkVersion(in);
            SocksProtocol.checkReply(in);
            SocksProtocol.checkReserved(in);
            byte addressType = SocksProtocol.checkAddressType(in);
            SocksProtocol.getHostAddressBytes(in, addressType);
            SocksProtocol.getPortBytes(in);

            return socket;
        } catch (IOException ex) {
            return null;
        }
    }
}
