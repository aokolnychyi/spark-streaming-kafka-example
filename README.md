# Sample Spark Streaming Application with Kafka

This repository contains a sample Spark Streaming that uses Kafka as a source. 

## Local Usage

1. Start ZooKeeper

    `bin/zookeeper-server-start.sh config/zookeeper.properties`

2. Start Kafka

    `bin/kafka-server-start.sh config/server.properties`
    
3. Create a Kafka topic

    `bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 4 --topic spark-test-4-partitions`
    
4. Start Spark application

5. Send messages

    `bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic spark-test-4-partitions`