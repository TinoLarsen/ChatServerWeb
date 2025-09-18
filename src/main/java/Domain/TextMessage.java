package Domain;

import java.util.Date;
import java.util.Objects;

public class TextMessage extends Message {
    private final String text;

    public TextMessage(String text, Client sender, Date timestamp, String id) {
        super(MessageType.TEXT ,sender,timestamp,id);
        this.text = Objects.requireNonNull(text, "text must not be null");

    }
    public String getText(){
        return text;
    }
}
