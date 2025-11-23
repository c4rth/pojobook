# Benchmarks

## CopybookSerializationBenchmark

| Benchmark                                                       | Mode | Cnt |    Score | Error | Units |
|-----------------------------------------------------------------|------|----:|---------:|-------|-------|
| CopybookSerializationBenchmark.benchmarkAnnotationSerialization | avgt |     | 1044.860 |       | ns/op |
| CopybookSerializationBenchmark.benchmarkEmbeddedSerialization   | avgt |     |  545.762 |       | ns/op |
| CopybookSerializationBenchmark.benchmarkJRecordSerialization    | avgt |     |  693.080 |       | ns/op |

## CopybookDeserializationBenchmark

| Benchmark                                                           | Mode | Cnt |    Score | Error | Units |
|---------------------------------------------------------------------|------|----:|---------:|-------|-------|
| CopybookDeserializationBenchmark.benchmarkAnnotationDeserialization | avgt |     |  711.572 |       | ns/op |
| CopybookDeserializationBenchmark.benchmarkEmbeddedDeserialization   | avgt |     |  357.027 |       | ns/op |
| CopybookDeserializationBenchmark.benchmarkJRecordDeserialization    | avgt |     | 1379.266 |       | ns/op |

## CopybookRoundTripBenchmark

_Comprehensive JMH Benchmark for full round-trip (serialization + deserialization)._\
_This represents real-world usage where data is read, processed, and written._

| Benchmark                                               | Mode | Cnt |    Score | Error | Units |
|---------------------------------------------------------|------|----:|---------:|------:|-------|
| CopybookRoundTripBenchmark.benchmarkAnnotationRoundTrip | avgt |     | 1656.901 |       | ns/op |
| CopybookRoundTripBenchmark.benchmarkEmbeddedRoundTrip   | avgt |     |  810.038 |       | ns/op |
| CopybookRoundTripBenchmark.benchmarkJRecordRoundTrip    | avgt |     | 2143.425 |       | ns/op |

## CopybookMultiThreadedBenchmark

_Multi-threaded JMH Benchmark for testing concurrent serialization/deserialization._\
_Tests the thread-safety and performance of the library under concurrent load._\
_This benchmark uses JMH's @Group annotation to simulate realistic concurrent usage_
_where multiple threads are reading and writing data simultaneously._

| Benchmark                                            | Mode  | Cnt |        Score | Error | Units |
|------------------------------------------------------|-------|----:|-------------:|-------|-------|
| CopybookMultiThreadedBenchmark.annotationConcurrent4 | thrpt |     |  6652125.078 |       | ops/s |
| CopybookMultiThreadedBenchmark.annotationConcurrent8 | thrpt |     |  6887373.922 |       | ops/s |
| CopybookMultiThreadedBenchmark.embeddedConcurrent4   | thrpt |     | 14828988.056 |       | ops/s |
| CopybookMultiThreadedBenchmark.embeddedConcurrent8   | thrpt |     | 14979986.394 |       | ops/s |
| CopybookMultiThreadedBenchmark.jrecordConcurrent4    | thrpt |     |  1674156.411 |       | ops/s |
| CopybookMultiThreadedBenchmark.jrecordConcurrent8    | thrpt |     |  1252425.322 |       | ops/s |

