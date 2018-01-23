package main;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

class SocksProxy {

    static Runnable forwardStream(DataInputStream from, DataOutputStream to, boolean needClose) {
        return () -> {
            try {
                while (true) {
                    byte[] buffer = new byte[2048];
                    int bytesRead = from.read(buffer);
                    if (bytesRead == -1) {
                        if (needClose)
                            throw new IOException("Close socket in proxy chain");
                        break;
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
}
