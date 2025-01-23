import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.net.HttpURLConnection;


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
                             System.out.println("ファイルパスが必要です。");
                             break;
                         }
                          String[] openParts = parts[1].split(" ", 2);
                         String filePath = openParts[0].trim();
                          String mode = (openParts.length > 1) ? openParts[1].trim() : "READ_ONLY";
                         open(filePath, mode);
                         System.out.println(filePath + " を " + mode + " モードで開きました。");
                        break;
                    case "read":
                        if (parts.length < 2) {
                            System.out.println("ファイルパスが必要です。");
                            break;
                        }
                        String readPath = parts[1].trim();
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
                        if(writeParts.length < 2){
                             System.out.println("ファイルパスと内容が必要です。");
                             break;
                         }
                        String writePath = writeParts[0].trim();
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
                       close(closePath);
                         System.out.println(closePath + " を閉じました。");
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

   public void open(String filePath, String mode) throws IOException {
         String content = "";
        if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
            content = downloadFileFromUrl(filePath);
             if(content.isEmpty()){
                 System.out.println("URLからのダウンロードに失敗しました");
                return;
            }
           cache.cacheFile(filePath, content); // URLをキーとしてキャッシュに保存
         }
        else if(!cache.isCached(filePath)){
              try (Socket socket = new Socket(SERVER_ADDRESS, PORT);
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                  DataInputStream in = new DataInputStream(socket.getInputStream())) {
                out.writeUTF("READ");
                 out.writeUTF(filePath);
                content = in.readUTF();
                  cache.cacheFile(filePath, content);
            }
        }
       cache.setFileMode(filePath, mode);
    }

    private String downloadFileFromUrl(String fileUrl) {
          try {
               URL url = new URL(fileUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
               connection.setRequestMethod("GET");

                 int responseCode = connection.getResponseCode();
               if (responseCode == HttpURLConnection.HTTP_OK) {
                     BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                     StringBuilder content = new StringBuilder();
                     String line;
                     while ((line = reader.readLine()) != null) {
                         content.append(line).append("\n");
                     }
                     reader.close();
                   return content.toString();
               } else {
                  System.out.println("HTTPリクエストエラー: " + responseCode);
                  return "";
               }
            } catch (Exception e) {
              e.printStackTrace();
              return "";
           }
    }


    public String read(String filePath) {
        return cache.read(filePath);
    }

   public void write(String filePath, String content) {
        cache.write(filePath, content);
    }

    public void close(String filePath) throws IOException {
          if (cache.isModified(filePath)) {
            try (Socket socket = new Socket(SERVER_ADDRESS, PORT);
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
                out.writeUTF("WRITE");
                out.writeUTF(filePath);
                out.writeUTF(cache.read(filePath));
            }
        }
        cache.remove(filePath);
    }
}