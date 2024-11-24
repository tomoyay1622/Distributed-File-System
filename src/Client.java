import java.io.*;
import java.net.*;

public class Client {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int PORT = 12345;
    private FileCache cache = new FileCache();

    public static void main(String[] args) {
        Client client = new Client();
        client.run();
    }

    public void run() {
        try {
            open("example.txt", "READ_WRITE");
            write("example.txt", "Hello, Distributed File System!");
            String content = read("example.txt");
            System.out.println("Read content: " + content);
            close("example.txt");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void open(String filePath, String mode) throws IOException {
        if (!cache.isCached(filePath)) {
            try (Socket socket = new Socket(SERVER_ADDRESS, PORT);
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                 DataInputStream in = new DataInputStream(socket.getInputStream())) {
                out.writeUTF("READ");
                out.writeUTF(filePath);
                String content = in.readUTF();
                cache.cacheFile(filePath, content);
            }
        }
        cache.setFileMode(filePath, mode);
    }

    public String read(String filePath) {
        return cache.read(filePath);
    }

    public void write(String filePath, String content) {
        cache.write(filePath, content);
    }

    public void close(String filePath) throws IOException {
        if (cache.isModified(filePath)) {
            try (Socket socket = new Socket(SERVER_ADDRESS, PORT);
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
                out.writeUTF("WRITE");
                out.writeUTF(filePath);
                out.writeUTF(cache.read(filePath));
            }
        }
        cache.remove(filePath);
    }
}
