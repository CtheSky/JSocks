package Server;

import Protocol.AuthMethod.AuthMethod;
import Protocol.AuthMethod.NoAuth;
import Protocol.AuthMethod.UsernamePasswordAuth;
import Protocol.BindHandler.BindProxy;
import Protocol.ConnectHandler.ConnectProxy;
import Protocol.SocksProtocol;

import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LocalServer extends SocksProtocol{
    private final String remoteHost = "localhost";
    private final short remotePort = 1082;
    private final short localPort = 1081;
    private final SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
    private final String username = "cthesky";
    private final String password = "cthesky";

    public LocalServer() {
        pool = Executors.newCachedThreadPool();
        localSupportedAuthMethods = new AuthMethod[] {new NoAuth()};
        byte2localAuth = Stream.of(localSupportedAuthMethods).collect(Collectors.toMap(AuthMethod::bytecode, Function.identity()));
        remoteSupportedAuthMethods = new AuthMethod[] {new UsernamePasswordAuth(username, password)};
        byte2remoteAuth = Stream.of(remoteSupportedAuthMethods).collect(Collectors.toMap(AuthMethod::bytecode, Function.identity()));
        connectHandler = new ConnectProxy(remoteHost, remotePort, remoteSupportedAuthMethods, factory);
        bindHandler = new BindProxy(remoteHost, remotePort, remoteSupportedAuthMethods, factory);
    }

    @Override
    public ServerSocket setupServer() throws IOException {
        return new ServerSocket(localPort);
    }

    public static void main(String[] argv) {
        new LocalServer().runService();
    }
}
