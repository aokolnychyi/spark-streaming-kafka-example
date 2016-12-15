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
    
## Key Points

1. One cannot define multiple computations on one stream since receivers (1 per each stream) cannot
 be accessed concurrently.

2. The `reduceByWindow` method will output one element per each sliding window. An RDD will be 
generated after the `slidingInterval` and will contain one element that represents the reduced 
value over the `windowDuration`. 

3. The `reduceByKeyAndWindow` method provides similar behaviour as `reduceByKey` but over a sliding window.

4. Both `reduceByWindow` and `reduceByKeyAndWindow` have overloaded methods that accept inverse functions. 
These overloaded versions should be used whenever possible since they are more efficient. The reduction
happens incrementally using the old window's reduced value. First, Spark Streaming reduces the new 
values that entered the window. Second, Spark "inverse reduce" the old values that left the window.

5. Stateful aggregations (e.g. updateStateByKey, reduceByKeyAndWindow with inverse functions) require
periodical RDD data checkpointing. Some methods (e.g. countByWindow) might call those ones under the hood.   

6. Use StreamingContext.getOrCreate(...) whenever checkpointing is enabled.

7. Commit offsets to a special Kafka topic to ensure recovery from a failure.

8. Use the `transform` operation to do RDD-to-RDD transformations.

9. Use `mapWithState` instead of `updateStateByKey`. The latter has performance proportional to the size
of the state, while the former has performance that is proportional to the size of the batch. Moreover,
`mapWithState` allows one to read its initial state as an RDD, set the number of partitions (useful when
you can estimate the size of the state), partitioner (hash by default), timeout (keys whose values are 
not updated within the specified timeout will be removed from the state).

10. Consider parallelizing the data receiving. Each input DStream creates a single receiver (running
 on a worker machine) that receives a single stream of data. Receiving multiple data streams can 
 therefore be achieved by creating multiple input DStreams and configuring them to receive different 
 partitions of the data stream from the source(s).
 
11. If you assign the same group id to several consumer instances then all the consumers will get 
different set of messages on the same topic. This is a kind of load balancing which kafka provides 
with its Consumer API.

12. If you're doing multiple output operations and aren't caching, Spark is going to read from Kafka 
again each time, and if some of those reads are happening for the same group and same topicpartition, 
it's not going to work.

13. Output operations by default ensure at-least once semantics because it depends on the type of 
output operation (idempotent, or not) and the semantics of the downstream system (supports transactions or not). 
But users can implement their own transaction mechanisms to achieve exactly-once semantics.

14. Checkpointing of RDDs incurs the cost of saving to reliable storage. This may cause an increase 
in the processing time of those batches where RDDs get checkpointed. Hence, the interval of 
checkpointing needs to be set carefully. At small batch sizes (say 1 second), checkpointing every 
batch may significantly reduce operation throughput. Conversely, checkpointing too infrequently causes
the lineage and task sizes to grow, which may have detrimental effects. For stateful transformations 
that require RDD checkpointing, the default interval is a multiple of the batch interval that is at 
least 10 seconds. It can be set by using dstream.checkpoint(checkpointInterval). Typically, a checkpoint 
interval of 5 - 10 sliding intervals of a DStream is a good setting to try.

15. Good throughput is an extremely important point. Spark creates one receiver on one executor for each DStream based
 on a receiver-based data source (e.g. socket). As a result, the data is stored on one executor and
 all processing happens there since Spark tries to achieve data locality. As a result, the cluster is 
  not utilized properly. There are two options so solve the problem. First, you can manually 
  repartition your data so that it gets evenly distributed across all nodes. Second, you can increase
  the number of receivers, which is a better option. Then you can just union those DStreams. In the 
  latest version of Kafka, there are no Spark receivers. In this case, set the appropriate number of partitions
  in Kafka. Each Kafka partition is mapped to a Spark partition. More partitions in Kafka == more 
  paralellism. Spark uses Kafka to pull the data whenever is required.
  
16. Specify the number of partitions for each operation that involves shuffling the data if you are 
not happy with the default value.
