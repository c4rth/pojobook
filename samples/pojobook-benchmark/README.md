# Benchmarks

## CopybookSerializationBenchmark

Benchmark                                                        Mode  Cnt    Score   Error  Units
CopybookSerializationBenchmark.benchmarkAnnotationSerialization  avgt       615.051          ns/op
CopybookSerializationBenchmark.benchmarkStandaloneSerialization    avgt       227.256          ns/op
CopybookSerializationBenchmark.benchmarkJRecordSerialization     avgt       693.140          ns/op

## CopybookDeserializationBenchmark

Benchmark                                                            Mode  Cnt     Score   Error  Units
CopybookDeserializationBenchmark.benchmarkAnnotationDeserialization  avgt        548.003          ns/op
CopybookDeserializationBenchmark.benchmarkStandaloneDeserialization    avgt        249.459          ns/op
CopybookDeserializationBenchmark.benchmarkJRecordDeserialization     avgt       1296.732          ns/op

## CopybookRoundTripBenchmark

_Comprehensive JMH Benchmark for full round-trip (serialization + deserialization)._\
_This represents real-world usage where data is read, processed, and written._

Benchmark                                                Mode  Cnt     Score   Error  Units
CopybookRoundTripBenchmark.benchmarkAnnotationRoundTrip  avgt       1362.570          ns/op
CopybookRoundTripBenchmark.benchmarkStandaloneRoundTrip    avgt        451.301          ns/op
CopybookRoundTripBenchmark.benchmarkJRecordRoundTrip     avgt       2137.625          ns/op

## CopybookMultiThreadedBenchmark

_Multi-threaded JMH Benchmark for testing concurrent serialization/deserialization._\
_Tests the thread-safety and performance of the library under concurrent load._\
_This benchmark uses JMH's @Group annotation to simulate realistic concurrent usage_
_where multiple threads are reading and writing data simultaneously._

Benchmark                                              Mode  Cnt         Score   Error  Units
CopybookMultiThreadedBenchmark.annotationConcurrent4  thrpt        8241774.692          ops/s
CopybookMultiThreadedBenchmark.annotationConcurrent8  thrpt        9015175.045          ops/s
CopybookMultiThreadedBenchmark.standaloneConcurrent4    thrpt       23563143.671          ops/s
CopybookMultiThreadedBenchmark.standaloneConcurrent8    thrpt       23400821.717          ops/s
CopybookMultiThreadedBenchmark.jrecordConcurrent4     thrpt        1270011.281          ops/s
CopybookMultiThreadedBenchmark.jrecordConcurrent8     thrpt        1325949.832          ops/s

