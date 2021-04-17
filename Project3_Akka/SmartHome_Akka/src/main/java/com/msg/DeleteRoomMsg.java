package com.msg;


import com.fasterxml.jackson.annotation.JsonCreator;

public class DeleteRoomMsg{

    final private String roomName;

    public String getRoomName() {
        return roomName;
    }

    @JsonCreator
    public DeleteRoomMsg(String roomName){
        this.roomName = roomName;
    }
}
