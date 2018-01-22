package main;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;


public class Client {
    private static final String host = "localhost";
    private static final int port = 1082;
    private static final int localport = 1081;
    private static final SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
    private static final String username = "cthesky";
    public static final String password = "cthesky";

    private static void handleAuthentication(DataInputStream in, DataOutputStream out) throws IOException{
        byte version = in.readByte();
        if (version != 5)
            throw new IllegalStateException("Socks version other than 5");

        byte numMethods = in.readByte();
        byte methods[] = new byte[numMethods];
        for (int i = 0; i < numMethods; ++i) {
            methods[i] = in.readByte();
        }

        out.writeByte(5);
        out.write(0);
        out.flush();
    }

    private static Socket handleConnectionRequest(DataInputStream in, DataOutputStream out) throws IOException {
        ArrayList<Byte> requestContent = new ArrayList<>();

        byte version = in.readByte();
        if (version != 5)
            throw new IllegalStateException("Socks version other than 5");
        requestContent.add(version);

        byte command = in.readByte();
        byte reserved = in.readByte();
        requestContent.add(command);
        requestContent.add(reserved);

        byte addrType = in.readByte();
        requestContent.add(addrType);

        byte addrLength;
        if (addrType == 1) {
            addrLength = 4;
        } else if (addrType == 3) {
            addrLength = in.readByte();
            requestContent.add(addrLength);
        } else if (addrType == 4) {
            addrLength = 16;
        } else throw new IllegalStateException("Unknown address type code");

        // dst address
        for (byte i = 0; i < addrLength; ++i) {
            requestContent.add(in.readByte());
        }

        // dst port
        requestContent.add(in.readByte());
        requestContent.add(in.readByte());

        Socket proxySocket = establishProxySocket(requestContent);
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

    static SSLSocket establishProxySocket(ArrayList<Byte> connectionRequest) {
        SSLSocket socket;
        try {
            socket = (SSLSocket) factory.createSocket(host, port);
            // enable all supported cipher suites
            String[] supported = socket.getSupportedCipherSuites();
            socket.setEnabledCipherSuites(supported);

            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            sendProxyAuthentication(in, out);
            sendProxyConnectionRequest(in, out, connectionRequest);

            return socket;
        } catch (IOException ex) {
            System.err.println(ex);
            return null;
        }
    }

    private static void sendProxyAuthentication(DataInputStream in, DataOutputStream out) throws IOException{
        out.writeByte(5);
        out.writeByte(1);
        out.writeByte(2);
        out.flush();

        byte version = in.readByte();
        if (version != 5)
            throw new IllegalStateException("Socks version other than 5");

        byte method = in.readByte();
        if (method != 2)
            throw new IllegalStateException("Unsupported authentication method");


        out.writeByte(5);
        byte[] uname = username.getBytes(StandardCharsets.US_ASCII);
        out.writeByte(uname.length);
        for (byte ubyte : uname)
            out.writeByte(ubyte);

        byte[] pwd = password.getBytes(StandardCharsets.US_ASCII);
        out.writeByte(pwd.length);
        for (byte pbyte : pwd)
            out.writeByte(pbyte);
        out.flush();

        version = in.readByte();
        if (version != 5)
            throw new IllegalStateException("Socks version other than 5");

        byte status = in.readByte();
        if (status != 0)
            throw new IllegalStateException("Wrong username for password");
    }

    private static void sendProxyConnectionRequest(
            DataInputStream in, DataOutputStream out,
            ArrayList<Byte> connectionRequest) throws IOException {
        for (Byte b : connectionRequest)
            out.writeByte(b);
        out.flush();

        byte version = in.readByte();
        if (version != 5)
            throw new IllegalStateException("Socks version other than 5");

        byte reply = in.readByte();
        if (reply != 0)
            throw new IllegalStateException("Connection request failed");

        byte reserved = in.readByte();
        byte addrType = in.readByte();

        byte addrLength;
        if (addrType == 1) {
            addrLength = 4;
        } else if (addrType == 3) {
            addrLength = in.readByte();
        } else if (addrType == 4) {
            addrLength = 16;
        } else throw new IllegalStateException("Unknown address type code");

        // dst address
        for (byte i = 0; i < addrLength; ++i) {
            in.readByte();
        }

        // dst port
        in.readByte();
        in.readByte();
    }

    public static void main(String[] args) {
        try (ServerSocket server = new ServerSocket(localport)) {
            while (true) {
                Socket socket = server.accept();
                DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

                handleAuthentication(in, out);
                Socket proxySocket = handleConnectionRequest(in, out);

                DataInputStream pin = new DataInputStream(new BufferedInputStream(proxySocket.getInputStream()));
                DataOutputStream pout = new DataOutputStream(new BufferedOutputStream(proxySocket.getOutputStream()));

                new Thread(() -> {
                    try {
                        while (true) {
                            byte[] buffer = new byte[1024];
                            int bytesRead = in.read(buffer);
                            if (bytesRead == -1) {
                                proxySocket.shutdownOutput();
                                break;
                            } else {
                                pout.write(buffer, 0, bytesRead);
                                pout.flush();
                            }
                        }
                    } catch (IOException ex) {

                    }
                }).start();
                new Thread(() -> {
                    try {
                        while (true) {
                            byte[] buffer = new byte[1024];
                            int bytesRead = pin.read(buffer);
                            if (bytesRead == -1) {
                                socket.shutdownOutput();
                                break;
                            } else {
                                out.write(buffer, 0, bytesRead);
                                out.flush();
                            }
                        }
                    } catch (IOException ex) {

                    }
                }).start();
            }
        } catch (IOException ex) {
            System.out.println(ex);
        } catch (IllegalStateException ex) {
            System.out.println(ex);
        }
    }
}
