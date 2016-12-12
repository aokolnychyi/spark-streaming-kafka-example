package com.aokolnychyi.spark.streaming.example

import org.apache.kafka.common.serialization.StringDeserializer

import org.apache.spark.SparkConf
import org.apache.spark.streaming.kafka010.ConsumerStrategies._
import org.apache.spark.streaming.kafka010.LocationStrategies._
import org.apache.spark.streaming.kafka010.{CanCommitOffsets, HasOffsetRanges, KafkaUtils}
import org.apache.spark.streaming.{Seconds, State, StateSpec, StreamingContext}

object MapWithStateExample {

  private val checkpointDir = "hdfs://localhost:9000/checkpoint-spark-streaming-map"

  def main(args: Array[String]): Unit = {

    val streamingContext = StreamingContext.getOrCreate(checkpointDir, createStreamingContext)

    val kafkaParams = Map[String, Object](
      "bootstrap.servers" -> "localhost:9092",
      "key.deserializer" -> classOf[StringDeserializer],
      "value.deserializer" -> classOf[StringDeserializer],
      "group.id" -> "spark_test_group",
      "auto.offset.reset" -> "latest",
      "enable.auto.commit" -> (false: java.lang.Boolean)
    )

    val topics = Array("spark-test-4-partitions")

    val stream = KafkaUtils.createDirectStream[String, String](
      streamingContext,
      PreferConsistent,
      Subscribe[String, String](topics, kafkaParams)
    )

    val wordPairs = stream.map(record => (record.value(), 1))

    val stateSpec = StateSpec.function(updateState _)
    // .initialState(RDD)
    // .numPartitions(#partitions)
    // .partitioner(partitioner)
    // .timeout(Minutes(5))
    val wordCountWithState = wordPairs.mapWithState(stateSpec)
    // Print only updates in current batch
    wordCountWithState.print()
    // Print actual states
    wordCountWithState.stateSnapshots().print()

    // Commit offsets to a special Kafka topic to ensure recovery from a failure
    stream.foreachRDD { rdd =>
      val offsetRanges = rdd.asInstanceOf[HasOffsetRanges].offsetRanges
      stream.asInstanceOf[CanCommitOffsets].commitAsync(offsetRanges)
    }

    streamingContext.start()
    streamingContext.awaitTermination()
  }

  private def createStreamingContext(): StreamingContext = {
    val sparkConfig = new SparkConf().setMaster("local[4]").setAppName("Spark Kafka Test")
    val streamingContext = new StreamingContext(sparkConfig, Seconds(5))
    streamingContext.checkpoint(checkpointDir)
    streamingContext
  }

  private def updateState(key: String, value: Option[Int], state: State[Long]): Option[(String, Long)] = {
    if (state.exists() && !state.isTimingOut()) {
      val existingState = state.get()
      val newState = existingState + value.getOrElse(0)
      state.update(newState)
      Some(key, newState)
    } else if (value.isDefined) {
      val initialValue = value.get.toLong
      state.update(initialValue)
      Some(key, initialValue)
    } else {
      None
    }
  }


}
