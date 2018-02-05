package Protocol;

import Protocol.AuthMethod.AuthMethod;
import Protocol.BindHandler.BindHandler;
import Protocol.ConnectionRequestHandler.ConnectHandler;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public abstract class SocksProtocol {
    protected AuthMethod[] localSupportedAuthMethods;
    protected Map<Byte, AuthMethod> byte2localAuth;
    protected AuthMethod[] remoteSupportedAuthMethods;
    protected Map<Byte, AuthMethod> byte2remoteAuth;
    protected ConnectHandler connectHandler;
    protected BindHandler bindHandler;
    protected ExecutorService pool;
    protected ServerSocket server;


    public static byte checkVersion(DataInputStream in) throws IOException {
        byte version = in.readByte();
        if (version != 5)
            throw new IllegalStateException("Socks version other than 5");
        return version;
    }

    public static byte checkMethod(DataInputStream in) throws IOException {
        byte method = in.readByte();
        if (method < 0 || method > 3)
            throw new IllegalStateException("No acceptable methods");
        return method;
    }

    public static byte checkCommand(DataInputStream in) throws IOException {
        byte command = in.readByte();
        if (command != 1 && command != 2 && command != 3)
            throw new IllegalStateException("Unknown command code type");
        return command;
    }

    public static byte checkReply(DataInputStream in) throws IOException {
        byte reply = in.readByte();
        if (reply != 0)
            throw new IllegalStateException("Connection request failed");
        return reply;
    }

    public static byte checkReserved(DataInputStream in) throws IOException {
        byte reserved = in.readByte();
        if (reserved != 0)
            throw new IllegalStateException("Unknown reserved code type");
        return reserved;
    }

    public static byte checkAddressType(DataInputStream in) throws IOException {
        byte addressType = in.readByte();
        if (addressType != 1 && addressType != 3 && addressType != 4)
            throw new IllegalStateException("Unknown address type code:" + addressType);
        return addressType;
    }

    public static String getHostAddressByString(DataInputStream in, byte addressType) throws IOException {
        StringBuilder host = new StringBuilder();
        if (addressType == 1) {
            int addressLength = 4;
            for (int i = 0; i < addressLength; ++i) {
                if (i != 0) host.append(".");
                host.append(String.valueOf(Byte.toUnsignedInt(in.readByte())));
            }
        } else if (addressType == 3) {
            int addressLength = in.readByte();
            for (int i = 0; i < addressLength; ++i) {
                host.append((char)in.readByte());
            }
        } else if (addressType == 4) {
            int addressLength = 16;
            for (int i = 0; i < addressLength; ++i) {
                if (i != 0 && i % 2 == 0) host.append(":");
                host.append(String.valueOf(Byte.toUnsignedInt(in.readByte())));
            }
        } else throw new IllegalStateException("Unknown address type code:" + addressType);

        return host.toString();
    }

    public static List<Byte> getHostAddressBytes(DataInputStream in, byte addressType) throws IOException {
        ArrayList<Byte> addressBytes = new ArrayList<>();

        byte addressLength;
        if (addressType == 1) {
            addressLength = 4;
        } else if (addressType == 3) {
            addressLength = in.readByte();
            addressBytes.add(addressLength);
        } else if (addressType == 4) {
            addressLength = 16;
        } else throw new IllegalStateException("Unknown address type code:" + addressType);

        // dst address
        for (byte i = 0; i < addressLength; ++i) {
            addressBytes.add(in.readByte());
        }

        return addressBytes;
    }

    public static int getPort(DataInputStream in) throws IOException {
        int port = Short.toUnsignedInt(in.readShort());
        if (port < 0)
            throw new IllegalStateException("Unknown port number:" + port);
        return port;
    }

    public static List<Byte> getPortBytes(DataInputStream in) throws IOException {
        ArrayList<Byte> portBytes = new ArrayList<>();
        portBytes.add(in.readByte());
        portBytes.add(in.readByte());
        return portBytes;
    }

    public void handleAuthentication(DataInputStream in, DataOutputStream out) throws IOException{
        byte version = checkVersion(in);

        byte numMethods = in.readByte();
        byte methods[] = new byte[numMethods];
        for (int i = 0; i < numMethods; ++i) {
            methods[i] = in.readByte();
        }

        for (byte bytecode : methods) {
            if (byte2localAuth.containsKey(bytecode)) {
                AuthMethod method = byte2localAuth.get(bytecode);
                out.writeByte(5);
                out.writeByte(bytecode);
                out.flush();
                method.handleRequest(in, out);
                return;
            }
        }
        throw new IllegalStateException("No common supported authentication method between client and server");
    }

    public Socket handleRequest(DataInputStream in, DataOutputStream out) throws IOException {
        byte version = checkVersion(in);
        byte command = checkCommand(in);

        switch (command) {
            case 1:
                return connectHandler.handle(in, out);
            case 2:
                return bindHandler.handle(in, out);
            case 3:
                return connectHandler.handle(in, out);
            default:
                return null;
        }
    }

    public static Runnable forwardStream(DataInputStream from, DataOutputStream to) {
        return () -> {
            try {
                while (true) {
                    byte[] buffer = new byte[2048];
                    int bytesRead = from.read(buffer);
                    if (bytesRead == -1) {
                        throw new IOException("Close socket in proxy chain");
                    }
                    else {
                        to.write(buffer, 0, bytesRead);
                        to.flush();
                    }
                }
            } catch (IOException ex) {
                try {
                    from.close();
                } catch (IOException e) {
                    //
                }
                try {
                    to.close();
                } catch (IOException e) {
                    //
                }
            }
        };
    }

    public abstract ServerSocket setupServer() throws IOException;

    public void runService() {
        try {
            server = setupServer();
            while (!Thread.currentThread().isInterrupted()) {
                Socket socket = server.accept();
                pool.submit(() -> {
                    try {
                        DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

                        handleAuthentication(in, out);
                        Socket proxySocket = handleRequest(in, out);
                        if (proxySocket != null) {
                            DataInputStream pin = new DataInputStream(new BufferedInputStream(proxySocket.getInputStream()));
                            DataOutputStream pout = new DataOutputStream(new BufferedOutputStream(proxySocket.getOutputStream()));

                            pool.submit(forwardStream(in, pout));
                            pool.submit(forwardStream(pin, out));
                        }
                    } catch (IllegalStateException | IOException ex) {
                        ex.printStackTrace();
                        try {
                            socket.close();
                        } catch (IOException e) {
                            //ignore
                        }
                    }
                });
            }
        } catch (IOException ex) {
            //ignore
        } finally {
            shutdownServer();
            shutdownExecutor();
        }
    }

    private void shutdownExecutor() {
        try {
            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            System.err.println("termination interrupted");
        }
        finally {
            if (!pool.isTerminated()) {
                System.err.println("killing non-finished tasks");
            }
            pool.shutdownNow();
        }
    }

    private void shutdownServer() {
        if (server != null) {
            try {
                server.close();
            } catch (IOException ex) {
                //ignore
            }
        }
    }

    public void shutdownService() {
        shutdownServer();
        shutdownExecutor();
    }
}
