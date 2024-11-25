import java.io.*;
import java.net.*;
// import java.util.concurrent.ConcurrentHashMap;

public class Server {
    private static final int PORT = 12345;
    private static final String STORAGE_DIR = "server_storage";
    // private static ConcurrentHashMap<String, String> fileStore = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        // サーバーストレージディレクトリを準備
        File storageDir = new File(STORAGE_DIR);
        if (!storageDir.exists()) {
            storageDir.mkdir();
        }

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
                File file = new File(STORAGE_DIR, filePath);

                switch (command) {
                    case "READ":
                        if (file.exists()) {
                            String content = readFile(file);
                            out.writeUTF(content);
                        } else {
                            out.writeUTF(""); // ファイルが存在しない場合は空文字を返す
                        }
                        break;
                    case "WRITE":
                        String newContent = in.readUTF();
                        writeFile(file, newContent); // ファイルに書き込む（必要なら作成）
                        out.writeUTF("SUCCESS");
                        break;
                    default:
                        out.writeUTF("INVALID_COMMAND");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
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

        // ファイルに書き込むメソッド（必要なら作成）
        private void writeFile(File file, String content) throws IOException {
            // 必要に応じてディレクトリを作成
            file.getParentFile().mkdirs();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                writer.write(content);
            }
        }
    }
}
