// Server.java
import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    private static final int PORT = 12345;
    private static final String STORAGE_DIR = "server_storage"; // ストレージディレクトリの定義

    // ファイルロックを管理するマップ (ファイルパス -> クライアント識別子)
    private static final ConcurrentHashMap<String, String> lockedFiles = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        // ストレージディレクトリが存在しない場合は作成
        File storageDir = new File(STORAGE_DIR);
        if (!storageDir.exists()) {
            if (storageDir.mkdirs()) {
                System.out.println("Storage directory created at: " + storageDir.getAbsolutePath());
            } else {
                System.err.println("Failed to create storage directory at: " + storageDir.getAbsolutePath());
                return;
            }
        }

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server is running on port " + PORT + "...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected from: " + clientSocket.getInetAddress());
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Server exception: " + e.getMessage());
        }
    }

    static class ClientHandler implements Runnable {
        private Socket socket;
        private Map<String, String> openFiles = new HashMap<>(); // クライアントごとのオープンファイル管理
        private String clientId; // クライアント識別子

        public ClientHandler(Socket socket) {
            this.socket = socket;
            this.clientId = socket.getInetAddress().toString() + ":" + socket.getPort();
        }

        // ファイルを読み込むメソッド
        private String readFile(File file) throws IOException {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                return content.toString().trim();
            }
        }

        // ファイルに書き込むメソッド
        private void writeFile(File file, String content) throws IOException {
            file.getParentFile().mkdirs();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                writer.write(content);
            }
        }

        @Override
        public void run() {
            try (DataInputStream in = new DataInputStream(socket.getInputStream());
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

                while (true) {
                    String command;
                    try {
                        command = in.readUTF();
                    } catch (EOFException e) {
                        // クライアントが接続を閉じた
                        break;
                    }

                    String filePath = in.readUTF();

                    if (filePath.startsWith("/")) {
                        filePath = filePath.substring(1);
                    }

                    if (filePath.contains("..")) {
                        out.writeUTF("ERROR:INVALID_FILE_PATH");
                        continue;
                    }

                    File file = new File(STORAGE_DIR, filePath);

                    switch (command) {
                        case "OPEN":
                            String mode = in.readUTF();
                            handleOpenCommand(file, filePath, mode, out);
                            break;
                        case "READ":
                            handleReadCommand(filePath, out);
                            break;
                        case "WRITE":
                            handleWriteCommand(filePath, in, out);
                            break;
                        case "CLOSE":
                            String closeMode = in.readUTF();
                            String content = in.readUTF();
                            handleCloseCommand(filePath, closeMode, content, out);
                            break;
                        default:
                            out.writeUTF("ERROR:INVALID_COMMAND");
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                // クライアントが切断された場合、保持しているすべてのロックを解除
                for (String filePath : openFiles.keySet()) {
                    lockedFiles.remove(filePath, clientId);
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    System.err.println("Error closing client socket: " + e.getMessage());
                }
            }
        }

        private void handleOpenCommand(File file, String filePath, String mode, DataOutputStream out) throws IOException {
            if (!mode.equals("READ_ONLY")) {
                boolean lockAcquired = lockedFiles.putIfAbsent(filePath, clientId) == null;
                if (!lockAcquired) {
                    out.writeUTF("ERROR:FILE_ALREADY_LOCKED");
                    return;
                }
            }

            String content = "";
            if (file.exists()) {
                content = readFile(file);
            }

            openFiles.put(filePath, content);
            out.writeUTF("OK");
            out.writeUTF(content);
        }

        private void handleReadCommand(String filePath, DataOutputStream out) throws IOException {
            if (!openFiles.containsKey(filePath)) {
                out.writeUTF("ERROR:FILE_NOT_OPEN");
                return;
            }

            String content = openFiles.get(filePath);
            out.writeUTF(content);
        }

        private void handleWriteCommand(String filePath, DataInputStream in, DataOutputStream out) throws IOException {
            if (!openFiles.containsKey(filePath)) {
                out.writeUTF("ERROR:FILE_NOT_OPEN");
                return;
            }

            String newContent = in.readUTF();
            openFiles.put(filePath, newContent);
            out.writeUTF("OK");
        }

        private void handleCloseCommand(String filePath, String mode, String content, DataOutputStream out) throws IOException {
            if (!openFiles.containsKey(filePath)) {
                out.writeUTF("ERROR:FILE_NOT_OPEN");
                return;
            }

            if (!mode.equals("READ_ONLY")) {
                writeFile(new File(STORAGE_DIR, filePath), content);
                lockedFiles.remove(filePath, clientId);
            }

            openFiles.remove(filePath);
            out.writeUTF("OK");
        }
    }
}
