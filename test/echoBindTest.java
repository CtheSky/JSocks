import Protocol.AuthMethod.AuthMethod;
import Protocol.AuthMethod.NoAuth;
import Protocol.BindHandler.BindDirectly;
import Protocol.SocksProtocol;
import Server.LocalServer;
import Server.RemoteServer;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

public class echoBindTest {

    // address for proxy server to wait for
    private static InetAddress addressWaitingFor;
    private static int portWaitingFor;

    // where proxy server is listening
    private static String bindHostAddress;
    private static int bindPort;

    static {
        try {
            addressWaitingFor = BindDirectly.getLocalAddress();
        } catch (IOException ex) {
            System.err.println("Can't resolve local address");
        }
        portWaitingFor = 1083;
    }

    public static void main(String[] args) {

        // start proxy server
        LocalServer localServer = new LocalServer();
        RemoteServer remoteServer = new RemoteServer();
        Thread t1 = new Thread(localServer::runService); t1.start();
        Thread t2 = new Thread(remoteServer::runService); t2.start();

        // connect to proxy and use bind command
        Thread t3 = new Thread(() -> {
            sleepMilisecs(700);
            try (Socket socket = new Socket("127.0.0.1", 1081)){
                DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

                AuthMethod[] authMethods = {new NoAuth()};
                AuthMethod.sendRequest(in, out, authMethods);

                // bind request
                out.writeByte(5);
                out.writeByte(2);
                out.writeByte(0);
                out.writeByte(1);
                out.write(addressWaitingFor.getAddress());
                out.writeShort(portWaitingFor);
                out.flush();

                // bind reply 1
                SocksProtocol.checkVersion(in);
                SocksProtocol.checkReply(in);
                SocksProtocol.checkReserved(in);
                byte addressType = SocksProtocol.checkAddressType(in);
                bindHostAddress = SocksProtocol.getHostAddressByString(in, addressType);
                bindPort = SocksProtocol.getPort(in);

                // bind reply 2
                SocksProtocol.checkVersion(in);
                SocksProtocol.checkReply(in);
                SocksProtocol.checkReserved(in);
                addressType = SocksProtocol.checkAddressType(in);
                SocksProtocol.getHostAddressByString(in, addressType);
                SocksProtocol.getPort(in);

                byte[] buffer = new byte[1024];
                in.read(buffer);
                out.write(buffer);
                out.flush();
                out.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });t3.start();

        // wait until proxy server is listening
        while (bindHostAddress == null)
            sleepMilisecs(500);

        // connect proxy server and test echo
        try (Socket socket = new Socket()) {
            socket.bind(new InetSocketAddress(addressWaitingFor, portWaitingFor));
            socket.connect(new InetSocketAddress(bindHostAddress, bindPort));
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            String msg = "hello world";
            out.writeUTF(msg);
            out.flush();

            String echo = in.readUTF();
            if (msg.equals(echo))
                System.out.println("Success");
            else
                System.out.println("Fail");
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        // shutdown proxy server
        localServer.shutdownService();
        remoteServer.shutdownService();
    }

    private static void sleepMilisecs(long timeout) {
        // wait servers to bind
        try {
            TimeUnit.MILLISECONDS.sleep(timeout);
        } catch (InterruptedException ex) {
            //ignore
        }
    }
}
