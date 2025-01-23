
import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Client {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int PORT = 12345;
    private FileCache cache = new FileCache();
    private Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        Client client = new Client();
        client.run();
    }

    public void run() {
        System.out.println("クライアント起動。コマンドを入力してください (open, read, write, close, exit):");

        while (true) {
            System.out.print("> "); // プロンプトを表示
            String input = scanner.nextLine();
            String[] parts = input.split(" ", 2);
            String command = parts[0].trim().toLowerCase();

            try {
                switch (command) {
                    case "open":
                        if (parts.length < 2) {
                            System.out.println("ファイルパスとモードが必要です。");
                            break;
                        }
                        String[] openParts = parts[1].split(" ", 2);
                        String filePath = openParts[0].trim();
                        String mode = (openParts.length > 1) ? openParts[1].trim().toUpperCase() : "READ_ONLY";
                        if (!mode.equals("READ_ONLY") && !mode.equals("WRITE_ONLY") && !mode.equals("READ_WRITE")) {
                            System.out.println("無効なモードです。READ_ONLY, WRITE_ONLY, READ_WRITE のいずれかを指定してください。");
                            break;
                        }
                        String openResult = open(filePath, mode);
                        if (openResult.startsWith("ERROR:")) {
                            System.out.println("エラー: " + openResult.substring(6));
                        } else {
                            System.out.println(filePath + " を " + mode + " モードで開きました。");
                        }
                        break;
                    case "read":
                        if (parts.length < 2) {
                            System.out.println("ファイルパスが必要です。");
                            break;
                        }
                        String readPath = parts[1].trim();
                        String readMode = cache.getFileMode(readPath);
                        if (readMode == null || (!readMode.equals("READ_ONLY") && !readMode.equals("READ_WRITE"))) {
                            System.out.println("ファイルが適切なモードで開かれていません。");
                            break;
                        }
                        String content = read(readPath);
                        if (content == null) {
                            System.out.println("ファイルがキャッシュにありません。");
                        } else {
                            System.out.println("ファイル内容: " + content);
                        }
                        break;
                    case "write":
                        if (parts.length < 2) {
                            System.out.println("ファイルパスと内容が必要です。");
                            break;
                        }
                        String[] writeParts = parts[1].split(" ", 2);
                        if (writeParts.length < 2) {
                            System.out.println("ファイルパスと内容が必要です。");
                            break;
                        }
                        String writePath = writeParts[0].trim();
                        String writeMode = cache.getFileMode(writePath);
                        if (writeMode == null || (!writeMode.equals("WRITE_ONLY") && !writeMode.equals("READ_WRITE"))) {
                            System.out.println("ファイルが書き込み可能なモードで開かれていません。");
                            break;
                        }
                        String writeContent = writeParts[1].trim();
                        write(writePath, writeContent);
                        System.out.println(writePath + " に内容を書き込みました。");
                        break;
                    case "close":
                        if (parts.length < 2) {
                            System.out.println("ファイルパスが必要です。");
                            break;
                        }
                        String closePath = parts[1].trim();
                        String closeResult = close(closePath);
                        if (closeResult.startsWith("ERROR:")) {
                            System.out.println("エラー: " + closeResult.substring(6));
                        } else {
                            System.out.println(closePath + " を閉じました。");
                        }
                        break;
                    case "exit":
                        System.out.println("クライアントを終了します。");
                        scanner.close();
                        return;
                    default:
                        System.out.println("無効なコマンドです。");
                }
            } catch (IOException e) {
                System.out.println("エラーが発生しました: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public String open(String filePath, String mode) throws IOException {
        String content = "";
         if (!cache.isCached(filePath)) {
            try (Socket socket = new Socket(SERVER_ADDRESS, PORT);
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                 DataInputStream in = new DataInputStream(socket.getInputStream())) {
                out.writeUTF("OPEN"); // コマンドを OPEN に変更
                out.writeUTF(filePath);
                out.writeUTF(mode); // モードを送信
                String response = in.readUTF(); // サーバーからの応答を受信
                if (response.startsWith("ERROR:")) {
                    return response; // エラー応答をそのまま返す
                }
                content = in.readUTF(); // ファイル内容を受信
                cache.cacheFile(filePath, content);
            } catch (IOException e) {
                return "ERROR:" + e.getMessage(); // 例外発生時のエラー応答
            }
        }
        cache.setFileMode(filePath, mode);
        return "OK"; // 成功応答
    }


    public String read(String filePath) {
        return cache.read(filePath);
    }

    public void write(String filePath, String content) {
        cache.write(filePath, content);
    }

    public String close(String filePath) throws IOException {
        if (cache.isModified(filePath)) {
            try (Socket socket = new Socket(SERVER_ADDRESS, PORT);
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                 DataInputStream in = new DataInputStream(socket.getInputStream())) {
                out.writeUTF("CLOSE"); // コマンドを CLOSE に変更
                out.writeUTF(filePath);
                out.writeUTF(cache.getFileMode(filePath)); // モードを送信
                out.writeUTF(cache.read(filePath));
                String response = in.readUTF(); // サーバーからの応答を受信
                return response; // サーバーからの応答をそのまま返す
            } catch (IOException e) {
                return "ERROR:" + e.getMessage(); // 例外発生時のエラー応答
            }
        } else {
            cache.remove(filePath);
            return "OK"; // 変更がない場合は成功応答
        }
    }
}