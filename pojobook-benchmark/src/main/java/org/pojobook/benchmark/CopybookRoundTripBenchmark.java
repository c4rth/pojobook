package org.pojobook.benchmark;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.pojobook.PojoBook;
import org.pojobook.benchmark.codegen.CopybookConverter;
import org.pojobook.benchmark.codegen.cobol.LineSampleCbkPojo;

import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive JMH Benchmark for full round-trip (serialization + deserialization).
 * This represents real-world usage where data is read, processed, and written.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, warmups = 1)
@Warmup(iterations = 1, time = 2)
@Measurement(iterations = 1, time = 2)
public class CopybookRoundTripBenchmark {

    private static final Charset CHARSET = Charset.forName("CP1047");

    private byte[] copybookData;
    private PojoBook pojoBook;

    private CopybookConverter copybookConverter;

    @Setup
    public void setup() throws Exception {
        // Initialize POJOBook
        pojoBook = new PojoBook();
        // Initialize CopybookConverter
        copybookConverter = new CopybookConverter();
        //
        copybookData = createTestData();
    }

    private byte[] createTestData() {
        return new byte[]{
                (byte) 0xE3, (byte) 0xC1, (byte) 0xC3, (byte) 0xE2, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0xD9,
                (byte) 0xD2, (byte) 0xC8, (byte) 0xC3, (byte) 0xF9, (byte) 0xF2, (byte) 0xF0, (byte) 0xC3, (byte) 0xD7, (byte) 0xC3,
                (byte) 0xD6, (byte) 0xD5, (byte) 0xE2, (byte) 0xE4, (byte) 0xD3, (byte) 0xE3, (byte) 0x40, (byte) 0xD5, (byte) 0xF0,
                (byte) 0xF0, (byte) 0xF0, (byte) 0xF0, (byte) 0xF2, (byte) 0xF1, (byte) 0xF4, (byte) 0xF5, (byte) 0xF0, (byte) 0xF6,
                (byte) 0xF3, (byte) 0xF2, (byte) 0xF0, (byte) 0xF2, (byte) 0xF5, (byte) 0xF0, (byte) 0xF3, (byte) 0xF2, (byte) 0xF1,
                (byte) 0xE3, (byte) 0x85, (byte) 0xA2, (byte) 0xA3, (byte) 0x40, (byte) 0xD3, (byte) 0x96, (byte) 0x87, (byte) 0x96,
                (byte) 0x40, (byte) 0x51, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0xE2,
                (byte) 0xA3, (byte) 0x81, (byte) 0x95, (byte) 0x84, (byte) 0x81, (byte) 0x99, (byte) 0x84, (byte) 0x40, (byte) 0x84,
                (byte) 0x85, (byte) 0x40, (byte) 0xD3, (byte) 0x89, (byte) 0x54, (byte) 0x87, (byte) 0x85, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0xC3, (byte) 0xC6,
                (byte) 0xF1, (byte) 0xF1, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0xE2, (byte) 0xF0, (byte) 0x4B, (byte) 0xF6, (byte) 0xF0, (byte) 0xF9, (byte) 0xF9, (byte) 0xF2,
                (byte) 0xF9, (byte) 0x6B, (byte) 0xF5, (byte) 0x4B, (byte) 0xF5, (byte) 0xF4, (byte) 0xF2, (byte) 0xF6, (byte) 0xF8,
                (byte) 0xF2, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0xD9, (byte) 0xA4, (byte) 0x85, (byte) 0x40, (byte) 0x84, (byte) 0x85, (byte) 0x40, (byte) 0x93, (byte) 0x81,
                (byte) 0x40, (byte) 0xC3, (byte) 0x85, (byte) 0x95, (byte) 0xA3, (byte) 0x99, (byte) 0x81, (byte) 0x93, (byte) 0x85,
                (byte) 0x40, (byte) 0xF2, (byte) 0x6B, (byte) 0x40, (byte) 0xF4, (byte) 0xF0, (byte) 0xF0, (byte) 0xF0, (byte) 0x40,
                (byte) 0xD3, (byte) 0x89, (byte) 0x54, (byte) 0x87, (byte) 0x85, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0xA3,
                (byte) 0x85, (byte) 0xA2, (byte) 0xA3, (byte) 0x40, (byte) 0x82, (byte) 0xA8, (byte) 0x40, (byte) 0xC6, (byte) 0xE7,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,
                (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40
        };
    }

    @Benchmark
    public byte[] benchmarkEmbeddedRoundTrip() throws Exception {
        // Deserialize
        org.pojobook.samples.embedded.SampleCbk pojo =
                org.pojobook.samples.embedded.SampleCbk.deserialize(copybookData, CHARSET);
        // Serialize
        return pojo.serialize(CHARSET);
    }

    @Benchmark
    public byte[] benchmarkAnnotationRoundTrip() throws Exception {
        // Deserialize
        org.pojobook.samples.annotation.SampleCbk pojo =
                pojoBook.deserialize(copybookData, org.pojobook.samples.annotation.SampleCbk.class, CHARSET);
        // Serialize
        return pojoBook.serialize(pojo, CHARSET);
    }

    @Benchmark
    public byte[] benchmarkJRecordRoundTrip() throws Exception {
        // Deserialize
        LineSampleCbkPojo pojo = copybookConverter.convertToCopybookModel(copybookData);
        // Serialize
        return copybookConverter.convertToCopybookData(pojo);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(CopybookRoundTripBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}

