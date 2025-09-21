package com.example.chatserverweb.application;

import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatHandler extends TextWebSocketHandler {

    // Gemmer brugernavn -> session
    private final Map<String, WebSocketSession> users = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sendMsg(session, "SERVER|Welcome! Please login with: LOGIN|yourName");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        String[] parts = payload.split("\\|", 3); // fx "LOGIN|Tobias" eller "MSG|Hello world"

        if (parts[0].equalsIgnoreCase("LOGIN")) {
            String username = parts[1];
            users.put(username, session);
            broadcast("SERVER", username + " joined the chat!");
        }
        else if (parts[0].equalsIgnoreCase("MSG")) {
            String text = parts[1];
            String sender = findUserBySession(session);
            if (sender != null) {
                broadcast(sender, text);
            }
        }
        else if (parts[0].equalsIgnoreCase("ROOM")) {
            // eksempel: "ROOM|general|Hej alle!"
            String room = parts[1];
            String text = parts[2];
            String sender = findUserBySession(session);
            if (sender != null) {
                // i skeleton -> bare broadcast, men du kan udvide med rigtige room-maps
                broadcast(sender + "@" + room, text);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String user = findUserBySession(session);
        if (user != null) {
            users.remove(user);
            broadcast("SERVER", user + " left the chat.");
        }
    }

    private String findUserBySession(WebSocketSession session) {
        return users.entrySet().stream()
                .filter(e -> e.getValue().equals(session))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private void sendMsg(WebSocketSession session, String msg) {
        try {
            session.sendMessage(new TextMessage(msg));
        } catch (Exception ignored) {}
    }

    private void broadcast(String sender, String msg) {
        users.values().forEach(s -> sendMsg(s, sender + "|" + msg));
    }
}
