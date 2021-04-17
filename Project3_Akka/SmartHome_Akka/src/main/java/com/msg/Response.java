package com.msg;

import com.fasterxml.jackson.annotation.JsonCreator;

public class Response {

    private final String response;

    public String getResponse() {
        return response;
    }

    @JsonCreator
    public Response(String content){
        this.response = content;
    }
}