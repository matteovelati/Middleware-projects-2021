
#include "contiki.h"
#include "random.h"

// Network libraries
#include "net/routing/routing.h"
#include "net/netstack.h"
#include "net/ipv6/simple-udp.h"

// Log configuration
#include "sys/log.h"
#define LOG_MODULE "SIGNALER"
#define LOG_LEVEL LOG_LEVEL_INFO

static struct simple_udp_connection udp_conn;

#define SEND_INTERVAL		        (CLOCK_SECOND * 10)
#define COMMUNICATION_LAG           (CLOCK_SECOND >> 1)  // 0.5 seconds
#define UDP_PORT	                5555

// Declare and auto-start this file's process
PROCESS(udp_signaler_process, "UDP signaler");
AUTOSTART_PROCESSES(&udp_signaler_process);

PROCESS_THREAD(udp_signaler_process, ev, data){
    static struct etimer    udp_find_timer;
    static struct etimer    udp_lag_timer;
    static uip_ds6_nbr_t    *nbr;
    static uip_ipaddr_t     *send_to_ipaddr;
    PROCESS_BEGIN();

    // Initialize UDP connection
    simple_udp_register(&udp_conn, UDP_PORT, NULL, UDP_PORT, NULL);

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
            uint8_t isSignal = 1;
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