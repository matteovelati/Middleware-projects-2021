package it.polimi.middleware.kafka.beans;

import it.polimi.middleware.kafka.enums.OrderStatus;

import java.util.HashMap;

public class Order {

    private int id;
    private String username;
    private String address;
    private OrderStatus status;
    private HashMap<String, Integer> mapItemToQuantity;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public HashMap<String, Integer> getMapItemToQuantity() {
        return mapItemToQuantity;
    }

    public void setMapItemToQuantity(HashMap<String, Integer> mapItemToQuantity) {
        this.mapItemToQuantity = mapItemToQuantity;
    }
}
