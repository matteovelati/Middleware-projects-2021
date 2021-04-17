package com.Server;

import akka.actor.*;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import com.ResumeException;
import com.msg.*;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class RoomServerActor extends AbstractActor {
    // average bounds for standard indoor temperatures
    public static final int LOW_BOUND_TEMP = 20;
    public static final int UP_BOUND_TEMP = 25;
    public static final int LOW_BOUND_HUM = 40;
    public static final int UP_BOUND_HUM = 60;

    // additional data
    public static final int HVAC_POWER = 1000; // HVAC power in Watt

    private Cancellable sensorEventsSchedule;
    private final ActorSystem sys = getContext().system();

    // values of humidity and temperature currently sensed in the room
    private double currentTemperature;
    private double currentHumidity;
    // target values that have to be reached (they cause the power consumption)
    private float targetTemperature = -100;
    private float targetHumidity = -100;


    Cluster cluster = Cluster.get(getContext().getSystem());

    // leave the cluster when the actor is removed
    @Override
    public void postStop() {
        cluster.unsubscribe(getSelf());
    }

    @Override
    public void preStart() {
        // join the cluster when the actor is created
        cluster.subscribe(getSelf(), ClusterEvent.initialStateAsEvents(), ClusterEvent.MemberEvent.class, ClusterEvent.UnreachableMember.class);

        // when the room is created randomly select a temperature and humidity in a standard range. Those values will
        // represent the initial room conditions
        System.out.println(self().path().name() + " - Sensing initial room temperature and humidity");
        Random rand = new Random();
        this.currentHumidity = rand.nextInt(UP_BOUND_HUM - LOW_BOUND_HUM) + LOW_BOUND_HUM;
        this.currentTemperature = rand.nextInt(UP_BOUND_TEMP - LOW_BOUND_TEMP) + LOW_BOUND_TEMP;
        System.out.println("\t\tHum: " + currentHumidity);
        System.out.println("\t\tTemp: " + currentTemperature);
    }

    @Override
    public Receive createReceive(){
        // at the beginning the room behaviour is to not use energy
        return noEnergy();
    }

    // different behaviours which can be changed according to the operational modes
    // when the room is not consuming energy, do not accept sensor messages
    private Receive noEnergy() {
        return receiveBuilder().match(GetDataMsg.class, this::onGetDataMsg)
                .match(SetDataMsg.class, this::onSetDataMsg)
                .match(CrashMsg.class, this::onCrashMsg).build();
    }
    // when the room is consuming energy manage sensor messages
    private Receive energy(){
        return receiveBuilder().match(GetDataMsg.class, this::onGetDataMsg)
                .match(SetDataMsg.class, this::onSetDataMsg)
                .match(SensorMsg.class, this::onSensorMsg)
                .match(CrashMsg.class, this::onCrashMsg).build();
    }

    // dTime = 10 seconds
    void onSensorMsg(SensorMsg msg){
        String timeStamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        boolean endTmp = false;
        boolean endHum = false;
        currentTemperature = (Math.round(currentTemperature * 100.0)/100.0);
        currentHumidity = (Math.round(currentHumidity * 100.0)/100.0);

        // update the temperature if needed (if the temperature system is on)
        if (targetTemperature != -100 && targetTemperature != currentTemperature) {
            if (targetTemperature > currentTemperature)
                currentTemperature += 0.1f;
            else
                currentTemperature -= 0.1f;

            System.out.printf(self().path().name() + " - " + timeStamp + " Current temperature = %.1f \n", currentTemperature);
        }
        else endTmp = true;

        // update the humidity if needed (if the humidity system is on)
        if (targetHumidity != -100 && targetHumidity != currentHumidity) {
            if (targetHumidity > currentHumidity)
                currentHumidity += 0.5;
            else
                currentHumidity -= 0.5;

            System.out.printf(self().path().name() + " - " + timeStamp + " Current Humidity = %.1f \n", currentHumidity);
        }
        else endHum = true;

        // both systems have completed their execution, stop the energy consumption
        if (endTmp && endHum){
            sensorEventsSchedule.cancel();  // cancel the sensor events
            getContext().become(noEnergy());    // the room now is no longer allowed to receive sensor messages
            System.out.println(self().path().name() + " - Target temperature and humidity reached, stopping HVAC");
            targetHumidity = -100;
            targetTemperature = -100;
        }
    }

    // request data about the room
    void onGetDataMsg(GetDataMsg msg){
        currentTemperature = (Math.round(currentTemperature * 100.0)/100.0);
        currentHumidity = (Math.round(currentHumidity * 100.0)/100.0);
        // check if the message is received by the correct room
        if(self().path().name().equals(msg.getRoomName())) {
            double energyConsumption = 0;

            if(targetTemperature != currentTemperature && targetTemperature != -100){
                energyConsumption = energyConsumption + HVAC_POWER * 10; // U = P*dTime (dTime is 10 seconds)
            }
            if(targetHumidity != currentHumidity && targetHumidity != -100){
                energyConsumption = energyConsumption + HVAC_POWER * 10; // U = P*dTime (dTime is 10 seconds)
            }
            // send a message with the requested information
            sender().tell(new Response(
                    self().path().name() + " - Temperature = " + currentTemperature +
                            ", Humidity = " + currentHumidity + ", Energy = " + energyConsumption +
                            ((targetTemperature != -100) ? ("\n\t\t\t\tTarget temperature = " + targetTemperature) : "") +
                            ((targetHumidity != -100) ? ("\n\t\t\t\tTarget humidity = " + targetHumidity) : "")), self());
        }
    }

    // set temperature and humidity of a room
    void onSetDataMsg(SetDataMsg msg){
        // first of all cancel all already scheduled periodic messages if you are changing and already set target
        // temperature while some messages are being sent
        if(targetTemperature != -100 || targetHumidity != -100){
            sensorEventsSchedule.cancel();
        }
        // if the message contains a new target temperature, update it
        if(msg.getTargetTemp() != -100){
            targetTemperature = msg.getTargetTemp();
        }
        // if the message contains a new target humidity, update it
        if(msg.getTargetHum() != -100){
            targetHumidity = msg.getTargetHum();
        }

        // print out useful information in the server console
        System.out.println(self().path().name() + " - Target Temperature = "
                + (targetTemperature != -100 ? targetTemperature : "Not Set") +
                ", Target Humidity " + (targetHumidity != -100 ? targetHumidity : "Not Set"));

        // send a feedback to the client
        sender().tell(new Response(
                "Data of room " + self().path().name() + " correctly set " + " - Target Temperature = "
                        + (targetTemperature != -100 ? targetTemperature : "Not Set") +
                        ", Target Humidity " + (targetHumidity != -100 ? targetHumidity : "Not Set")), self());

        // if we have set a target temp or a target hum, we start consuming energy and the room will periodically
        // sense the room parameters variations simulating sensor data reception by sending self messages every 10 sec
        if(msg.getTargetTemp() != -100 || msg.getTargetHum() != -100){
            getContext().become(energy());
            sensorEventsSchedule = sys.scheduler().scheduleAtFixedRate(
                scala.concurrent.duration.Duration.create(0, TimeUnit.SECONDS),
                scala.concurrent.duration.Duration.create(10, TimeUnit.SECONDS),
                () -> self().tell(new SensorMsg(), ActorRef.noSender()),
                sys.dispatcher()
            );
        }
    }

    // signal that that room is crashing
    void onCrashMsg(CrashMsg msg) throws ResumeException {
        System.out.println("\n!!!!!! " + self().path().name() + " is crashing !!!!!\n");
        throw new ResumeException();
    }

    static Props props() {
        return Props.create(RoomServerActor.class);
    }
}
