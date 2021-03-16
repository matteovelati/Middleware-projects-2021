/* COOJA mote
 * this mote implements both UDP and MQTT functionalities.
 * It looks for motes in his neighborhood (transmission range TX) and once
 * found it communicates with those its IPv6 address [fe80::20x:x:x:x].
 * Once the communication arrives, each node checks if the new contact has been
 * with a specific signal node and connect to the MQTT broker at mqtt.neslab.it:3200
 * to store on the backend the contact.
 */

#include "contiki.h"
#include "random.h"
#include "string.h"
/*---------------------------------------------------------------------------*/
// Network libraries
#include "net/routing/routing.h"
#include "net/netstack.h"
#include "net/ipv6/simple-udp.h"
/*---------------------------------------------------------------------------*/
// Neighbor cache library
#include "uip-ds6-nbr.h"
#include "nbr-table.h"
/*---------------------------------------------------------------------------*/
// MQTT
#include "mqtt.h"
#include "rpl.h"
#include "net/ipv6/uip.h"
#include "net/ipv6/sicslowpan.h"
#include "sys/etimer.h"
#include "sys/ctimer.h"
/*---------------------------------------------------------------------------*/
// Log configuration
#include "sys/log.h"
#define LOG_MODULE                  "Client"
#define LOG_LEVEL                   LOG_LEVEL_INFO
/*---------------------------------------------------------------------------*/
// Network parameters
#define UDP_PORT	                5555
#define COMMUNICATION_LAG           (CLOCK_SECOND >> 1)  // 0.5 seconds
#define SEND_INTERVAL		        (CLOCK_SECOND * 20)
static const uip_ipaddr_t           *my_ipaddr;
/*---------------------------------------------------------------------------*/
// MQTT parameters
#define MQTT_BROKER_IP_ADDR         "fd00::1"
#define MQTT_PUB_TOPIC_CONTACTS     "mdw2021/contacts/"
#define MQTT_PUB_TOPIC_SIGNALS      "mdw2021/signals/"
#define MQTT_SUB_TOPIC              "mdw2021/notifications/"
// Publish to the local MQTT broker (mosquitto) running on border router node
static const char *broker_ip        = MQTT_BROKER_IP_ADDR;
#define DEFAULT_ORG_ID              "mqtt-client"
        // A timeout used when waiting for something to happen (connect/disconnect)
#define STATE_MACHINE_PERIODIC     (CLOCK_SECOND * 1)
        // Connections and reconnections
#define RECONNECT_INTERVAL         (CLOCK_SECOND * 2)
#define CONNECTION_STABLE_TIME     (CLOCK_SECOND * 5)
static struct timer                 connection_life;
static uint8_t                      connect_attempt;
        // States of the machine
static uint8_t                      state;
#define STATE_INIT                  0
#define STATE_REGISTERED            1
#define STATE_CONNECTING            2
#define STATE_CONNECTED             3
#define STATE_SUBSCRIBED            4
#define STATE_DISCONNECTED          5
#define STATE_ERROR                 0xFF
        // Client configuration values
#define CONFIG_ORG_ID_LEN           32
#define CONFIG_TYPE_ID_LEN          32
#define CONFIG_AUTH_TOKEN_LEN       32
#define CONFIG_CMD_TYPE_LEN         8
#define CONFIG_IP_ADDR_STR_LEN      64
#define DEFAULT_TYPE_ID             "native"
#define DEFAULT_AUTH_TOKEN          "AUTHZ"
#define DEFAULT_SUBSCRIBE_CMD_TYPE  "+"
#define DEFAULT_BROKER_PORT         1883
#define DEFAULT_PUBLISH_INTERVAL    (CLOCK_SECOND * 5)
#define DEFAULT_KEEP_ALIVE_TIMER    10
        // Timeout used when waiting to connect to a network
#define NET_CONNECT_PERIODIC        (CLOCK_SECOND >> 2)
#define NET_RETRY                   (CLOCK_SECOND * 10)
        // Maximum TCP segment size for outgoing segments of our socket
#define MAX_TCP_SEGMENT_SIZE        32
#define ADDRESS_SIZE                32
#define BUFFER_SIZE                 128
#define APP_BUFFER_SIZE             256
/*---------------------------------------------------------------------------*/
// Processes declaration
PROCESS(udp_client_process,         "UDP client");
PROCESS(mqtt_client_process,        "MQTT");
AUTOSTART_PROCESSES(&udp_client_process, &mqtt_client_process);
/*---------------------------------------------------------------------------*/
static struct simple_udp_connection udp_conn;
// Function to trim the IPv6 address
static char addr_buffer[ADDRESS_SIZE];
static char* trim_ip_addr(const uip_ipaddr_t *ip_addr){
    char *addr_ptr = addr_buffer;
    uiplib_ipaddr_snprint(addr_ptr, ADDRESS_SIZE, ip_addr);
    // Address is of type fd00::20x:x:x:x   --> we remove the first 6 chars
    addr_ptr += 6;
    return addr_ptr;
}
/*---------------------------------------------------------------------------*/

// Data structure declaration for the MQTT client configuration
typedef struct mqtt_client_config {
        char org_id[CONFIG_ORG_ID_LEN];
        char type_id[CONFIG_TYPE_ID_LEN];
        char auth_token[CONFIG_AUTH_TOKEN_LEN];
        char broker_ip[CONFIG_IP_ADDR_STR_LEN];
        char cmd_type[CONFIG_CMD_TYPE_LEN];
        clock_time_t pub_interval;
        uint16_t broker_port;
} mqtt_client_config_t;
static struct mqtt_connection conn;
        // MQTT Buffers for app, Client ID and Topics
static char app_buffer[APP_BUFFER_SIZE];
static char client_id[BUFFER_SIZE];
static char pub_topic_contacts[BUFFER_SIZE];
static char pub_topic_signals[BUFFER_SIZE];
static char sub_topic[BUFFER_SIZE];
static char *buf_ptr;

static struct mqtt_message *msg_ptr = 0;
static struct etimer fsm_periodic_timer;
static mqtt_client_config_t conf;
/*---------------------------------------------------------------------------*/
// MQTT functions
static int construct_pub_topics(void){
    //  Dynamically creates the publication topics of the mote
    //  mdw2021/contacts/2x:x:x:x
    int len;
    int remaining = BUFFER_SIZE;
    buf_ptr = pub_topic_contacts;

    len = snprintf(buf_ptr, remaining, MQTT_PUB_TOPIC_CONTACTS);
    if(len < 0 || len >= BUFFER_SIZE) {
        LOG_ERR("Pub topic: %d, buffer %d\n", len, BUFFER_SIZE);
        return 0;
    }
    remaining -= len;
    buf_ptr += len;

    char *addr_ptr = trim_ip_addr(my_ipaddr);
    len = snprintf(buf_ptr, remaining, "%s", addr_ptr);
    if(len < 0 || len >= BUFFER_SIZE) {
        LOG_ERR("Pub topic: %d, buffer %d\n", len, BUFFER_SIZE);
        return 0;
    }

    //  mdw2021/signals/2x:x:x:x
    remaining = BUFFER_SIZE;
    buf_ptr = pub_topic_signals;

    len = snprintf(buf_ptr, remaining, MQTT_PUB_TOPIC_SIGNALS);
    if(len < 0 || len >= BUFFER_SIZE) {
        LOG_ERR("Pub topic: %d, buffer %d\n", len, BUFFER_SIZE);
        return 0;
    }
    remaining -= len;
    buf_ptr += len;

    len = snprintf(buf_ptr, remaining, "%s", addr_ptr);
    if(len < 0 || len >= BUFFER_SIZE) {
        LOG_ERR("Pub topic: %d, buffer %d\n", len, BUFFER_SIZE);
        return 0;
    }

    return 1;
}
static int construct_sub_topic(void){
    // Dynamically creates the subscription topic of the mote -> mdw2021/signals/20x:x:x:x
    int len;
    int remaining = BUFFER_SIZE;
    buf_ptr = sub_topic;

    len = snprintf(buf_ptr, remaining, MQTT_SUB_TOPIC);
    if(len < 0 || len >= BUFFER_SIZE) {
        LOG_ERR("Sub topic: %d, buffer %d\n", len, BUFFER_SIZE);
        return 0;
    }
    remaining -= len;
    buf_ptr += len;

    char *addr_ptr = trim_ip_addr(my_ipaddr);
    len = snprintf(buf_ptr, remaining, "%s", addr_ptr);
    if(len < 0 || len >= BUFFER_SIZE) {
        LOG_ERR("Sub topic: %d, buffer %d\n", len, BUFFER_SIZE);
        return 0;
    }
    return 1;
}
// Creates che clientID for MQTT communication
static int construct_client_id(void){
    int len = snprintf(client_id, BUFFER_SIZE, "d:%s:%s:%02x%02x%02x%02x%02x%02x",
                       conf.org_id, conf.type_id,
                       linkaddr_node_addr.u8[0], linkaddr_node_addr.u8[1],
                       linkaddr_node_addr.u8[2], linkaddr_node_addr.u8[5],
                       linkaddr_node_addr.u8[6], linkaddr_node_addr.u8[7]);

    // len < 0: Error. Len >= BUFFER_SIZE: Buffer too small
    if(len < 0 || len >= BUFFER_SIZE) {
        LOG_INFO("Client ID: %d, Buffer %d\n", len, BUFFER_SIZE);
        return 0;
    }

    return 1;
}
static void init_config(){
    // Populate configuration with default values
    memset(&conf, 0, sizeof(mqtt_client_config_t));

    memcpy(conf.org_id, DEFAULT_ORG_ID, strlen(DEFAULT_ORG_ID));
    memcpy(conf.type_id, DEFAULT_TYPE_ID, strlen(DEFAULT_TYPE_ID));
    memcpy(conf.auth_token, DEFAULT_AUTH_TOKEN, strlen(DEFAULT_AUTH_TOKEN));
    memcpy(conf.broker_ip, broker_ip, strlen(broker_ip));
    memcpy(conf.cmd_type, DEFAULT_SUBSCRIBE_CMD_TYPE, 1);

    conf.broker_port = DEFAULT_BROKER_PORT;
    conf.pub_interval = DEFAULT_PUBLISH_INTERVAL;

    if(construct_client_id() == 0) {
        // Fatal error. Client ID larger than the buffer
        state = STATE_ERROR;
        return;
    }

    state = STATE_INIT;

    // Starts the FSM ASAP (immediately).
    etimer_set(&fsm_periodic_timer, 0);

}
static void update_config(void){
    // Get one of this node's global addresses.
    my_ipaddr = rpl_get_global_address();

    if(construct_sub_topic() == 0) {
        // Fatal error. Topic larger than the buffer
        state = STATE_ERROR;
        return;
    }

    if(construct_pub_topics() == 0) {
        // Fatal error. Topic larger than the buffer
        state = STATE_ERROR;
        return;
    }
    return;
}
static void connect_to_broker(void){
    // Connect to MQTT server
    mqtt_connect(&conn, conf.broker_ip, conf.broker_port, conf.pub_interval * 3);
    state = STATE_CONNECTING;
}
static void publish(const uip_ipaddr_t *dest_ipaddr){
    int len;
    int remaining = APP_BUFFER_SIZE;
    static char pub_topic[BUFFER_SIZE];

    buf_ptr = app_buffer;

    len = snprintf(buf_ptr, remaining, " {\"me\":\" ");

    if(len < 0 || len >= remaining) {
        LOG_ERR("Buffer too short. Have %d, need %d + \\0\n", remaining, len);
        return;
    }
    remaining -= len;
    buf_ptr += len;

    char *addr_ptr = trim_ip_addr(my_ipaddr);
    len = snprintf(buf_ptr, remaining, "%s\",\"contact\":\"", addr_ptr);

    if(len < 0 || len >= remaining) {
        LOG_ERR("Buffer too short. Have %d, need %d + \\0\n", remaining, len);
        return;
    }
    remaining -= len;
    buf_ptr += len;

    if (dest_ipaddr == NULL){
        // SIGNAL received.
        //LOG_INFO("PUB FUNCTION: signal received\n");
        char *signal_ptr = "SIGNAL";
        len = snprintf(buf_ptr, remaining, "%s\"}", signal_ptr);
        strcpy(pub_topic, pub_topic_signals);
    }
    else {
        // CONTACT received.
        addr_ptr = trim_ip_addr(dest_ipaddr);
        //LOG_INFO("PUB FUNCTION: contact received: %s\n", addr_ptr);
        len = snprintf(buf_ptr, remaining, "%s\"}", addr_ptr);
        strcpy(pub_topic, pub_topic_contacts);
    }

    if(len < 0 || len >= remaining) {
        LOG_ERR("Buffer too short. Have %d, need %d + \\0\n", remaining, len);
        return;
    }

    mqtt_publish(&conn, NULL, pub_topic, (uint8_t *)app_buffer, strlen(app_buffer),
                 MQTT_QOS_LEVEL_1, MQTT_RETAIN_OFF);
}
static void subscribe(void){
    mqtt_status_t status;

    status = mqtt_subscribe(&conn, NULL, sub_topic, MQTT_QOS_LEVEL_0);

    LOG_INFO("Subscribing\n");
    if(status == MQTT_STATUS_OUT_QUEUE_FULL) {
        LOG_INFO("Tried to subscribe but command queue was full!\n");
    }
}
static void mqtt_event(struct mqtt_connection *m, mqtt_event_t event, void *data){
    switch(event) {
        case MQTT_EVENT_CONNECTED: {
            LOG_INFO("Application has a MQTT connection!\n");
            timer_set(&connection_life, CONNECTION_STABLE_TIME);
            state = STATE_CONNECTED;
            break;
        }
        case MQTT_EVENT_DISCONNECTED: {
            LOG_INFO("MQTT Disconnect: reason %u\n", *((mqtt_event_t *)data));

            state = STATE_DISCONNECTED;
            process_poll(&mqtt_client_process);
            break;
        }
        case MQTT_EVENT_PUBLISH: {
            msg_ptr = data;
            if(msg_ptr->first_chunk) {
                LOG_INFO("NOTIFICATION received from mote with ID #%i \n", msg_ptr->payload_length);
            }
            break;
        }
        case MQTT_EVENT_SUBACK: {
            state = STATE_SUBSCRIBED;
            LOG_INFO("Application is subscribed to topic successfully\n");
            break;
        }
        case MQTT_EVENT_UNSUBACK: {
            LOG_INFO("Application is unsubscribed to topic successfully\n");
            break;
        }
        case MQTT_EVENT_PUBACK: {
            LOG_INFO("Publishing complete\n");
            break;
        }
        default:
            LOG_WARN("Application got a unhandled MQTT event: %i\n", event);
            break;
    }
}
static void state_machine(void){
    switch(state) {
        case STATE_INIT:
            LOG_INFO("STATE INIT\n");
            // If we have just been configured register MQTT connection
            mqtt_register(&conn, &mqtt_client_process, client_id, mqtt_event, MAX_TCP_SEGMENT_SIZE);
            mqtt_set_username_password(&conn, "use-token-auth", conf.auth_token);

            // _register() will set auto_reconnect; we don't want that, disabling it
            conn.auto_reconnect = 0;
            connect_attempt = 1;

            state = STATE_REGISTERED;
            // Don't break: go on with REGISTERED state
        case STATE_REGISTERED:
            if(uip_ds6_get_global(ADDR_PREFERRED) != NULL) {
                // Registered and with a global IPv6 address, now connect!
                update_config();
                LOG_INFO("Joined network! Connect attempt %u\n", connect_attempt);
                connect_to_broker();
            }
            etimer_set(&fsm_periodic_timer, NET_CONNECT_PERIODIC);
            return;
            break;
        case STATE_CONNECTING:
            /* Not connected yet. Wait */
            LOG_INFO("Connecting: retry %u...\n", connect_attempt);
            break;
        case STATE_CONNECTED:
        case STATE_SUBSCRIBED:
            // If the timer expired, the connection is stable, no more attempts needed
            if(timer_expired(&connection_life)) {
                connect_attempt = 0;
            }
            if(mqtt_ready(&conn) && conn.out_buffer_sent && state == STATE_CONNECTED) {
                subscribe();
            }
            break;
        case STATE_DISCONNECTED:
            // Disconnect and backoff
            mqtt_disconnect(&conn);
            connect_attempt++;

            LOG_INFO("Disconnected: attempt %u in %lu ticks\n", connect_attempt, NET_RETRY);
            etimer_set(&fsm_periodic_timer, NET_RETRY);

            state = STATE_REGISTERED;
            return;
        case STATE_ERROR:
            // Idle away. The only way out is a new config
            LOG_ERR("ERROR. Bad configuration.\n");
            return;
        default:
            /*
             * 'default' should never happen
             * If we enter here it's because of some error. Stop timers. The only thing
             * that can bring us out is a new config event
             */
            LOG_INFO("ERROR. Default case: State=0x%02x\n", state);
            return;
    }

    // If we didn't return so far, reschedule ourselves
    etimer_set(&fsm_periodic_timer, STATE_MACHINE_PERIODIC);
}

// UDP callback function
static void udp_rx_callback(struct simple_udp_connection *c,
                            const uip_ipaddr_t *sender_addr,    uint16_t sender_port,
                            const uip_ipaddr_t *receiver_addr,  uint16_t receiver_port,
                            const uint8_t *data,                uint16_t datalen) {
    if(uip_ipaddr_cmp(&sender_addr, &receiver_addr))
        return;

    uint8_t isSignal = *(uint8_t *)data;
    if (isSignal == 0){
        // Normal contact, set the sender IPv6 to destination ipaddr;
        publish(sender_addr);
    }
    else {
        // Signal received, set NULL to destination ipaddr;
        publish(NULL);
    }

}

/*---------------------------------------------------------------------------*
                            UDP CLIENT PROCESS
/----------------------------------------------------------------------------*/
PROCESS_THREAD(udp_client_process, ev, data){
    static struct           etimer udp_find_timer;
    static struct           etimer udp_lag_timer;
    static uip_ds6_nbr_t    *nbr;
    static uip_ipaddr_t     *send_to_ipaddr;

    PROCESS_BEGIN();

    // Initialize UDP connection
    simple_udp_register(&udp_conn, UDP_PORT, NULL, UDP_PORT, udp_rx_callback);

    // Get one of this node's global addresses.
    my_ipaddr = rpl_get_global_address();

    // Starting udp communication periodic timer
    etimer_set(&udp_find_timer, random_rand() % SEND_INTERVAL);
    while(1) {
        PROCESS_WAIT_EVENT();
        if (ev == PROCESS_EVENT_TIMER       &&  data == &udp_find_timer){
            // Point to the head of the neighbors list
            nbr = nbr_table_head(ds6_neighbors);
            // Add delay timer
            etimer_set(&udp_lag_timer, COMMUNICATION_LAG);
        }
        else if (ev == PROCESS_EVENT_TIMER  &&  data == &udp_lag_timer){
            if (nbr != NULL){
                send_to_ipaddr = &(nbr->ipaddr);
                uint8_t isSignal = 0;
                simple_udp_sendto(&udp_conn, &isSignal, sizeof(isSignal), send_to_ipaddr);

                // Move pointer down the table stack
                nbr = nbr_table_next(ds6_neighbors, nbr);
                etimer_reset(&udp_lag_timer);
            }
            else {
                // Set a periodic timer for the frequency of communication (jitter of +- 5 seconds on SEND INTERVAL)
                etimer_set(&udp_find_timer, SEND_INTERVAL - (5 * CLOCK_SECOND) + (random_rand() % (10 * CLOCK_SECOND)));
            }
        }
    }

    PROCESS_END();
}

/*---------------------------------------------------------------------------*
                                MQTT PROCESS
/----------------------------------------------------------------------------*/
PROCESS_THREAD(mqtt_client_process, ev, data){
    PROCESS_BEGIN();

    init_config();

    while(1) {
        PROCESS_YIELD();

        if (ev == PROCESS_EVENT_TIMER && data == &fsm_periodic_timer) {
            state_machine();
        }
    }
    PROCESS_END();
}
