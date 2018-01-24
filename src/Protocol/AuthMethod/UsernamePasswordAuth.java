package Protocol.AuthMethod;

import Protocol.SocksProtocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class UsernamePasswordAuth implements AuthMethod{
    private final String username;
    private final String password;

    public UsernamePasswordAuth(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    public byte bytecode() {
        return 2;
    }

    @Override
    public void sendRequest(DataInputStream in, DataOutputStream out) throws IOException {
        out.writeByte(5);
        byte[] uname = username.getBytes(StandardCharsets.US_ASCII);
        out.writeByte(uname.length);
        for (byte ubyte : uname)
            out.writeByte(ubyte);

        byte[] pwd = password.getBytes(StandardCharsets.US_ASCII);
        out.writeByte(pwd.length);
        for (byte pbyte : pwd)
            out.writeByte(pbyte);
        out.flush();

        byte version = in.readByte();
        if (version != 5)
            throw new IllegalStateException("Socks version other than 5");

        byte status = in.readByte();
        if (status != 0)
            throw new IllegalStateException("Wrong username for password");
    }

    @Override
    public void handleRequest(DataInputStream in, DataOutputStream out) throws IOException {
        byte version = SocksProtocol.checkVersion(in);

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
}
