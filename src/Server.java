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

        public FileAccessInfo(String mode) {
            this.mode = mode;
            this.lock = new ReentrantReadWriteLock();
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

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (DataInputStream in = new DataInputStream(socket.getInputStream());
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

                String command = in.readUTF();
                String filePath = in.readUTF();

                switch (command) {
                    case "OPEN":
                        String mode = in.readUTF();
                        handleOpenCommand(filePath, mode, out);
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

        private void handleOpenCommand(String filePath, String mode, DataOutputStream out) throws IOException {
            FileAccessInfo accessInfo = fileAccessInfoMap.computeIfAbsent(filePath, k -> new FileAccessInfo(mode));
            accessInfo.lock.readLock().lock(); // open は read lock で保護 (排他制御)
            try {
                if (accessInfo.mode == null) {
                    accessInfo.mode = mode; // 初めて open される場合はモードを設定
                } else if (!accessInfo.mode.equals(mode)) {
                    out.writeUTF("ERROR:ALREADY_OPEN_IN_DIFFERENT_MODE");
                    return;
                }

                System.out.println("OPEN request for file: " + filePath + " in mode: " + mode);
                String content = fileStore.getOrDefault(filePath, "");
                out.writeUTF("OK"); // 成功応答を送信
                out.writeUTF(content);
            } finally {
                accessInfo.lock.readLock().unlock();
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

            accessInfo.lock.writeLock().lock(); // close は write lock で排他制御
            try {
                System.out.println("CLOSE request for file: " + filePath + ", mode: " + mode);
                fileStore.put(filePath, content); // キャッシュされた内容でサーバーのファイルストアを更新
                fileAccessInfoMap.remove(filePath); // ファイルアクセス情報を削除
                out.writeUTF("OK");
            } finally {
                accessInfo.lock.writeLock().unlock();
            }
        }
    }
}
