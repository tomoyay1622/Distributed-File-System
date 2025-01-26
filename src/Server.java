// Server.java
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

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
        private String clientId; // クライアント識別子
        private BufferedReader reader;
        private BufferedWriter writer;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            this.clientId = "名無し" + socket.getPort();
            System.out.println("ClientHandler created for: " + clientId);
        }

        // ファイルを読み込むメソッド
        private String readFile(File file) throws IOException {
            System.out.println("Reading file: " + file.getAbsolutePath());
            StringBuilder content = new StringBuilder();
            try (BufferedReader fileReader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = fileReader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }
            return content.toString().trim();
        }

        // ファイルに書き込むメソッド（追記モード）
        private void writeFile(File file, String content) throws IOException {
            System.out.println("Writing to file: " + file.getAbsolutePath());
            file.getParentFile().mkdirs(); // ディレクトリが存在しない場合は作成
            try (BufferedWriter fileWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8))) {
                fileWriter.write(content);
            }
        }

        // 不適切な単語をフィルタリングするメソッド
        private boolean isValidMessage(String message) {
            String[] bannedWords = {"fuck", "shit", "bitch"};
            String lowerCaseMessage = message.toLowerCase(); // メッセージを小文字に変換
            for (String word : bannedWords) {
                // 正規表現で単語単位の一致をチェック
                Pattern pattern = Pattern.compile("\\b" + Pattern.quote(word) + "\\b");
                if (pattern.matcher(lowerCaseMessage).find()) {
                    return false;
                }
            }
            return true; // 全てのメッセージを許可
        }

        @Override
        public void run() {
            try {
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                System.out.println("Data streams initialized for: " + clientId);

                while (true) {
                    String command = reader.readLine(); // コマンドを受信
                    if (command == null) {
                        System.out.println("Client " + clientId + " disconnected.");
                        break;
                    }
                    System.out.println("Received command from " + clientId + ": " + command);

                    switch (command) {
                        case "SEND_MESSAGE": // メッセージ送信コマンド
                            String message = reader.readLine(); // メッセージを受信
                            handleSendMessage(message); // メッセージ処理
                            break;
                        case "READ_CHAT":
                            handleReadChat(); // チャット履歴の処理
                            break;
                        default:
                            System.err.println("Invalid command from " + clientId + ": " + command);
                            writer.write("ERROR:INVALID_COMMAND\n");
                            writer.flush();
                    }
                }

            } catch (IOException e) {
                System.err.println("IOException in ClientHandler for " + clientId + ": " + e.getMessage());
                e.printStackTrace();
            } finally {
                // クライアントが切断された場合、保持しているすべてのロックを解除
                try {
                    if (reader != null) reader.close();
                    if (writer != null) writer.close();
                    socket.close();
                    System.out.println("Connection closed for " + clientId);
                } catch (IOException e) {
                    System.err.println("Error closing client socket for " + clientId + ": " + e.getMessage());
                }
            }
        }

        private void handleSendMessage(String message) throws IOException {
            String chatFilePath = "chat.txt"; // 対象ファイル
            System.out.println("Handling SEND_MESSAGE for " + clientId + ": " + message);

            // メッセージのバリデーション
            if (!isValidMessage(message)) {
                writer.write("ERROR:INVALID_MESSAGE\n");
                writer.flush();
                System.out.println("Invalid message from " + clientId + ": " + message);
                return;
            }

            // ロックの取得
            boolean lockAcquired = lockedFiles.putIfAbsent(chatFilePath, clientId) == null;
            if (!lockAcquired) {
                String currentLockOwner = lockedFiles.get(chatFilePath);
                if (!currentLockOwner.equals(clientId)) {
                    // 他のクライアントがロックを保持している場合
                    System.err.println("Chat file is locked by " + currentLockOwner);
                    writer.write("ERROR:CHAT_FILE_LOCKED\n");
                    writer.flush();
                    return;
                }
            }

            try {
                // メッセージにタイムスタンプとクライアントIDを追加
                String timestamp = String.valueOf(System.currentTimeMillis());
                String formattedMessage = timestamp + " [" + clientId + "]: " + message + "\n";

                // chat.txt に追記
                File chatFile = new File(STORAGE_DIR, chatFilePath);
                writeFile(chatFile, formattedMessage);

                writer.write("OK:MESSAGE_SENT\n");
                writer.flush();
                System.out.println("Message sent to chat: " + formattedMessage.trim());
            } catch (IOException e) {
                System.err.println("Failed to write message to chat.txt for " + clientId + ": " + e.getMessage());
                writer.write("ERROR:FAILED_TO_WRITE_CHAT\n");
                writer.flush();
            } finally {
                // ロック解除
                lockedFiles.remove(chatFilePath, clientId);
                System.out.println("Chat file lock released by " + clientId);
            }
        }

        private void handleReadChat() throws IOException {
            String chatFilePath = "chat.txt"; // 対象ファイル
            System.out.println("Handling READ_CHAT for " + clientId);

            // ロックの取得
            boolean lockAcquired = lockedFiles.putIfAbsent(chatFilePath, clientId) == null;
            if (!lockAcquired) {
                String currentLockOwner = lockedFiles.get(chatFilePath);
                if (!currentLockOwner.equals(clientId)) {
                    // 他のクライアントがロックを保持している場合
                    System.err.println("Chat file is locked by " + currentLockOwner);
                    writer.write("ERROR:CHAT_FILE_LOCKED\n");
                    writer.flush();
                    return;
                }
            }

            try {
                // チャットファイルの内容を読み取る
                File chatFile = new File(STORAGE_DIR, chatFilePath);
                String chatContent = "";
                if (chatFile.exists()) {
                    chatContent = readFile(chatFile);
                }

                writer.write("OK\n");
                writer.write(chatContent + "\n");
                writer.flush();
                System.out.println("Chat content sent to " + clientId);
            } catch (IOException e) {
                System.err.println("Failed to read chat.txt for " + clientId + ": " + e.getMessage());
                writer.write("ERROR:FAILED_TO_READ_CHAT\n");
                writer.flush();
            } finally {
                // ロック解除
                lockedFiles.remove(chatFilePath, clientId);
                System.out.println("Chat file lock released by " + clientId);
            }
        }
    }
}
