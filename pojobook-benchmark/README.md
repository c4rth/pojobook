# Benchmarks

## CopybookSerializationBenchmark

Benchmark                                                        Mode  Cnt     Score   Error  Units
CopybookSerializationBenchmark.benchmarkAnnotationSerialization  avgt       1135.929          ns/op
CopybookSerializationBenchmark.benchmarkEmbeddedSerialization    avgt        279.023          ns/op
CopybookSerializationBenchmark.benchmarkJRecordSerialization     avgt        764.082          ns/op

## CopybookDeserializationBenchmark

Benchmark                                                            Mode  Cnt     Score   Error  Units
CopybookDeserializationBenchmark.benchmarkAnnotationDeserialization  avgt        747.069          ns/op
CopybookDeserializationBenchmark.benchmarkEmbeddedDeserialization    avgt        372.215          ns/op
CopybookDeserializationBenchmark.benchmarkJRecordDeserialization     avgt       1326.081          ns/op

## CopybookRoundTripBenchmark

_Comprehensive JMH Benchmark for full round-trip (serialization + deserialization)._\
_This represents real-world usage where data is read, processed, and written._

Benchmark                                                Mode  Cnt     Score   Error  Units
CopybookRoundTripBenchmark.benchmarkAnnotationRoundTrip  avgt       1693.146          ns/op
CopybookRoundTripBenchmark.benchmarkEmbeddedRoundTrip    avgt        666.806          ns/op
CopybookRoundTripBenchmark.benchmarkJRecordRoundTrip     avgt       2293.805          ns/op

## CopybookMultiThreadedBenchmark

_Multi-threaded JMH Benchmark for testing concurrent serialization/deserialization._\
_Tests the thread-safety and performance of the library under concurrent load._\
_This benchmark uses JMH's @Group annotation to simulate realistic concurrent usage_
_where multiple threads are reading and writing data simultaneously._

Benchmark                                              Mode  Cnt         Score   Error  Units
CopybookMultiThreadedBenchmark.annotationConcurrent4  thrpt        6944255.230          ops/s
CopybookMultiThreadedBenchmark.annotationConcurrent8  thrpt        6435440.799          ops/s
CopybookMultiThreadedBenchmark.embeddedConcurrent4    thrpt       17494296.957          ops/s
CopybookMultiThreadedBenchmark.embeddedConcurrent8    thrpt       17769709.572          ops/s
CopybookMultiThreadedBenchmark.jrecordConcurrent4     thrpt        1299099.559          ops/s
CopybookMultiThreadedBenchmark.jrecordConcurrent8     thrpt        1363603.615          ops/s

