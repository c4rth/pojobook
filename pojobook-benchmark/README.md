# Benchmarks

## CopybookSerializationBenchmark

Benchmark                                                        Mode  Cnt     Score   Error  Units
CopybookSerializationBenchmark.benchmarkAnnotationSerialization  avgt       1067.665          ns/op
CopybookSerializationBenchmark.benchmarkEmbeddedSerialization    avgt        236.224          ns/op
CopybookSerializationBenchmark.benchmarkJRecordSerialization     avgt        758.623          ns/op

## CopybookDeserializationBenchmark

Benchmark                                                            Mode  Cnt     Score   Error  Units
CopybookDeserializationBenchmark.benchmarkAnnotationDeserialization  avgt        561.770          ns/op
CopybookDeserializationBenchmark.benchmarkEmbeddedDeserialization    avgt        273.292          ns/op
CopybookDeserializationBenchmark.benchmarkJRecordDeserialization     avgt       1355.413          ns/op

## CopybookRoundTripBenchmark

_Comprehensive JMH Benchmark for full round-trip (serialization + deserialization)._\
_This represents real-world usage where data is read, processed, and written._

Benchmark                                                Mode  Cnt     Score   Error  Units
CopybookRoundTripBenchmark.benchmarkAnnotationRoundTrip  avgt       1562.844          ns/op
CopybookRoundTripBenchmark.benchmarkEmbeddedRoundTrip    avgt        462.172          ns/op
CopybookRoundTripBenchmark.benchmarkJRecordRoundTrip     avgt       2212.013          ns/op

## CopybookMultiThreadedBenchmark

_Multi-threaded JMH Benchmark for testing concurrent serialization/deserialization._\
_Tests the thread-safety and performance of the library under concurrent load._\
_This benchmark uses JMH's @Group annotation to simulate realistic concurrent usage_
_where multiple threads are reading and writing data simultaneously._

Benchmark                                              Mode  Cnt         Score   Error  Units
CopybookMultiThreadedBenchmark.annotationConcurrent4  thrpt        7870728.645          ops/s
CopybookMultiThreadedBenchmark.annotationConcurrent8  thrpt        6713087.067          ops/s
CopybookMultiThreadedBenchmark.embeddedConcurrent4    thrpt       22759925.806          ops/s
CopybookMultiThreadedBenchmark.embeddedConcurrent8    thrpt       23282396.385          ops/s
CopybookMultiThreadedBenchmark.jrecordConcurrent4     thrpt        1521068.362          ops/s
CopybookMultiThreadedBenchmark.jrecordConcurrent8     thrpt        1340780.319          ops/s

