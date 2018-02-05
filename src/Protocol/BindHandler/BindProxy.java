package Protocol.BindHandler;

import Protocol.AuthMethod.AuthMethod;
import Protocol.SocksProtocol;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class BindProxy implements BindHandler {
    private final SSLSocketFactory factory;
    private final String remoteHost;
    private final short port;
    private final AuthMethod[] supportedAuth;

    public BindProxy(String remoteHost, short port, AuthMethod[] supported, SSLSocketFactory factory) {
        this.remoteHost = remoteHost;
        this.port = port;
        this.supportedAuth = supported;
        this.factory = factory;
    }

    @Override
    public Socket handle(DataInputStream in, DataOutputStream out) throws IOException{
        byte reserved = SocksProtocol.checkReserved(in);
        byte addressType = SocksProtocol.checkAddressType(in);
        List<Byte> addressBytes = SocksProtocol.getHostAddressBytes(in, addressType);
        List<Byte> portBytes = SocksProtocol.getPortBytes(in);

        // Start communication with
        SSLSocket proxySocket = null;
        try {
            proxySocket = (SSLSocket) factory.createSocket(remoteHost, port);

            // enable all supported cipher suites
            String[] supported = proxySocket.getSupportedCipherSuites();
            proxySocket.setEnabledCipherSuites(supported);

            DataInputStream pin = new DataInputStream(new BufferedInputStream(proxySocket.getInputStream()));
            DataOutputStream pout = new DataOutputStream(new BufferedOutputStream(proxySocket.getOutputStream()));


            // Authenticate with next server
            AuthMethod.sendRequest(pin, pout, supportedAuth);

            // Relay connect request
            pout.writeByte(5);
            pout.writeByte(2);
            pout.writeByte(reserved);
            pout.writeByte(addressType);
            for (byte b : addressBytes)
                pout.writeByte(b);
            for (byte b : portBytes)
                pout.writeByte(b);
            pout.flush();

            // Handle bind reply
            SocksProtocol.checkVersion(pin);
            SocksProtocol.checkReply(pin);
            reserved = SocksProtocol.checkReserved(pin);
            addressType = SocksProtocol.checkAddressType(pin);
            addressBytes = SocksProtocol.getHostAddressBytes(pin, addressType);
            portBytes = SocksProtocol.getPortBytes(pin);
        } catch (IOException | IllegalStateException ex) {
            if (proxySocket != null) {
                try {
                    proxySocket.close();
                } catch (IOException e) {
                    //ignore
                }
            }
            proxySocket = null;
        }

        out.writeByte(5);
        out.writeByte(proxySocket != null ? 0 : 1);
        out.writeByte(reserved);
        out.writeByte(addressType);
        for (byte b : addressBytes)
            out.writeByte(b);
        for (byte b : portBytes)
            out.writeByte(b);
        out.flush();

        if (proxySocket == null)
            throw new IllegalStateException("Unable to establish proxy socket");

        return proxySocket;
    }

}
