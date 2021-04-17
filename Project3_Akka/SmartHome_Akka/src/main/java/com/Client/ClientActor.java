package com.Client;

import akka.actor.*;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import com.Server.RoomServerActor;
import com.msg.*;

public class ClientActor extends AbstractActor{

    // reference to the server actor used to forward all the messages
    final private ActorSelection server = getContext().actorSelection("akka://System@192.168.1.33:2551/user/CentralServer");

    Cluster cluster = Cluster.get(getContext().getSystem());

    @Override
    public void preStart(){
        cluster.subscribe(getSelf(), ClusterEvent.initialStateAsEvents(), ClusterEvent.MemberEvent.class, ClusterEvent.UnreachableMember.class);
    }

    @Override
    public void postStop() {
        cluster.unsubscribe(getSelf());
    }

    @Override
    public AbstractActor.Receive createReceive() {
        return receiveBuilder().match(CreateRoomMsg.class, this::onCreateRoomMsg).
                match(GetRoomsMsg.class, this::onGetRoomsMsg).
                match(GetDataMsg.class, this::onGetDataMsg).
                match(SetDataMsg.class, this::onSetDataMsg).
                match(CrashMsg.class, this::onCrashMsg).
                match(DeleteRoomMsg.class, this::onDeleteRoomMsg).
                match(Response.class, this::onResponse).build();
    }

    // when the client main calls for a crash msg, forward it to the server
    void onCrashMsg(CrashMsg msg){
        server.tell(msg, ActorRef.noSender());
    }

    // when the client main calls for the creation of a new room, forward it to the server
    void onCreateRoomMsg(CreateRoomMsg msg) {
        server.tell(msg, getSelf());
    }

    // when the client main requests the list of the rooms, forward it to the server
    void onGetRoomsMsg(GetRoomsMsg msg){
        server.tell(msg, getSelf());
    }

    // when the client main wants to get data from a room, forward the request to the server
    void onGetDataMsg(GetDataMsg msg){
        server.tell(msg, getSelf());
    }

    // when the client main wants to set data from a room, forward the request to the server
    void onSetDataMsg(SetDataMsg msg){
        server.tell(msg, getSelf());
    }

    // general message used to print responses on the client console
    void onResponse(Response msg) {
        System.out.println(msg.getResponse());
    }

    // when the user requests to delete a room, the message is forwarded to the server
    void onDeleteRoomMsg(DeleteRoomMsg msg) {
        server.tell(msg, getSelf());
    }

    static Props props() {
        return Props.create(ClientActor.class);
    }

}
