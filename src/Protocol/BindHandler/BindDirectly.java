package Protocol.BindHandler;

import Protocol.SocksProtocol;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.ExecutorService;


public class BindDirectly implements BindHandler{
    private ExecutorService pool;

    public BindDirectly(ExecutorService pool) {
        this.pool = pool;
    }

    @Override
    public Socket handle(DataInputStream in, DataOutputStream out) throws IOException {
        byte reserved = SocksProtocol.checkReserved(in);
        byte addressType = SocksProtocol.checkAddressType(in);
        String destHost = SocksProtocol.getHostAddressByString(in, addressType);
        int destPort = SocksProtocol.getPort(in);

        pool.submit(() -> {
            try (ServerSocket server = new ServerSocket(0)) {
                byte[] bindAddress = InetAddress.getLocalHost().getAddress();
                int bindAddressType = bindAddress.length == 4 ? 1 : 4;
                int bindPort = server.getLocalPort();

                out.writeByte(5);
                out.writeByte(0);
                out.writeByte(0);
                out.writeByte(bindAddressType);
                out.write(bindAddress);
                out.writeShort((short)(bindPort));

                server.setSoTimeout(10000);
                while (true) {
                    Socket socket = server.accept();
                    InetAddress remoteAddress = socket.getInetAddress();
                    if (destHost.equals(remoteAddress.getHostAddress()) && destPort == socket.getPort()) {
                        out.writeByte(5);
                        out.writeByte(0);
                        out.writeByte(0);
                        out.writeByte(addressType);
                        out.write(remoteAddress.getAddress());
                        out.writeShort((short)(socket.getPort()));


                        DataInputStream pin = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                        DataOutputStream pout = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

                        pool.submit(SocksProtocol.forwardStream(in, pout, false));
                        pool.submit(SocksProtocol.forwardStream(pin, out, true));
                    } else {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            //ignore
                        }
                    }
                }
            } catch (IOException e) {
                try {
                    out.writeByte(5);
                    out.writeByte(1);
                    out.writeByte(0);
                    out.writeByte(1);
                    out.writeInt(0);
                    out.writeShort(0);
                } catch (IOException ex) {
                    //ignore
                }
            }
        });

        return null;
    }
}
