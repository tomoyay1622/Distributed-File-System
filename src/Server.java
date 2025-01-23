import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Server {
    private static final int PORT = 12345;
    private static final ConcurrentHashMap<String, String> fileStore = new ConcurrentHashMap<>();
     private static final ConcurrentHashMap<String, ReentrantReadWriteLock> fileLocks = new ConcurrentHashMap<>();


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
           try (DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

                String command = in.readUTF();
                String filePath = in.readUTF();

                ReentrantReadWriteLock lock = fileLocks.computeIfAbsent(filePath, k -> new ReentrantReadWriteLock());

                 switch (command) {
                    case "READ":
                         lock.readLock().lock();
                        try {
                            System.out.println("READ request for file: " + filePath);
                            String content = fileStore.getOrDefault(filePath, "");
                            System.out.println("Sending content: " + content);
                            out.writeUTF(content);
                        } finally {
                            lock.readLock().unlock();
                        }
                        break;
                    case "WRITE":
                        lock.writeLock().lock();
                        try {
                             String newContent = in.readUTF();
                              System.out.println("WRITE request for file: " + filePath + ", content: " + newContent);
                             fileStore.put(filePath, newContent);
                             out.writeUTF("SUCCESS");
                        }finally{
                            lock.writeLock().unlock();
                        }
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