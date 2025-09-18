package Domain;

import java.awt.*;
import java.util.Date;
import java.util.Objects;

public class PrivateMessage extends Message {
    private final Client receiver;
    private final String text;

    public PrivateMessage(String id, Client sender, Client receiver, Date timestamp, String text) {
        super(id, sender, timestamp, MessageType.PRIVATE);
        this.receiver = Objects.requireNonNull(receiver, "receiver must not be null");
        this.text = Objects.requireNonNull(text, "Text cannot be null");
}
    public Client getReceiver(){
        return receiver;
    }
    public String getText(){


        return text;
    }
}
