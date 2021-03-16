package it.polimi.middleware.kafka.enums;

public enum OrderStatus {
    SUBMITTED,      // Order submitted by the customer (userService)
    ACCEPTED,       // Order accepted due to availability of items requested (orderService)
    REFUSED,        // Order refused due to unavailability of items requested (orderService)
    INVALID,        // Order invalid due to unreachable delivery address (shippingService)
    DELIVERING,     // Order delivering (shippingService)
    DELIVERED,      // Order delivered (shippingService)
}
