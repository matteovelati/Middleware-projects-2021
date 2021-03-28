# Apache Kafka Food delivery application

Middleware Technologies for Distributed Systems course [2021 - POLIMI]

Developed and tested with Scala v.2.13 and Apache Spark v.2.6.0

HOW TO RUN:
1.  Change server.properties kafka file configuration:
        - broker.id=<UNIQUE NUMBER>       must be unique among all kafka server
        - listeners=PLAINTEXT://<my_IP_address>:9092
        - those 3 following values must be set equals to the number of kafka server started N.SERVERS
            - offsets.topic.replication.factor=<N.SERVERS>
            - transaction.state.log.replication.factor=<N.SERVERS>
            - transaction.state.log.min.isr=<N.SERVERS>
        - zookeeper.connect=<zookeeper_IP_address>:2181
        
2.  Change the IP addresses inside the StringUtils.java file:
        public static final String brokersAddr = "<server_IP_address_1>:9092,<server_IP_address_2>:9092, ...";
        public static final String httpServers = "<zookeeper_IP_address>";
        
3.  Change the IP address of utils.js file:
        var http_server_address = "<zookeeper_IP_address>"
        
4.  Open two terminal sessions and start kafka environment
        $ bin/zookeeper-server-start.sh config/zookeeper.properties
        $ bin/kafka-server-start.sh config/server.properties

5.  Execute UserService, OrderService and ShippingService microservices

6.  Open index.html and start using the FoodDeliveryApplication
