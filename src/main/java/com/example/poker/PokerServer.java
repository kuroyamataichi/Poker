import com.sun.net.httpserver.*;
import com.sun.net.httpserver.*;
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.WebSocket;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class PokerServer {
    private static final Set<String> playerNames = ConcurrentHashMap.newKeySet();
    private static final Set<WebSocket> clients = ConcurrentHashMap.newKeySet();
    private static final Map<WebSocket, String> clientNameMap = new ConcurrentHashMap<>();
    private static final Poker poker = new Poker();

    public static void main(String[] args) throws Exception {
        // HTTPサーバ（ポート8080）
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(8080), 0);
        httpServer.createContext("/join", new JoinHandler());
        httpServer.setExecutor(null);
        httpServer.start();
        System.out.println("HTTPサーバ開始（ポート8080）");

        // WebSocketサーバ（ポート12345）
        PokerWebSocketServer wsServer = new PokerWebSocketServer(new InetSocketAddress(12345));
        wsServer.start();
        System.out.println("WebSocketサーバ開始（ポート12345）");
    }

    static class JoinHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                InputStream is = exchange.getRequestBody();
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                String name = Arrays.stream(body.split("&"))
                        .map(s -> s.split("="))
                        .filter(arr -> arr.length == 2 && arr[0].equals("playerName"))
                        .map(arr -> URLDecoder.decode(arr[1], StandardCharsets.UTF_8))
                        .findFirst()
                        .orElse("Unknown");

                System.out.println("参加者登録: " + name);
                playerNames.add(name);

                // リダイレクト
                exchange.getResponseHeaders().add("Location", "/table.html");
                exchange.sendResponseHeaders(302, -1);
            } else {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
            }
        }
    }

    static class PokerWebSocketServer extends WebSocketServer {
        public PokerWebSocketServer(InetSocketAddress address) {
            super(address);
        }

        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            System.out.println("接続: " + conn.getRemoteSocketAddress());
            clients.add(conn);
            conn.send("接続成功。名前を送信してください（例: name:Alice）");
        }

        public void onMessage(WebSocket conn, String message) {
            System.out.println("受信: " + message);

            if (message.startsWith("name:")) {
                String name = message.substring(5).trim();
                clientNameMap.put(conn, name);
                conn.send("ようこそ " + name + " さん！");
                checkAndStartGame();
            }
        }

        private void checkAndStartGame() {
            // 全員が接続したらゲーム開始
            if (clientNameMap.size() == playerNames.size()) {
                System.out.println("全プレイヤー接続済み。ゲーム開始。");

                List<String> names = new ArrayList<>(clientNameMap.values());
                poker.startGame(names);
                poker.dealCards();
                poker.dealTableCards();

                // 各プレイヤーに手札を送信
                for (WebSocket conn : clientNameMap.keySet()) {
                    String name = clientNameMap.get(conn);
                    Poker.Player p = poker.getPlayers().stream()
                            .filter(player -> player.name.equals(name))
                            .findFirst().orElse(null);

                    if (p != null) {
                        conn.send("あなたの手札: " + p.hand);
                    }
                }

                //カードを送信
                broadcast("テーブルカード: " + poker.getTableCards());

                // 勝者
                try {
                    List<Poker.Player> winners = poker.evaluateHandsParallel();
                    StringBuilder winnerMsg = new StringBuilder("勝者: ");
                    for (Poker.Player w : winners) {
                        winnerMsg.append(w.name).append(" ");
                    }
                    broadcast(winnerMsg.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                    broadcast("エラーが発生しました: " + e.getMessage());
                }
            }
        }

        public void broadcast(String message) {
            for (WebSocket client : clients) {
                if (client.isOpen()) {
                    client.send(message);
                }
            }
        }

        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            System.out.println("切断: " + conn.getRemoteSocketAddress());
            clients.remove(conn);
            String name = clientNameMap.remove(conn);
            if (name != null) {
                playerNames.remove(name);
            }
        }

        public void onError(WebSocket conn, Exception ex) {
            System.out.println("エラー: " + ex.getMessage());
        }

        public void onStart() {
            System.out.println("WebSocketサーバーが起動しました。");
        }
    }
}
