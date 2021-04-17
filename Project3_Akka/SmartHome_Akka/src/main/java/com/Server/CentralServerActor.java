package com.Server;

import akka.actor.*;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.japi.pf.DeciderBuilder;
import akka.serialization.jackson.JacksonJsonSerializer;
import com.ResumeException;
import com.msg.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CentralServerActor extends AbstractActor{
    private int done = 0;

    Cluster cluster = Cluster.get(getContext().getSystem());

    // the actors subscribes to the cluster
    @Override
    public void preStart(){
        cluster.subscribe(getSelf(), ClusterEvent.initialStateAsEvents(),
                ClusterEvent.MemberEvent.class, ClusterEvent.UnreachableMember.class);
    }

    // the actor leaves the cluster
    @Override
    public void postStop() {
        cluster.unsubscribe(getSelf());
    }

    // the central server also acts as a supervisor for the roomActors (assume that it can't crash)
    private final static SupervisorStrategy strategy = new OneForOneStrategy(
            10,
            Duration.ofMinutes(1),
            // implement a resume strategy after a crash to keep all the inserted settings in the room
            DeciderBuilder.match(ResumeException.class, e -> SupervisorStrategy.resume())
                    .build());

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return strategy;
    }

    // list of refs associated to different rooms
    List<ActorRef> rooms = new ArrayList<>();

    @Override
    public AbstractActor.Receive createReceive() {
        return receiveBuilder().match(CreateRoomMsg.class, this::onCreateRoomMsg).
                match(GetRoomsMsg.class, this::onGetRoomsMsg).
                match(GetDataMsg.class, this::onGetDataMsg).
                match(SetDataMsg.class, this::onSetDataMsg).
                match(CrashMsg.class, this::onCrashMsg).
                match(DeleteRoomMsg.class, this::onDeleteRoomMsg).
                build();
    }

    // when the server gets a message from the client requesting data from a room, it forwards it to the correct room
    void onGetDataMsg(GetDataMsg msg) {
        for (ActorRef room: rooms) {
            // if the room matches the name inserted in the request message
            if(room.path().name().equals(msg.getRoomName())){
                // send a new message to the room with the client as a sender
                room.tell(new GetDataMsg(room.path().name()), sender());
                done = 1;
            }
        }
        if(done == 0){
            sender().tell(new Response("Room not found"), self());
            System.out.println("Room not found");
        }
    }

    // used to change the value of some parameters of the room
    void onSetDataMsg(SetDataMsg msg){
        for (ActorRef room : rooms) {
            // if the room matches the name inserted in the request message
            if(room.path().name().equals(msg.getRoomName())){
                // send a new message to the room with the client as a sender
                room.tell(new SetDataMsg(room.path().name(), msg.getTargetTemp(), msg.getTargetHum()), sender());
            }
        }
    }

    // create a new room
    void onCreateRoomMsg(CreateRoomMsg msg) {
        try {
            // add the room to the list of rooms given the name if there isn't already one
            rooms.add(getContext().actorOf(RoomServerActor.props(), msg.getRoomName()));
            sender().tell(new Response(
                    "Room " + msg.getRoomName() + " successfully created"), self());
        }
        catch (Exception e){
            // if the selected room name already exists print that it is invalid
            System.out.println("Invalid room name");
            sender().tell(new Response(
                    "Invalid room name"), self());
        }
    }

    // delete a room
    void onDeleteRoomMsg(DeleteRoomMsg msg) {
        try {
            for (ActorRef room : rooms) {
                if(room.path().name().equals(msg.getRoomName())){
                    // stop the room actor
                    getContext().stop(room);
                    // remove the room from the list
                    rooms.remove(room);
                    // send a message back to the client
                    sender().tell(new Response(
                            "Room " + msg.getRoomName() + " successfully removed"), self());
                    break;
                }
                else{
                    // if the room name doesn't exist, send an error message to the client
                    sender().tell(new Response(
                            "Invalid room name"), self());
                }
            }
        }
        catch (Exception e){
            e.printStackTrace();
            // if the selected room name doesn't exists print that it is invalid
            System.out.println("Invalid room name");
            sender().tell(new Response(
                    "Invalid room name"), self());
        }
    }

    // respond to the sender with the list of all the available rooms
    void onGetRoomsMsg(GetRoomsMsg msg){
        String string = "List of all the available rooms: ";
        // from the list of rooms extract each room and concatenate the name in single string
        for(ActorRef room : rooms)
            string = string.concat(room.path().name() + " ");

        // send a response to the sender with the string of all the room names
        Response resp = new Response(string);
        sender().tell(resp, self()); // send it back to the sender
    }

    // handle the room crash message
    void onCrashMsg(CrashMsg msg){
        Random rand = new Random();
        if(rooms.size() != 0){
            rooms.get(rand.nextInt(1000)%rooms.size()).tell(new CrashMsg(), ActorRef.noSender());
        }
    }

    public static Props props() {
        return Props.create(CentralServerActor.class);
    }
}
