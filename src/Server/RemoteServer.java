package Server;

import Protocol.AuthMethod.AuthMethod;
import Protocol.AuthMethod.UsernamePasswordAuth;
import Protocol.ConnectionRequestHandler.ConnectDirectly;
import Protocol.SocksProtocol;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.IOException;
import java.net.ServerSocket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RemoteServer extends SocksProtocol{
    private static final int localPort = 1082;
    private static final String username = "cthesky";
    private static final String password = "cthesky";

    public RemoteServer() {
        pool = Executors.newCachedThreadPool();
        localSupportedAuthMethods = new AuthMethod[] {new UsernamePasswordAuth(username, password)};
        byte2localAuth = Stream.of(localSupportedAuthMethods).collect(Collectors.toMap(AuthMethod::bytecode, Function.identity()));
        remoteSupportedAuthMethods = new AuthMethod[] {};
        byte2remoteAuth = new HashMap<>();
        connectionRequestHandler = new ConnectDirectly();
    }

    @Override
    public ServerSocket setupServer() throws IOException{
        SSLContext context;
        try {
            context = SSLContext.getInstance("SSL");
            context.init(null, null, null);
        } catch (KeyManagementException | NoSuchAlgorithmException ex) {
            throw new IOException("Unable to start SSL Context");
        }

        SSLServerSocketFactory factory = context.getServerSocketFactory();
        SSLServerSocket server = (SSLServerSocket) factory.createServerSocket(localPort);

        // enable all supported cipher suites
        String[] supported = server.getSupportedCipherSuites();
        server.setEnabledCipherSuites(supported);

        return server;
    }

    public static void main(String[] args) {
        new RemoteServer().runService();
    }
}
