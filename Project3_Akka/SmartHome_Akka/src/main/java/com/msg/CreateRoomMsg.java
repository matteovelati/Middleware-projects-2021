package com.msg;


import com.fasterxml.jackson.annotation.JsonCreator;

public class CreateRoomMsg{

    final private String roomName;

    public String getRoomName() {
        return roomName;
    }

    @JsonCreator
    public CreateRoomMsg(String roomName){
        this.roomName = roomName;
    }
}
