import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Server {
    private static final int PORT = 12345;
    private static final ConcurrentHashMap<String, String> fileStore = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, FileAccessInfo> fileAccessInfoMap = new ConcurrentHashMap<>();

    static class FileAccessInfo {
        String mode;
        ReentrantReadWriteLock lock;
        boolean isLocked;

        public FileAccessInfo(String mode) {
            this.mode = mode;
            this.lock = new ReentrantReadWriteLock();
            this.isLocked = false;
        }
    }

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
        private static final String STORAGE_DIR = "server_storage"; // ストレージディレクトリの定義

        public ClientHandler(Socket socket) {
            this.socket = socket;
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

        @Override
        public void run() {
            try (DataInputStream in = new DataInputStream(socket.getInputStream());
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

                String command = in.readUTF();
                String filePath = in.readUTF();
                File file = new File(STORAGE_DIR, filePath); // ファイルパスをserver_storageに設定

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
                        String closeMode = in.readUTF(); // close時のmodeを受信 (念のため)
                        String content = in.readUTF();
                        handleCloseCommand(filePath, closeMode, content, out);
                        break;
                    default:
                        out.writeUTF("ERROR:INVALID_COMMAND");
                }
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("Client handler exception: " + e.getMessage()); // サーバー側のエラーログ
            }
        }

        private void handleOpenCommand(File file, String filePath, String mode, DataOutputStream out) throws IOException {
            FileAccessInfo accessInfo = fileAccessInfoMap.computeIfAbsent(filePath, k -> new FileAccessInfo(mode));
            accessInfo.lock.readLock().lock(); // open は read lock で保護 (排他制御)
            accessInfo.isLocked = true; // ロック状態を更新
            try {
                if (accessInfo.mode == null) {
                    accessInfo.mode = mode; // 初めて open される場合はモードを設定
                } else if (!accessInfo.mode.equals(mode)) {
                    out.writeUTF("ERROR:ALREADY_OPEN_IN_DIFFERENT_MODE");
                    return;
                }

                System.out.println("OPEN request for file: " + filePath + " in mode: " + mode);
                String content = fileStore.getOrDefault(filePath, "");
                if (content.isEmpty() && file.exists()) {
                    content = readFile(file); // ファイルから内容を読み込む
                    fileStore.put(filePath, content); // キャッシュに保存
                }
                out.writeUTF("OK"); // 成功応答を送信
                out.writeUTF(content);
            } finally {
                // accessInfo.lock.readLock().unlock(); // ここではロックを解除しない
            }
        }

         private void handleReadCommand(String filePath, DataOutputStream out) throws IOException {
            FileAccessInfo accessInfo = fileAccessInfoMap.get(filePath);
            if (accessInfo == null || accessInfo.mode == null) {
                out.writeUTF("ERROR:FILE_NOT_OPEN");
                return;
            }
            if (!accessInfo.mode.equals("READ_ONLY") && !accessInfo.mode.equals("READ_WRITE")) {
                out.writeUTF("ERROR:INCORRECT_MODE_FOR_READ");
                return;
            }

             accessInfo.lock.readLock().lock();
            try {
                System.out.println("READ request for file: " + filePath);
                String content = fileStore.getOrDefault(filePath, "");
                System.out.println("Sending content: " + content);
                out.writeUTF(content);
            } finally {
                 accessInfo.lock.readLock().unlock();
            }
        }

         private void handleWriteCommand(String filePath, DataInputStream in, DataOutputStream out) throws IOException {
            FileAccessInfo accessInfo = fileAccessInfoMap.get(filePath);
             if (accessInfo == null || accessInfo.mode == null) {
                out.writeUTF("ERROR:FILE_NOT_OPEN");
                return;
            }
            if (!accessInfo.mode.equals("WRITE_ONLY") && !accessInfo.mode.equals("READ_WRITE")) {
                out.writeUTF("ERROR:INCORRECT_MODE_FOR_WRITE");
                return;
            }

              accessInfo.lock.writeLock().lock();
              try {
                    String newContent = in.readUTF();
                    System.out.println("WRITE request for file: " + filePath + ", content: " + newContent);
                    fileStore.put(filePath, newContent);
                    out.writeUTF("OK");
               } finally {
                   accessInfo.lock.writeLock().unlock();
               }
        }

        private void handleCloseCommand(String filePath, String mode, String content, DataOutputStream out) throws IOException {
            FileAccessInfo accessInfo = fileAccessInfoMap.get(filePath);
            if (accessInfo == null || accessInfo.mode == null) {
                out.writeUTF("ERROR:FILE_NOT_OPEN");
                return;
            }
            if (!accessInfo.mode.equals(mode)) { // 念のため mode の整合性チェック
                 out.writeUTF("ERROR:MODE_MISMATCH_ON_CLOSE");
                 return;
            }

            // read lockを解除する
            if (accessInfo.isLocked) {
                try {
                    accessInfo.lock.readLock().unlock(); // close時にread lockを解除
                } catch (IllegalMonitorStateException e) {
                    System.err.println("Error unlocking read lock: " + e.getMessage());
                }
                accessInfo.isLocked = false; // ロック状態を更新
            }

            // accessInfo.lock.writeLock().lock(); // close は write lock で排他制御
            try {
                System.out.println("CLOSE request for file: " + filePath + ", mode: " + mode);
                fileStore.put(filePath, content); // キャッシュされた内容でサーバーのファイルストアを更新
                writeFile(new File(STORAGE_DIR, filePath), content); // ファイルに書き込む
                fileAccessInfoMap.remove(filePath); // ファイルアクセス情報を削除
                out.writeUTF("OK");
            } finally {
                // accessInfo.lock.writeLock().unlock();
            }
        }
    }
}