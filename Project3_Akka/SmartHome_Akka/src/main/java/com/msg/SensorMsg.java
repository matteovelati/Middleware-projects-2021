package com.msg;

import com.fasterxml.jackson.annotation.JsonCreator;

public class SensorMsg {
    int buffer;

    @JsonCreator
    public SensorMsg(){}
}
