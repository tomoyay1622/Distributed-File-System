// Client.java
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets; // 追加
import java.util.Scanner;

public class Client {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int PORT = 12345;
    private Scanner scanner = new Scanner(System.in);
    private Socket socket;
    private BufferedWriter writer;
    private BufferedReader reader;

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
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        System.out.println("サーバーに接続しました。");
    }

    // 接続を閉じる
    public void closeConnection() {
        try {
            if (writer != null) writer.close();
            if (reader != null) reader.close();
            if (socket != null) socket.close();
            System.out.println("サーバーとの接続を閉じました。");
        } catch (IOException e) {
            System.err.println("接続を閉じる際にエラーが発生しました: " + e.getMessage());
        }
    }

    public void run() {
        System.out.println("クライアント起動。コマンドを入力してください (open, read, write, close, send, read_chat, exit):");

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
                        String readResult = read(readPath);
                        if (readResult == null) {
                            System.out.println("エラー: ファイルの読み取りに失敗しました。");
                        } else {
                            System.out.println("ファイル内容:\n" + readResult);
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
                        String writeContent = writeParts[1].trim();
                        String writeResult = write(writePath, writeContent);
                        if (writeResult.startsWith("ERROR:")) {
                            System.out.println("エラー: " + writeResult.substring(6));
                        } else {
                            System.out.println(writePath + " に内容を書き込みました。");
                        }
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
                    case "send":
                        if (parts.length < 2) {
                            System.out.println("メッセージが必要です。");
                            break;
                        }
                        String message = parts[1].trim();
                        String sendResult = sendMessage(message);
                        if (sendResult.startsWith("ERROR:")) {
                            System.out.println("エラー: " + sendResult.substring(6));
                        } else {
                            System.out.println("メッセージが送信されました。");
                        }
                        break;
                    case "read_chat":
                        String chatResult = readChat();
                        if (chatResult.startsWith("ERROR:")) {
                            System.out.println("エラー: " + chatResult.substring(6));
                        } else {
                            System.out.println("=== グループチャット ===\n" + chatResult + "\n======================");
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

    // OPEN コマンドの送信
    public String open(String filePath, String mode) throws IOException {
        writer.write("OPEN\n");
        writer.write(filePath + "\n");
        writer.write(mode + "\n");
        writer.flush();

        String response = reader.readLine();
        if (response.equals("OK")) {
            String content = reader.readLine();
            return "OK";
        } else {
            return response;
        }
    }

    // READ コマンドの送信
    public String read(String filePath) throws IOException {
        writer.write("READ\n");
        writer.write(filePath + "\n");
        writer.flush();

        String response = reader.readLine();
        if (response.equals("OK")) {
            String content = reader.readLine();
            return content;
        } else {
            return null;
        }
    }

    // WRITE コマンドの送信
    public String write(String filePath, String content) throws IOException {
        writer.write("WRITE\n");
        writer.write(filePath + "\n");
        writer.write(content + "\n");
        writer.flush();

        String response = reader.readLine();
        return response;
    }

    // CLOSE コマンドの送信
    public String close(String filePath) throws IOException {
        // モードを指定する必要があります。ここでは単純化のため READ_WRITE と仮定します
        String mode = "READ_WRITE"; // 実際にはファイルを開いた時のモードを追跡する必要があります
        writer.write("CLOSE\n");
        writer.write(filePath + "\n");
        writer.write(mode + "\n");
        writer.write("\n"); // 内容を送信（必要に応じて）
        writer.flush();

        String response = reader.readLine();
        return response;
    }

    // SEND_MESSAGE コマンドの送信
    public String sendMessage(String message) throws IOException {
        writer.write("SEND_MESSAGE\n");
        writer.write(message + "\n");
        writer.flush();

        String response = reader.readLine();
        return response;
    }

    // READ_CHAT コマンドの送信
    public String readChat() throws IOException {
        writer.write("READ_CHAT\n");
        writer.flush();

        String response = reader.readLine();
        if (response.equals("OK")) {
            String chatContent = reader.readLine();
            return chatContent;
        } else {
            return response;
        }
    }
}
