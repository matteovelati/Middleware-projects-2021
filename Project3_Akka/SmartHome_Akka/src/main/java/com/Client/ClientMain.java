package com.Client;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Address;
import akka.cluster.Cluster;
import com.Server.CentralServerActor;
import com.msg.*;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.*;

public class ClientMain {

    public static void main(String[] args) {

        // initialization
        // load the config file
        Config config = ConfigFactory.load("client.conf");
        // create the system where actors have to be created
        final ActorSystem sys = ActorSystem.create("System", config);

        // create an actor for the client
        ActorRef client = sys.actorOf(ClientActor.props(), "Client");

        int done = -1;
        Scanner input = new Scanner(System.in);
        String roomName;
        String line;
        String value;
        float targetTemp = -100;
        float targetHum = -100;

        while(done != 0){
            try{
                System.out.println(">>>>>> SmartHomeManager commands list <<<<<<");
                System.out.println("0: exit \n" +
                        "1: create new room \n" +
                        "2: request list of available rooms\n" +
                        "3: request data to a room\n" +
                        "4: set data of a room\n" +
                        "5: remove a room\n");

                done = Integer.parseInt(input.next());
                // extract a random number, if is a multiple of 10, trigger a crash
                Random rand = new Random();
                if(rand.nextInt(1000)%10 == 0){
                    client.tell(new CrashMsg(), ActorRef.noSender());
                }
                switch (done){
                    // send a message to the central server to create a new room with the inserted name
                    case 1: {System.out.println("Insert a room name:");
                        roomName = input.next();
                        client.tell(new CreateRoomMsg(roomName), client);
                        break;}
                    // request to the server the list of all the created rooms
                    case 2: {client.tell(new GetRoomsMsg(), client);
                        break;}
                    // start getting data from one of the rooms
                    case 3: {System.out.println("Select a room to get data:");
                        roomName = input.next();
                        client.tell(new GetDataMsg(roomName), client);
                        break;}
                    // set target temperature and humidity of a selected room
                    case 4: {
                        System.out.println("Set data to a room (roomName -t temperature -h humidity):");
                        // read the selected room
                        roomName = input.next();
                        // read all the rest of the line
                        line = input.nextLine();
                        // split the parameters according to spaces and read values after each parameter
                        StringTokenizer st = new StringTokenizer(line);
                        while (st.hasMoreTokens()){
                            value = st.nextToken();
                            // read the temperature value, if present
                            if(value.equals("-t")) {
                                targetTemp = Float.parseFloat(st.nextToken());
                            }
                            else{
                                // read the humidity value, if present
                                if(value.equals("-h")) {
                                    targetHum = Float.parseFloat(st.nextToken());
                                }
                            }
                        }
                        client.tell(new SetDataMsg(roomName, targetTemp, targetHum), client);
                        break;}
                    // decide what room needs to be deleted (based on the name)
                    case 5: {System.out.println("Insert a room name to delete:");
                        roomName = input.next();
                        client.tell(new DeleteRoomMsg(roomName), client);
                        break;}
                    // default case, manage typos
                    default: {System.out.println("operation not supported, select a valid one");
                        break;}
                }
            }
            catch (Exception e){
                e.printStackTrace();
            }
        }

        input.close();
        sys.terminate();

        System.out.println(">>>>>> Closing SmartHomeManager <<<<<<");

    }
}
