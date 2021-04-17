package com.msg;

import com.fasterxml.jackson.annotation.JsonCreator;

public class SetDataMsg {

    final private float targetTemp;
    final private float targetHum;
    final private String roomName;

    public float getTargetHum() {return targetHum;}

    public float getTargetTemp() {return targetTemp;}

    public String getRoomName() {
        return roomName;
    }

    @JsonCreator
    public SetDataMsg(String roomName, float targetTemp, float targetHum){
        this.targetHum = targetHum;
        this.targetTemp = targetTemp;
        this.roomName = roomName;
    }
}
