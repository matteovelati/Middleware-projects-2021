package com.Server;

import akka.actor.ActorSystem;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class ServerMain {
    public static void main(String[] args) {

        // load the config file
        Config config = ConfigFactory.load("server.conf");

        // create the system where actors have to be created
        ActorSystem sys = ActorSystem.create("System", config);

        // create the remote server
        sys.actorOf(CentralServerActor.props(), "CentralServer");
    }
}
