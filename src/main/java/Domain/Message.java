package Domain;

import java.awt.*;
import java.lang.reflect.Type;
import java.util.Date;
import java.util.Objects;

public abstract class Message {
    private final String id;
    private final Client sender;
    private final Date timestamp;
    private final MessageType type;


    protected Message (String id, Client sender, Date timestamp, MessageType type){
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.sender = Objects.requireNonNull(sender, "Sender must not be null");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
    }

    public String getId() {
        return id;
    }
    public Client getSender(){
        return sender;
    }
    public Date getTimestamp(){
        return timestamp;
    }
    public MessageType getType(){
        return type;
    }
}
