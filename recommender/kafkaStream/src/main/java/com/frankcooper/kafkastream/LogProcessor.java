package com.frankcooper.kafkastream;

import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;

/**
 * Created by FrankCooper
 * Date 2019/5/1 9:52
 * Description
 */
public class LogProcessor implements Processor<byte[], byte[]> {
    public static final String PREFIX_MSG = "abc:";
    private ProcessorContext context;


    @Override
    public void init(ProcessorContext context) {
        this.context = context;
    }

    @Override
    public void process(byte[] key, byte[] value) {
        String ratingValue = new String(value);
        if (ratingValue.contains(PREFIX_MSG)) {
            String bValue = ratingValue.split(PREFIX_MSG)[1];
            context.forward("log".getBytes(), bValue);
        }
    }


    @Override
    public void punctuate(long l) {

    }

    @Override
    public void close() {

    }
}
