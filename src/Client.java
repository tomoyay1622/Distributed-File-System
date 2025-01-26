// Client.java
import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Client {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int PORT = 12345;
    private FileCache cache = new FileCache();
    private Scanner scanner = new Scanner(System.in);
    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;

    public static void main(String[] args) {
        Client client = new Client();
        try {
            client.connect();
            client.run();
        } catch (IOException e) {
            System.out.println("サーバーへの接続に失敗しました: " + e.getMessage());
            e.printStackTrace();
        } finally {
            client.closeConnection();
        }
    }

    // サーバーに接続
    public void connect() throws IOException {
        socket = new Socket(SERVER_ADDRESS, PORT);
        out = new DataOutputStream(socket.getOutputStream());
        in = new DataInputStream(socket.getInputStream());
        System.out.println("サーバーに接続しました。");
    }

    // 接続を閉じる
    public void closeConnection() {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
            System.out.println("サーバーとの接続を閉じました。");
        } catch (IOException e) {
            System.err.println("接続を閉じる際にエラーが発生しました: " + e.getMessage());
        }
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

    // Client.java の open メソッド内
    public String open(String filePath, String mode) throws IOException {
        out.writeUTF("OPEN");
        out.writeUTF(filePath);
        out.writeUTF(mode);
        String response = in.readUTF();
        if (response.startsWith("ERROR:")) {
            return response;
        }
        String content = in.readUTF();
        cache.cacheFile(filePath, content);
        cache.setFileMode(filePath, mode);
        return "OK";
    }


    public String read(String filePath) throws IOException {
        out.writeUTF("READ");
        out.writeUTF(filePath);
        String response = in.readUTF();
        if (response.startsWith("ERROR:")) {
            return null;
        }
        return response;
    }

    public void write(String filePath, String content) throws IOException {
        out.writeUTF("WRITE");
        out.writeUTF(filePath);
        out.writeUTF(content);
        String response = in.readUTF();
        if (response.startsWith("ERROR:")) {
            System.out.println("エラー: " + response.substring(6));
        } else {
            // キャッシュを更新する
            cache.write(filePath, content);
        }
    }

    public String close(String filePath) throws IOException {
        out.writeUTF("CLOSE");
        out.writeUTF(filePath);
        String mode = cache.getFileMode(filePath);
        if (mode == null) {
            return "ERROR:FILE_NOT_OPEN";
        }
        out.writeUTF(mode);
        String content = cache.read(filePath);
        out.writeUTF(content != null ? content : "");
        String response = in.readUTF();
        if (response.startsWith("ERROR:")) {
            return response;
        }
        cache.remove(filePath);
        return "OK";
    }
}
