package com.msg;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.fasterxml.jackson.annotation.JsonCreator;

public class GetRoomsMsg {
    int buffer;

    @JsonCreator
    public GetRoomsMsg(){}
}
