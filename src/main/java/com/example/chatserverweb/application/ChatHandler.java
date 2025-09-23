package com.example.chatserverweb.application;

import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChatHandler extends TextWebSocketHandler {

    private final Map<String, WebSocketSession> users = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, String> sessions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> userRooms = new ConcurrentHashMap<>();
    private final Map<String, String> userColors = new ConcurrentHashMap<>();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ChatHandler() {
        initDatabase();
    }

    private void initDatabase() {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:chat.db")) {
            Statement stmt = conn.createStatement();
            stmt.execute("CREATE TABLE IF NOT EXISTS users (username TEXT PRIMARY KEY, password TEXT)");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private boolean authenticate(String username, String password, WebSocketSession session) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:chat.db")) {
            PreparedStatement pstmt = conn.prepareStatement("SELECT password FROM users WHERE username = ?");
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                if (rs.getString("password").equals(password)) {
                    if (users.containsKey(username)) {
                        sendMsg(session, "SERVER|" + now() + "|ERROR|Username already in use.");
                        return false;
                    }
                    return true;
                } else {
                    sendMsg(session, "SERVER|" + now() + "|ERROR|Invalid password.");
                    return false;
                }
            } else {
                // Register new user
                PreparedStatement insert = conn.prepareStatement("INSERT INTO users (username, password) VALUES (?, ?)");
                insert.setString(1, username);
                insert.setString(2, password);
                insert.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            sendMsg(session, "SERVER|" + now() + "|ERROR|Database error.");
            return false;
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sendMsg(session, "SERVER|" + now() + "|INFO|Welcome! Please login: LOGIN|yourName|yourPassword");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        String[] parts = payload.split("\\|", 4); // clientId|timestamp|type|payload

        if (parts.length < 3) {
            sendMsg(session, "SERVER|" + now() + "|ERROR|Invalid message format.");
            return;
        }

        String clientId = parts[0];
        String timestamp = parts[1];
        String type = parts[2];
        String content = parts.length > 3 ? parts[3] : "";

        if (type.equalsIgnoreCase("TEXT") && content.startsWith("/")) {
            if (content.equalsIgnoreCase("/list")) {
                String sender = sessions.get(session);
                String room = userRooms.getOrDefault(sender, "general");
                Set<String> roomUsers = rooms.getOrDefault(room, Collections.emptySet());
                sendMsg(session, "SERVER|" + now() + "|INFO|Users in room: " + String.join(", ", roomUsers));
                return;
            }
            if (content.startsWith("/w ")) {
                String[] cmd = content.split(" ", 3);
                if (cmd.length == 3) {
                    String recipient = cmd[1];
                    String msg = cmd[2];
                    WebSocketSession recSession = users.get(recipient);
                    String sender = sessions.get(session);
                    if (recSession != null) {
                        String color = userColors.getOrDefault(sender, "#000000");
                        sendMsg(recSession, sender + "|" + color + "|" + now() + "|PRIVATE|" + msg);
                        sendMsg(session, sender + "|" + color + "|" + now() + "|PRIVATE|" + msg);
                    } else {
                        sendMsg(session, "SERVER|" + now() + "|ERROR|User not found: " + recipient);
                    }
                }
                return;
            }
        }

        switch (type.toUpperCase()) {
            case "LOGIN":
                String[] creds = content.split("\\|", 2);
                if (creds.length < 2) {
                    sendMsg(session, "SERVER|" + now() + "|ERROR|Username and password required.");
                    break;
                }
                String username = creds[0];
                String password = creds[1];
                if (authenticate(username, password, session)) {
                    String color = String.format("#%06X", (int) (Math.random() * 0xFFFFFF));
                    userColors.put(username, color);
                    users.put(username, session);
                    sessions.put(session, username);
                    userRooms.put(username, "general");
                    rooms.computeIfAbsent("general", k -> ConcurrentHashMap.newKeySet()).add(username);
                    broadcastRoom("general", "SERVER", now(), "INFO", username + " joined the chat.");
                }
                break;

            case "TEXT":
                String sender = sessions.get(session);
                if (sender == null) {
                    sendMsg(session, "SERVER|" + now() + "|ERROR|Please login first.");
                    break;
                }
                String room = userRooms.getOrDefault(sender, "general");
                broadcastRoom(room, sender, now(), "TEXT", content);
                break;

            case "EMOJI":
                sender = sessions.get(session);
                if (sender == null) {
                    sendMsg(session, "SERVER|" + now() + "|ERROR|Please login first.");
                    break;
                }
                room = userRooms.getOrDefault(sender, "general");
                broadcastRoom(room, sender, now(), "EMOJI", content);
                break;

            case "JOIN_ROOM":
                sender = sessions.get(session);
                if (sender == null) {
                    sendMsg(session, "SERVER|" + now() + "|ERROR|Please login first.");
                    break;
                }
                String newRoom = content.split("\\|")[0];
                String oldRoom = userRooms.getOrDefault(sender, "general");
                rooms.getOrDefault(oldRoom, Collections.emptySet()).remove(sender);
                rooms.computeIfAbsent(newRoom, k -> ConcurrentHashMap.newKeySet()).add(sender);
                userRooms.put(sender, newRoom);
                sendMsg(session, "SERVER|" + now() + "|INFO|Joined room: " + newRoom);
                broadcastRoom(newRoom, "SERVER", now(), "INFO", sender + " joined the room.");
                break;

            case "PRIVATE":
                String[] priv = content.split("\\|", 2);
                if (priv.length == 2) {
                    String recipient = priv[0];
                    String msg = priv[1];
                    WebSocketSession recSession = users.get(recipient);
                    sender = sessions.get(session);
                    if (sender == null) {
                        sendMsg(session, "SERVER|" + now() + "|ERROR|Please login first.");
                        break;
                    }
                    if (recSession != null) {
                        String color = userColors.getOrDefault(sender, "#000000");
                        sendMsg(recSession, sender + "|" + color + "|" + now() + "|PRIVATE|" + msg);
                        sendMsg(session, sender + "|" + color + "|" + now() + "|PRIVATE|" + msg);
                    } else {
                        sendMsg(session, "SERVER|" + now() + "|ERROR|User not found: " + recipient);
                    }
                }
                break;

            default:
                sendMsg(session, "SERVER|" + now() + "|ERROR|Unknown message type: " + type);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String user = sessions.remove(session);
        if (user != null) {
            users.remove(user);
            userColors.remove(user);
            String room = userRooms.getOrDefault(user, "general");
            rooms.getOrDefault(room, Collections.emptySet()).remove(user);
            userRooms.remove(user);
            broadcastRoom(room, "SERVER", now(), "INFO", user + " left the chat.");
        }
    }

    private void sendMsg(WebSocketSession session, String msg) {
        try {
            session.sendMessage(new TextMessage(msg));
        } catch (Exception ignored) {}
    }

    private void broadcastRoom(String room, String sender, String timestamp, String type, String content) {
        Set<String> roomUsers = rooms.getOrDefault(room, Collections.emptySet());
        String color = "SERVER".equals(sender) ? "#000000" : userColors.getOrDefault(sender, "#000000");
        String msg = sender + "|" + color + "|" + timestamp + "|" + type + "|" + content;
        roomUsers.forEach(u -> {
            WebSocketSession s = users.get(u);
            if (s != null && s.isOpen()) sendMsg(s, msg);
        });
    }

    private String now() {
        return LocalDateTime.now().format(TIME);
    }

}