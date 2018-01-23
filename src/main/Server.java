package main;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Stream;

public class Server extends SocksProxy{

    private static final int PORT = 1082;
    private static final String algorithm = "SSL";
    private static final String username = "cthesky";
    private static final String password = "cthesky";

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
            throw new IllegalStateException("Unable to establish proxy socket" + host + port);

        return proxySocket;
    }

    private static Socket establishProxySocket(String host, short port) {
        Socket socket;
        try {
            socket = new Socket(host, port);
            socket.setSoTimeout(100000);
            return socket;
        } catch (IOException ex) {
            return null;
        }
    }

    public static void main(String[] args) {
        ExecutorService pool = Executors.newCachedThreadPool();//newFixedThreadPool(200);

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
            String[] toEnable = Stream.concat(Stream.of(supported), Stream.of(anonCipherSuitesSupported))
                    .toArray(String[]::new);
            server.setEnabledCipherSuites(toEnable);

            while (true) {
                try {
                    Socket socket = server.accept();
                    if (pool instanceof ThreadPoolExecutor) {
                        System.out.println(
                                "Pool size is now " +
                                        ((ThreadPoolExecutor) pool).getActiveCount()
                        );
                    }

                    pool.submit(() -> {
                        try {
                            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

                            handleAuthentication(in, out);
                            Socket proxySocket = handleConnectionRequest(in, out);
                            DataInputStream pin = new DataInputStream(new BufferedInputStream(proxySocket.getInputStream()));
                            DataOutputStream pout = new DataOutputStream(new BufferedOutputStream(proxySocket.getOutputStream()));

                            pool.submit(forwardStream(in, pout, false));
                            pool.submit(forwardStream(pin, out, true));
                        } catch (EOFException | IllegalStateException ex) {
                            System.out.println(ex.toString() + ex.getMessage());
                            try {
                                socket.close();
                            } catch (IOException e) {

                            }
                        } catch (IOException ex) {
                            System.out.println("IO branch");
                            ex.printStackTrace();
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
