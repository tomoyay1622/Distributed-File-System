// Server.java
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
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
        private Map<String, String> openFiles = new HashMap<>(); // クライアントごとのオープンファイル管理
        private String clientId; // クライアント識別子
        private BufferedReader reader;
        private BufferedWriter writer;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            this.clientId = socket.getInetAddress().toString() + ":" + socket.getPort();
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
                        case "OPEN":
                            String filePath = reader.readLine();
                            String mode = reader.readLine();
                            handleOpenCommand(filePath, mode, writer);
                            break;
                        case "READ":
                            String readPath = reader.readLine();
                            handleReadCommand(readPath, writer);
                            break;
                        case "WRITE":
                            String writePath = reader.readLine();
                            String newContent = reader.readLine();
                            handleWriteCommand(writePath, newContent, writer);
                            break;
                        case "CLOSE":
                            String closePath = reader.readLine();
                            String closeMode = reader.readLine();
                            String content = reader.readLine();
                            handleCloseCommand(closePath, closeMode, content, writer);
                            break;
                        case "SEND_MESSAGE":
                            String message = reader.readLine();
                            handleSendMessage(message, writer);
                            break;
                        case "READ_CHAT":
                            handleReadChat(writer);
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
                for (String filePath : openFiles.keySet()) {
                    lockedFiles.remove(filePath, clientId);
                }
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

        // OPEN コマンドの処理
        private void handleOpenCommand(String filePath, String mode, BufferedWriter out) throws IOException {
            System.out.println("Handling OPEN for " + clientId + ": " + filePath + " in mode " + mode);

            if (!mode.equals("READ_ONLY") && !mode.equals("WRITE_ONLY") && !mode.equals("READ_WRITE")) {
                out.write("ERROR:INVALID_MODE\n");
                out.flush();
                return;
            }

            if (!mode.equals("READ_ONLY")) {
                // 書き込みまたは読み書きモードの場合、ロックを取得
                boolean lockAcquired = lockedFiles.putIfAbsent(filePath, clientId) == null;
                if (!lockAcquired) {
                    String currentLockOwner = lockedFiles.get(filePath);
                    if (!currentLockOwner.equals(clientId)) {
                        // 他のクライアントがロックを保持している場合
                        out.write("ERROR:FILE_ALREADY_LOCKED\n");
                        out.flush();
                        System.err.println("File " + filePath + " is already locked by " + currentLockOwner);
                        return;
                    }
                }
            }

            File file = new File(STORAGE_DIR, filePath);
            String content = "";
            if (file.exists()) {
                content = readFile(file);
            }

            openFiles.put(filePath, mode); // ファイルを開いた状態を記録
            out.write("OK\n");
            out.write(content + "\n");
            out.flush();
            System.out.println("File " + filePath + " opened by " + clientId + " in mode " + mode);
        }

        // READ コマンドの処理
        private void handleReadCommand(String filePath, BufferedWriter out) throws IOException {
            System.out.println("Handling READ for " + clientId + ": " + filePath);

            if (!openFiles.containsKey(filePath)) {
                out.write("ERROR:FILE_NOT_OPEN\n");
                out.flush();
                return;
            }

            String mode = openFiles.get(filePath);
            if (!mode.equals("READ_ONLY") && !mode.equals("READ_WRITE")) {
                out.write("ERROR:FILE_NOT_OPEN_FOR_READ\n");
                out.flush();
                return;
            }

            File file = new File(STORAGE_DIR, filePath);
            String content = "";
            if (file.exists()) {
                content = readFile(file);
            }

            out.write("OK\n");
            out.write(content + "\n");
            out.flush();
            System.out.println("File " + filePath + " read by " + clientId);
        }

        // WRITE コマンドの処理
        private void handleWriteCommand(String filePath, String newContent, BufferedWriter out) throws IOException {
            System.out.println("Handling WRITE for " + clientId + ": " + filePath);

            if (!openFiles.containsKey(filePath)) {
                out.write("ERROR:FILE_NOT_OPEN\n");
                out.flush();
                return;
            }

            String mode = openFiles.get(filePath);
            if (!mode.equals("WRITE_ONLY") && !mode.equals("READ_WRITE")) {
                out.write("ERROR:FILE_NOT_OPEN_FOR_WRITE\n");
                out.flush();
                return;
            }

            File file = new File(STORAGE_DIR, filePath);
            writeFile(file, newContent);

            out.write("OK\n");
            out.flush();
            System.out.println("File " + filePath + " written by " + clientId);
        }

        // CLOSE コマンドの処理
        private void handleCloseCommand(String filePath, String mode, String content, BufferedWriter out) throws IOException {
            System.out.println("Handling CLOSE for " + clientId + ": " + filePath);

            if (!openFiles.containsKey(filePath)) {
                out.write("ERROR:FILE_NOT_OPEN\n");
                out.flush();
                return;
            }

            String currentMode = openFiles.get(filePath);
            if (!currentMode.equals(mode)) {
                out.write("ERROR:MODE_MISMATCH\n");
                out.flush();
                return;
            }

            if (!mode.equals("READ_ONLY")) {
                // ファイルを保存（上書き）
                File file = new File(STORAGE_DIR, filePath);
                writeFile(file, content);

                // ロックを解除
                lockedFiles.remove(filePath, clientId);
                System.out.println("Lock released for " + filePath + " by " + clientId);
            }

            openFiles.remove(filePath);
            out.write("OK\n");
            out.flush();
            System.out.println("File " + filePath + " closed by " + clientId);
        }

        // SEND_MESSAGE コマンドの処理
        private void handleSendMessage(String message, BufferedWriter out) throws IOException {
            String chatFilePath = "chat.txt";
            System.out.println("Handling SEND_MESSAGE for " + clientId + ": " + message);

            // メッセージのバリデーション
            if (!isValidMessage(message)) {
                out.write("ERROR:INVALID_MESSAGE\n");
                out.flush();
                System.out.println("Invalid message from " + clientId + ": " + message);
                return;
            }

            // ロックの取得
            boolean lockAcquired = lockedFiles.putIfAbsent(chatFilePath, clientId) == null;
            if (!lockAcquired) {
                String currentLockOwner = lockedFiles.get(chatFilePath);
                if (!currentLockOwner.equals(clientId)) {
                    // 他のクライアントがロックを保持している場合
                    out.write("ERROR:CHAT_FILE_LOCKED\n");
                    out.flush();
                    System.err.println("Chat file is locked by " + currentLockOwner);
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

                out.write("OK:MESSAGE_SENT\n");
                out.flush();
                System.out.println("Message sent to chat: " + formattedMessage.trim());
            } catch (IOException e) {
                System.err.println("Failed to write message to chat.txt for " + clientId + ": " + e.getMessage());
                out.write("ERROR:FAILED_TO_WRITE_CHAT\n");
                out.flush();
            } finally {
                // ロック解除
                lockedFiles.remove(chatFilePath, clientId);
                System.out.println("Chat file lock released by " + clientId);
            }
        }

        // READ_CHAT コマンドの処理
        private void handleReadChat(BufferedWriter out) throws IOException {
            String chatFilePath = "chat.txt";
            System.out.println("Handling READ_CHAT for " + clientId);

            // ロックの取得
            boolean lockAcquired = lockedFiles.putIfAbsent(chatFilePath, clientId) == null;
            if (!lockAcquired) {
                String currentLockOwner = lockedFiles.get(chatFilePath);
                if (!currentLockOwner.equals(clientId)) {
                    // 他のクライアントがロックを保持している場合
                    out.write("ERROR:CHAT_FILE_LOCKED\n");
                    out.flush();
                    System.err.println("Chat file is locked by " + currentLockOwner);
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

                out.write("OK\n");
                out.write(chatContent + "\n");
                out.flush();
                System.out.println("Chat content sent to " + clientId);
            } catch (IOException e) {
                System.err.println("Failed to read chat.txt for " + clientId + ": " + e.getMessage());
                out.write("ERROR:FAILED_TO_READ_CHAT\n");
                out.flush();
            } finally {
                // ロック解除
                lockedFiles.remove(chatFilePath, clientId);
                System.out.println("Chat file lock released by " + clientId);
            }
        }
    }
}
