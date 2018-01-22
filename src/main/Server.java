package main;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

public class Server {

    private static final int PORT = 1082;
    private static final String algorithm = "SSL";
    public static final String username = "cthesky";
    public static final String password = "cthesky";

    private static void handleAuthentication(DataInputStream in, DataOutputStream out) throws IOException{
        byte version = in.readByte();
        if (version != 5)
            throw new IllegalStateException("Socks version other than 5");

        boolean supportUserPwd = false;
        byte numMethods = in.readByte();
        byte methods[] = new byte[numMethods];
        for (int i = 0; i < numMethods; ++i) {
            methods[i] = in.readByte();
            if (methods[i] == 2)
                supportUserPwd = true;
        }

        if (supportUserPwd) {
            out.writeByte(5);
            out.write(2);
            out.flush();
        } else
            throw new IllegalStateException("No supported authentication method");

        version = in.readByte();
        if (version != 5)
            throw new IllegalStateException("Socks version other than 5");

        byte ulen = in.readByte();
        StringBuilder uname = new StringBuilder();
        for (int i = 0; i < ulen; ++i) {
            uname.append((char)in.readByte());
        }

        byte plen = in.readByte();
        StringBuilder pwd = new StringBuilder();
        for (int i = 0; i < plen; ++i) {
            pwd.append((char)in.readByte());
        }

        if (!username.equals(uname.toString()) || !password.equals(pwd.toString())) {
            out.writeByte(5);
            out.writeByte(1);
            out.flush();
            throw new IllegalStateException("Wrong username for password");
        } else {
            out.writeByte(5);
            out.writeByte(0);
            out.flush();
        }
    }

    private static Socket handleConnectionRequest(DataInputStream in, DataOutputStream out) throws IOException {
        byte version = in.readByte();
        if (version != 5)
            throw new IllegalStateException("Socks version other than 5");

        byte command = in.readByte();
        byte reserved = in.readByte();

        byte addrType = in.readByte();
        StringBuilder host = new StringBuilder();
        if (addrType == 1) {
            int addrLength = 4;
            for (int i = 0; i < addrLength; ++i) {
                if (i != 0) host.append(".");
                host.append((char)in.readByte());
            }
        } else if (addrType == 3) {
            int addrLength = in.readByte();
            for (int i = 0; i < addrLength; ++i) {
                host.append((char)in.readByte());
            }
        } else if (addrType == 4) {
            int addrLength = 16;
            for (int i = 0; i < addrLength; ++i) {
                if (i != 0 && i % 2 == 0) host.append(":");
                host.append((char)in.readByte());
            }
        } else throw new IllegalStateException("Unknown address type code");

        short port = in.readShort();
        System.out.println(host);

        Socket proxySocket = establishProxySocket(host.toString(), port);
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

    private static Socket establishProxySocket(String host, short port) {
        Socket socket;
        try {
            socket = new Socket(host, port);
            return socket;
        } catch (IOException ex) {
            return null;
        }
    }

    public static void main(String[] args) {
        ExecutorService pool = Executors.newFixedThreadPool(50);

        try {
            SSLContext context = SSLContext.getInstance(algorithm);
            context.init(null, null, null);
            SSLServerSocketFactory factory
                    = context.getServerSocketFactory();
            SSLServerSocket server
                    = (SSLServerSocket) factory.createServerSocket(PORT);

            String[] supported = server.getSupportedCipherSuites();
            String[] anonCipherSuitesSupported =
                    Stream.of(supported)
                    .filter(s -> s.contains("_anon_"))
                    .toArray(String[]::new);
            String[] toEable = Stream.concat(Stream.of(supported), Stream.of(anonCipherSuitesSupported))
                    .toArray(String[]::new);
            server.setEnabledCipherSuites(toEable);

            while (true) {
                try {
                    Socket socket = server.accept();
                    pool.submit(() -> {
                        try {
                            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

                            handleAuthentication(in, out);
                            Socket proxySocket = handleConnectionRequest(in, out);
                            DataInputStream pin = new DataInputStream(new BufferedInputStream(proxySocket.getInputStream()));
                            DataOutputStream pout = new DataOutputStream(new BufferedOutputStream(proxySocket.getOutputStream()));


                            pool.submit(() -> {
                                try {
                                    while (true) {
                                        byte[] buffer = new byte[1024];
                                        int bytesRead = in.read(buffer);
                                        if (bytesRead == -1) {
                                            proxySocket.shutdownOutput();
                                            break;
                                        }
                                        else {
                                            pout.write(buffer, 0, bytesRead);
                                            pout.flush();
                                        }
                                    }
                                } catch (IOException ex) {

                                }
                            });

                            pool.submit(() -> {
                                try {
                                    while (true) {
                                        byte[] buffer = new byte[1024];
                                        int bytesRead = pin.read(buffer);
                                        if (bytesRead == -1) {
                                            socket.shutdownOutput();
                                            break;
                                        }
                                        else {
                                            out.write(buffer, 0, bytesRead);
                                            out.flush();
                                        }
                                    }
                                } catch (IOException ex) {

                                }
                            });
                        } catch (IOException ex) {
                            System.err.println(ex);
                        }
                    });
                } catch (Exception ex) {
                    // ignore
                }
            }
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}
