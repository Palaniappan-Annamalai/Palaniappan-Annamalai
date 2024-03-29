

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.MetricGroup;
import org.apache.flink.runtime.state.StateBackend;
import org.apache.flink.runtime.state.filesystem.FsStateBackend;
import org.apache.flink.runtime.state.memory.MemoryStateBackend;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.operators.StreamingRuntimeContext;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;


import org.apache.flink.util.SerializedValue;
import org.apache.kafka.clients.consumer.ConsumerConfig;

import java.util.Collection;
import java.util.Map;
import java.util.Properties;

public class KafkaFlink {

    public static void main(String[] args) throws Exception {


        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        env.setRestartStrategy(RestartStrategies.fixedDelayRestart(10,5000));
        env.enableCheckpointing(1000);

        env.setStateBackend(new FsStateBackend("file:/D:/Flink/flink-1.9.0/flink-1.9.0/FsCheckpoint"));





        Properties properties = new Properties();
        properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,"localhost:9092");
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG,"flink-group-1");
        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,"earliest");

        DataStreamSource<String> dataStreamSource= env.addSource(

                new FlinkKafkaConsumer<>("flink-topic",new SimpleStringSchema(),properties)
        );


        dataStreamSource.map(new Convert())
                        .map(new Mapping())
                                .keyBy(0)
                                        .map(new MyMap())
                                                .print();



        env.execute();

    }

    private static class Convert implements MapFunction<String,Integer> {

        @Override
        public Integer map(String value) throws Exception {
            return Integer.parseInt(value);
        }
    }

    private static class Mapping implements MapFunction<Integer, Tuple2<Integer,Integer>>{

        @Override
        public Tuple2<Integer, Integer> map(Integer value) throws Exception {
            return new Tuple2<>(value,1);
        }
    }

    private static class MyMap extends RichMapFunction<Tuple2<Integer,Integer>,Tuple2<Integer,Integer>> {

        private transient ValueState<Tuple2<Integer,Integer>> valueState;


        @Override
        public void open(Configuration parameters) throws Exception {
            ValueStateDescriptor<Tuple2<Integer,Integer>> descriptor =
                    new ValueStateDescriptor<>("my-state", TypeInformation.of(new TypeHint<Tuple2<Integer,Integer>>() {
                    }));

            valueState = getRuntimeContext().getState(descriptor);
        }

        @Override
        public Tuple2<Integer, Integer> map(Tuple2<Integer, Integer> value) throws Exception {
            Tuple2<Integer,Integer> tuple2 = valueState.value();
            if(tuple2 == null){
                tuple2 = new Tuple2<>(value.f0,1);
                valueState.update(tuple2);
            }
            else {
                tuple2.f1 = valueState.value().f1 + value.f1;
                tuple2.f0 = value.f0;
                valueState.update(tuple2);
            }
            return tuple2;
        }
    }



}
