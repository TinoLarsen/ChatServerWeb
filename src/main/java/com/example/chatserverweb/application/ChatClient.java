package com.example.chatserverweb.application;

import jakarta.websocket.*;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

@ClientEndpoint
public class ChatClient extends Application {

    private Session session;
    private String username;
    private TextFlow log;
    private static final Logger LOGGER = Logger.getLogger(ChatClient.class.getName());
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("WebSocket Chat");

        VBox root = new VBox(10);
        root.setPadding(new Insets(10));

        // Login sektion
        HBox loginBox = new HBox(5);
        TextField nameField = new TextField();
        nameField.setPromptText("Your name");
        PasswordField passField = new PasswordField();
        passField.setPromptText("Password");
        Button loginButton = new Button("Login");
        loginBox.getChildren().addAll(new Label("Name:"), nameField, new Label("Pass:"), passField, loginButton);

        // Chat log visning
        log = new TextFlow();
        log.setStyle("-fx-font-family: 'Segoe UI Emoji', 'Apple Color Emoji', 'Noto Color Emoji', 'Arial Unicode MS', Arial, sans-serif;");
        ScrollPane logScroll = new ScrollPane(log);
        logScroll.setFitToWidth(true);
        logScroll.setPrefHeight(300);

        // Besked input
        HBox msgBox = new HBox(5);
        TextField msgField = new TextField();
        msgField.setPromptText("Type a message... (or /list, /w user msg)");
        Button sendButton = new Button("Send");
        msgBox.getChildren().addAll(msgField, sendButton);

        // Emoji input
        HBox emojiBox = new HBox(5);
        TextField emojiField = new TextField();
        emojiField.setPromptText(":smile: or emoji");
        Button sendEmojiButton = new Button("Send Emoji");
        emojiBox.getChildren().addAll(emojiField, sendEmojiButton);

        // Rum skift
        HBox roomBox = new HBox(5);
        TextField roomField = new TextField();
        roomField.setPromptText("Room name (e.g., general)");
        Button joinRoomButton = new Button("Join Room");
        roomBox.getChildren().addAll(roomField, joinRoomButton);

        // Privat besked
        HBox privateBox = new HBox(5);
        TextField privateToField = new TextField();
        privateToField.setPromptText("Recipient name");
        TextField privateMsgField = new TextField();
        privateMsgField.setPromptText("Private message...");
        Button sendPrivateButton = new Button("Send Private");
        privateBox.getChildren().addAll(privateToField, privateMsgField, sendPrivateButton);

        root.getChildren().addAll(loginBox, logScroll, msgBox, emojiBox, roomBox, privateBox);

        Scene scene = new Scene(root, 600, 600);
        primaryStage.setScene(scene);
        primaryStage.show();

        connectToWebSocket();

        // Event handlers for knapper
        loginButton.setOnAction(e -> {
            username = nameField.getText().trim();
            String pass = passField.getText().trim();
            if (username.isEmpty() || pass.isEmpty() || session == null) return;
            try {
                // Sender login eller opret bruger besked
                session.getBasicRemote().sendText(username + "|" + now() + "|LOGIN|" + username + "|" + pass);
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        });

        sendButton.setOnAction(e -> {
            String text = msgField.getText().trim();
            if (text.isEmpty() || username == null || session == null) return;
            try {
                // Sender besked til nuværende rum
                session.getBasicRemote().sendText(username + "|" + now() + "|TEXT|" + text);
                msgField.clear();
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        });

        sendEmojiButton.setOnAction(e -> {
            String emoji = emojiField.getText().trim();
            if (emoji.isEmpty() || username == null || session == null) return;
            try {
                // Sender emoji besked
                session.getBasicRemote().sendText(username + "|" + now() + "|EMOJI|" + emoji);
                emojiField.clear();
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        });

        joinRoomButton.setOnAction(e -> {
            String room = roomField.getText().trim();
            if (room.isEmpty() || username == null || session == null) return;
            try {
                // Skifter rum
                session.getBasicRemote().sendText(username + "|" + now() + "|JOIN_ROOM|" + room);
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        });

        sendPrivateButton.setOnAction(e -> {
            String recipient = privateToField.getText().trim();
            String msg = privateMsgField.getText().trim();
            if (recipient.isEmpty() || msg.isEmpty() || username == null || session == null) return;
            try {
                // Sender privat besked
                session.getBasicRemote().sendText(username + "|" + now() + "|PRIVATE|" + recipient + "|" + msg);
                privateMsgField.clear();
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        });

        // Enter tast understøttelse
        msgField.setOnAction(sendButton.getOnAction());
        emojiField.setOnAction(sendEmojiButton.getOnAction());
        privateMsgField.setOnAction(sendPrivateButton.getOnAction());
    }

    // Opretter forbindelse til WebSocket server
    private void connectToWebSocket() {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        try {
            URI uri = URI.create("ws://localhost:8080/chat");
            container.connectToServer(this, uri);
        } catch (DeploymentException | IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
    }

    @OnMessage
    public void onMessage(String message) {
        // Modtager beskeder fra serveren og viser dem i chatten
        String[] parts = message.split("\\|", 5);
        if (parts.length < 5) {
            appendToLog(message, Color.BLACK);
            return;
        }
        String sender = parts[0];
        String colorStr = parts[1];
        String timestamp = parts[2];
        String type = parts[3];
        String payload = parts[4];

        Color color = Color.web(colorStr);
        String line = "[" + timestamp + "] ";
        switch (type) {
            case "INFO":
                line += "[INFO] " + payload;
                appendToLog(line, Color.GRAY);
                break;
            case "ERROR":
                line += "[ERROR] " + payload;
                appendToLog(line, Color.RED);
                break;
            case "TEXT":
                appendColored(sender + ": ", color, payload, Color.BLACK);
                break;
            case "EMOJI":
                appendColored(sender + " sent: ", color, payload, Color.PURPLE);
                break;
            case "PRIVATE":
                appendColored("[PRIVATE] " + sender + ": ", color, payload, Color.BLUE);
                break;
            default:
                appendToLog(message, Color.BLACK);
        }
    }

    @OnClose
    public void onClose() {
        // Genopretter forbindelse hvis den lukkes
        appendToLog("Disconnected. Reconnecting...", Color.RED);
        connectToWebSocket();
    }

    // Tilføjer tekst til chat loggen
    private void appendToLog(String text, Color color) {
        Text t = new Text(text + "\n");
        t.setFill(color);
        log.getChildren().add(t);
    }

    // Tilføjer farvet tekst til chat loggen
    private void appendColored(String prefix, Color prefixColor, String content, Color contentColor) {
        Text p = new Text(prefix);
        p.setFill(prefixColor);
        Text c = new Text(content + "\n");
        c.setFill(contentColor);
        log.getChildren().addAll(p, c);
    }

    // Returnerer nuværende tidspunkt som streng
    private String now() {
        return LocalDateTime.now().format(TIME);
    }

}
