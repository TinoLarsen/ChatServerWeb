package com.example.chatserverweb.Domain;

import java.util.Date;
import java.util.Objects;

public class TextMessage extends Message {
    private final String text;

    public TextMessage(String text, Client sender, Date timestamp, String id) {
        super(id,sender,timestamp, MessageType.TEXT);
        this.text = Objects.requireNonNull(text, "text must not be null");

    }
    public String getText(){
        return text;
    }
}
