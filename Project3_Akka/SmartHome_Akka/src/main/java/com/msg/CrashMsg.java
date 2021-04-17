package com.msg;

import com.fasterxml.jackson.annotation.JsonCreator;

public class CrashMsg {
    int buffer;

    @JsonCreator
    public CrashMsg(){}
}
