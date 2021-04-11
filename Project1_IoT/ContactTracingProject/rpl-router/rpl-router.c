
#include "contiki.h"

// Network libraries
#include "net/routing/routing.h"
#include "net/netstack.h"
#include "net/ipv6/simple-udp.h"

// Log configuration
#include "sys/log.h"
#define LOG_MODULE "B.R."
#define LOG_LEVEL LOG_LEVEL_INFO

// Declare and auto-start this file's process
PROCESS(border_router_process, "RPL Border Router");
AUTOSTART_PROCESSES(&border_router_process);

PROCESS_THREAD(border_router_process, ev, data)
{
    PROCESS_BEGIN();

    // Initialize DAG root
    NETSTACK_ROUTING.root_start();

    #if BORDER_ROUTER_CONF_WEBSERVER
        PROCESS_NAME(webserver_nogui_process);
        process_start(&webserver_nogui_process, NULL);
    #endif /* BORDER_ROUTER_CONF_WEBSERVER */

    LOG_INFO(" RPL Border Router started\n UDP DAG root started\n");
    PROCESS_END();
}
