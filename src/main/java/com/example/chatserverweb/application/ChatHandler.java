package com.example.chatserverweb.application;

import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

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
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sendMsg(session, "SERVER|" + now() + "|INFO|Welcome! Please login: LOGIN|yourName");
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

        switch (type.toUpperCase()) {
            case "LOGIN":
                String username = content.split("\\|")[0];
                users.put(username, session);
                sessions.put(session, username);
                userRooms.put(username, "general");
                rooms.computeIfAbsent("general", k -> ConcurrentHashMap.newKeySet()).add(username);
                broadcastRoom("general", "SERVER", now(), "INFO", username + " joined the chat.");
                break;

            case "TEXT":
                String sender = sessions.get(session);
                String room = userRooms.getOrDefault(sender, "general");
                broadcastRoom(room, sender, now(), "TEXT", content);
                break;

            case "EMOJI":
                sender = sessions.get(session);
                room = userRooms.getOrDefault(sender, "general");
                broadcastRoom(room, sender, now(), "EMOJI", content);
                break;

            case "JOIN_ROOM":
                sender = sessions.get(session);
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
                    if (recSession != null) {
                        sendMsg(recSession, sender + "|" + now() + "|PRIVATE|" + msg);
                        sendMsg(session, sender + "|" + now() + "|PRIVATE|" + msg);
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
            String room = userRooms.getOrDefault(user, "general");
            rooms.getOrDefault(room, Collections.emptySet()).remove(user);
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
        String msg = sender + "|" + timestamp + "|" + type + "|" + content;
        roomUsers.forEach(u -> {
            WebSocketSession s = users.get(u);
            if (s != null && s.isOpen()) sendMsg(s, msg);
        });
    }

    private String now() {
        return LocalDateTime.now().format(TIME);
    }
}
