import Protocol.AuthMethod.AuthMethod;
import Protocol.AuthMethod.NoAuth;
import Server.LocalServer;
import Server.RemoteServer;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

public class echoTCPTest {
    private static int echoServerPort = 1088;

    public static void main(String[] args) {

        // start proxy server
        LocalServer localServer = new LocalServer();
        RemoteServer remoteServer = new RemoteServer();
        new Thread(localServer::runService).start();
        new Thread(remoteServer::runService).start();

        // start echo server
        new Thread (() -> {
            try (ServerSocket echo = new ServerSocket(echoServerPort)){
                Socket socket = echo.accept();
                DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

                byte[] buffer = new byte[1024];
                in.read(buffer);
                out.write(buffer);
                out.flush();
                out.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }).start();

        // wait servers to setup and listen
        try {
            TimeUnit.MILLISECONDS.sleep(800);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }

        // connect to proxy and test echo
        try (Socket socket = new Socket("127.0.0.1", 1081)){
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            AuthMethod[] authMethods = {new NoAuth()};
            AuthMethod.sendRequest(in, out, authMethods);

            //connection request
            out.writeByte(5);
            out.writeByte(1);
            out.writeByte(0);
            out.writeByte(1);
            out.writeByte(127);
            out.writeByte(0);
            out.writeByte(0);
            out.writeByte(1);
            out.writeShort(echoServerPort);
            out.flush();

            // connection reply
            for (int i = 0; i < 10; i++)
                in.readByte();

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
}
