package com.msg;

import com.fasterxml.jackson.annotation.JsonCreator;

public class GetDataMsg {

    final private String roomName;

    public String getRoomName() {
        return roomName;
    }

    @JsonCreator
    public GetDataMsg(String roomName){
        this.roomName = roomName;
    }
}
