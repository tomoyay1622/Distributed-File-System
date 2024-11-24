import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    private static final int PORT = 12345;
    private static ConcurrentHashMap<String, String> fileStore = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server is running...");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class ClientHandler implements Runnable {
        private Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            ) {
                String command = in.readUTF();
                String filePath = in.readUTF();
                switch (command) {
                    case "READ":
                        String content = fileStore.getOrDefault(filePath, "");
                        out.writeUTF(content);
                        break;
                    case "WRITE":
                        String newContent = in.readUTF();
                        fileStore.put(filePath, newContent);
                        out.writeUTF("SUCCESS");
                        break;
                    default:
                        out.writeUTF("INVALID_COMMAND");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
