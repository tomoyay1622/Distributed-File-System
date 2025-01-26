// Client.java
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets; // インポートを追加
import java.util.Scanner;

public class Client {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int PORT = 12345;
    private Scanner scanner = new Scanner(System.in);
    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;

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
        System.out.println("クライアント起動。コマンドを入力してください (send, read_chat, exit):");

        while (true) {
            System.out.print("> "); // プロンプトを表示
            String input = scanner.nextLine();
            String[] parts = input.split(" ", 2);
            String command = parts[0].trim().toLowerCase();

            try {
                switch (command) {
                    case "send":
                        if (parts.length < 2) {
                            System.out.println("メッセージが必要です。");
                            break;
                        }
                        String message = parts[1].trim();
                        sendMessage(message);
                        break;
                    case "read_chat":
                        readChat();
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

    // メッセージを送信するメソッド
    public void sendMessage(String message) throws IOException {
        System.out.println("Sending message: " + message);
        writer.write("SEND_MESSAGE\n");
        writer.write(message + "\n");
        writer.flush();
        System.out.println("Message sent to server. Awaiting response...");
        String response = reader.readLine();
        System.out.println("Received response: " + response);
    }

    // チャットを読み取るメソッド
    public void readChat() throws IOException {
        System.out.println("Requesting chat content...");
        writer.write("READ_CHAT\n");
        writer.write("\n"); // ファイルパスは内部で固定
        writer.flush();
        System.out.println("READ_CHAT command sent to server. Awaiting response...");
        String response = reader.readLine();
        System.out.println("Received response: " + response);
        if (response.equals("OK")) {
            String chatContent = reader.readLine();
            System.out.println("=== グループチャット ===");
            System.out.println(chatContent);
            System.out.println("======================");
        } else {
            System.out.println("エラー: " + response);
        }
    }
}
