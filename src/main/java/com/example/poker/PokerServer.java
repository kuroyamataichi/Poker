import com.sun.net.httpserver.*;
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.WebSocket;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
public class PokerServer {
    private static final Set<String> playerNames = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) throws Exception {
        // 1. HTTPサーバ（/join用）
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(8080), 0);
        httpServer.createContext("/join", new JoinHandler());
        httpServer.setExecutor(null);
        httpServer.start();
        System.out.println("HTTPサーバ（ポート）");

        // 2. WebSocketサーバ
        PokerWebSocketServer wsServer = new PokerWebSocketServer(new InetSocketAddress(12345));
        wsServer.start();
        System.out.println("WebSocketサーバ（ポート）");
    }

    // HTTP POSTハンドラー（プレイヤー名登録）
    static class JoinHandler implements HttpHandler {
        
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                InputStream is = exchange.getRequestBody();
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                // フォームデータ: playerName=名前
                String name = Arrays.stream(body.split("&"))
                        .map(s -> s.split("="))
                        .filter(arr -> arr.length == 2 && arr[0].equals("playerName"))
                        .map(arr -> URLDecoder.decode(arr[1], StandardCharsets.UTF_8))
                        .findFirst()
                        .orElse("Unknown");

                System.out.println("参加者登録: " + name);
                playerNames.add(name);

                // リダイレクトしてtable.htmlに飛ばす
                exchange.getResponseHeaders().add("Location", "/table.html");
                exchange.sendResponseHeaders(302, -1); // 302 Found
            } else {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
            }
        }
    }

    // WebSocketサーバ
    static class PokerWebSocketServer extends WebSocketServer {
        private final Set<WebSocket> clients = ConcurrentHashMap.newKeySet();

        public PokerWebSocketServer(InetSocketAddress address) {
            super(address);
        }

        
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            System.out.println("接続: " + conn.getRemoteSocketAddress());
            clients.add(conn);
            conn.send("サーバーに接続されました。ようこそ！");
        }

        
        public void onMessage(WebSocket conn, String message) {
            System.out.println("受信: " + message);
            // クライアントからの任意のメッセージを全員にブロードキャスト
            for (WebSocket client : clients) {
                if (client != conn && client.isOpen()) {
                    client.send("他プレイヤーから: " + message);
                }
            }
        }

        
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            System.out.println("切断: " + conn.getRemoteSocketAddress());
            clients.remove(conn);
        }

        
        public void onError(WebSocket conn, Exception ex) {
            System.out.println("エラー: " + ex.getMessage());
        }

        
        public void onStart() {
            System.out.println("WebSocketサーバーが起動しました。");
        }
    }
}