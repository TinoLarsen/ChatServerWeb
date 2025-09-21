package com.example.chatserverweb.Domain;

public class Client {
    private String name;
    private String id;
    private Cred credentials;
    private ClientStatus status;
    private Room currentRoom;

    public Client (String name, String id, Cred credentials){
        if (name == null || name.isEmpty()){
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        if (id == null || id.isEmpty()){
            throw new IllegalArgumentException("Id cannot be null or empty");
        }
        if (credentials == null){
            throw new IllegalArgumentException("Credentials cannot be null");
        }
        this.name = name;
        this.id = id;
        this.credentials = credentials;
        this.status = ClientStatus.OFFLINE;
        this.currentRoom = null;
    }
    // Getters
    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }

    public Cred getCredentials() {
        return credentials;
    }

    public ClientStatus getStatus() {
        return status;
    }

    public Room getCurrentRoom() {
        return currentRoom;
    }

    // Domain methods
    public void login() {
        this.status = ClientStatus.ONLINE;
    }

    public void logout() {
        this.status = ClientStatus.OFFLINE;
        leaveRoom();
    }

    public void joinRoom(Room room) {
        if (room == null) {
        throw new IllegalArgumentException("room cannot be null");
        }
        if (this.currentRoom != null) {
            throw new IllegalStateException("Client is already in a room");
        }
        this.currentRoom = room;
    }
    public void leaveRoom() {
        if (this.currentRoom != null) {
            this.currentRoom.removeParticipant(this);
            this.currentRoom = null;
        }
    }

    public void sendMessage(Message message) {
        if (message == null){
            throw new IllegalArgumentException("Message cannot be null");
        }
        if (this.currentRoom == null){
            throw new IllegalStateException("Client is not in a room");
        }
        this.currentRoom.broadcast(message);
    }




}
