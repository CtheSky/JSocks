# JSocks
Socks5 proxy implemented in Java.

### Features
- Support TCP forward and reverse proxy (**connect** and **bind** command in socks5)
- Support chain proxy with multiple servers
- Support SSL communication in proxy

### Files
src/Protocol
- `SocksProtocol.java` : Socks5 protocol implementations
- `AuthMethod` : Different authencitation methods in socks5, currently support no auth and username/password only.
- `ConnectHandler` : Protocol implementations for TCP forward proxy.
- `BindHandler` : Protocol implementations for TCP reverse proxy.

src/Server
- `LocalServer.java` : Listen to local socks5 request and talk to remote server.
- `RemoteServer.java` : Listen to request from `localserver` and relay the request to destination.

test/
- `echoTCPTest.java` : Echo test for TCP forward proxy.
- `echoBindTest.java` : Echo test for TCP reverse proxy.

### To Run
Compile and Run: 
```bash
> java LocalServer
```
```bash
> java RemoteServer
```
Local server will listen at port 1081. (will change to 1080 later)

### TODO
- Add configuration interface, maybe json format
- Add support for UDP relay
- Add more authentication method support
- Extract server related implementation from `SocksProtocal.java` to a standalone class.
