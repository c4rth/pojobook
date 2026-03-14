# Benchmarks

## CopybookSerializationBenchmark

Benchmark                                                        Mode  Cnt    Score   Error  Units
CopybookSerializationBenchmark.benchmarkAnnotationSerialization  avgt       605.855          ns/op
CopybookSerializationBenchmark.benchmarkEmbeddedSerialization    avgt       229.468          ns/op
CopybookSerializationBenchmark.benchmarkJRecordSerialization     avgt       744.662          ns/op  

## CopybookDeserializationBenchmark

Benchmark                                                            Mode  Cnt     Score   Error  Units
CopybookDeserializationBenchmark.benchmarkAnnotationDeserialization  avgt        533.870          ns/op
CopybookDeserializationBenchmark.benchmarkEmbeddedDeserialization    avgt        263.474          ns/op
CopybookDeserializationBenchmark.benchmarkJRecordDeserialization     avgt       1334.085          ns/op

## CopybookRoundTripBenchmark

_Comprehensive JMH Benchmark for full round-trip (serialization + deserialization)._\
_This represents real-world usage where data is read, processed, and written._

Benchmark                                                Mode  Cnt     Score   Error  Units
CopybookRoundTripBenchmark.benchmarkAnnotationRoundTrip  avgt       1404.475          ns/op
CopybookRoundTripBenchmark.benchmarkEmbeddedRoundTrip    avgt        451.602          ns/op
CopybookRoundTripBenchmark.benchmarkJRecordRoundTrip     avgt       2162.708          ns/op

## CopybookMultiThreadedBenchmark

_Multi-threaded JMH Benchmark for testing concurrent serialization/deserialization._\
_Tests the thread-safety and performance of the library under concurrent load._\
_This benchmark uses JMH's @Group annotation to simulate realistic concurrent usage_
_where multiple threads are reading and writing data simultaneously._

Benchmark                                              Mode  Cnt         Score   Error  Units
CopybookMultiThreadedBenchmark.annotationConcurrent4  thrpt        8353921.294          ops/s
CopybookMultiThreadedBenchmark.annotationConcurrent8  thrpt        8915540.695          ops/s
CopybookMultiThreadedBenchmark.embeddedConcurrent4    thrpt       22990360.823          ops/s
CopybookMultiThreadedBenchmark.embeddedConcurrent8    thrpt       23463119.088          ops/s
CopybookMultiThreadedBenchmark.jrecordConcurrent4     thrpt        1461150.875          ops/s
CopybookMultiThreadedBenchmark.jrecordConcurrent8     thrpt        1529551.283          ops/s

