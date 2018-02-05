package Protocol.BindHandler;

import Protocol.SocksProtocol;

import java.io.*;
import java.net.*;
import java.util.Enumeration;
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
                byte[] bindAddress = getLocalAddress().getAddress();
                int bindAddressType = bindAddress.length == 4 ? 1 : 4;
                int bindPort = server.getLocalPort();

                out.writeByte(5);
                out.writeByte(0);
                out.writeByte(0);
                out.writeByte(bindAddressType);
                out.write(bindAddress);
                out.writeShort((short)(bindPort));
                out.flush();

//                server.setSoTimeout(10000);
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

                        pool.submit(SocksProtocol.forwardStream(in, pout));
                        pool.submit(SocksProtocol.forwardStream(pin, out));
                        break;
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

    public static InetAddress getLocalAddress() throws IOException
    {
        Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
        while( ifaces.hasMoreElements() )
        {
            NetworkInterface iface = ifaces.nextElement();
            Enumeration<InetAddress> addresses = iface.getInetAddresses();

            while( addresses.hasMoreElements() )
            {
                InetAddress addr = addresses.nextElement();
                if( addr instanceof Inet4Address && !addr.isLoopbackAddress() )
                {
                    return addr;
                }
            }
        }

        return InetAddress.getLocalHost();
    }
}
