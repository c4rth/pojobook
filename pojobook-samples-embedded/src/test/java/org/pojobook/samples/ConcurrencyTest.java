package org.pojobook.samples;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.pojobook.samples.generated.BankingTransaction;
import org.pojobook.samples.generated.CustomerRecord;
import org.pojobook.samples.generated.EmployeeRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Multi-hreaded concurrency tests to validate thread-safety of Pojo
 * serialization and deserialization operations.
 * <p>
 * These tests ensure that:
 * - Multiple threads can serialize and deserialize simultaneously
 * - No data corruption occurs under concurrent access
 * - Thread-local state is properly isolated
 * - Performance remains consistent under load
 */
class ConcurrencyTest {

    private final Logger log = LoggerFactory.getLogger(ConcurrencyTest.class);

    private static final int NUM_THREADS = 10;
    private static final int OPERATIONS_PER_THREAD = 100;

    @Test
    void testConcurrentSerializationAndDeserialization() throws Exception {
        AtomicInteger successCount;
        AtomicInteger errorCount;
        List<Throwable> errors;
        boolean completed;
        boolean termination;
        try (ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS)) {
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(NUM_THREADS);
            successCount = new AtomicInteger(0);
            errorCount = new AtomicInteger(0);
            errors = new CopyOnWriteArrayList<>();

            // Submit tasks for multiple threads
            for (int threadId = 0; threadId < NUM_THREADS; threadId++) {
                final int id = threadId;
                executor.submit(() -> {
                    try {
                        // Wait for all threads to be ready
                        startLatch.await();

                        // Perform multiple serialization/deserialization operations
                        for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
                            // Create unique customer for this operation
                            CustomerRecord original = createCustomer(id * OPERATIONS_PER_THREAD + i);

                            // Serialize
                            byte[] data = original.serialize(Charset.forName("CP1047"));

                            // Deserialize
                            CustomerRecord deserialized = CustomerRecord.deserialize(data, Charset.forName("CP1047"));

                            // Validate
                            assertEquals(original.getCustomerId(), deserialized.getCustomerId());
                            assertEquals(original.getCustomerName().trim(), deserialized.getCustomerName().trim());
                            assertEquals(original.getCustomerAge(), deserialized.getCustomerAge());
                            assertEquals(original.getCustomerBalance(), deserialized.getCustomerBalance());
                            assertEquals(original.getCustomerStatus().trim(), deserialized.getCustomerStatus().trim());

                            successCount.incrementAndGet();
                        }
                    } catch (Throwable t) {
                        errorCount.incrementAndGet();
                        errors.add(t);
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            // Start all threads simultaneously
            startLatch.countDown();

            // Wait for all threads to complete
            completed = endLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();
            termination = executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        // Verify results
        assertTrue(completed, "All threads should complete within timeout");
        assertTrue(termination, "All threads should be terminated");
        assertEquals(0, errorCount.get(), "No errors should occur. Errors: " + errors);
        assertEquals(NUM_THREADS * OPERATIONS_PER_THREAD, successCount.get(),
                "All operations should succeed");
    }

    @Test
    void testConcurrentBankingTransactions() throws Exception {
        AtomicInteger successCount;
        List<Throwable> errors;
        boolean completed;
        boolean termination;
        try (ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS)) {
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(NUM_THREADS);
            successCount = new AtomicInteger(0);
            errors = new CopyOnWriteArrayList<>();

            String[] transactionTypes = {"D", "W", "T", "P"};

            for (int threadId = 0; threadId < NUM_THREADS; threadId++) {
                final int id = threadId;
                executor.submit(() -> {
                    try {
                        startLatch.await();

                        for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
                            String txType = transactionTypes[i % transactionTypes.length];
                            BankingTransaction original = createBankingTransaction(id * OPERATIONS_PER_THREAD + i, txType);

                            byte[] data = original.serialize(StandardCharsets.ISO_8859_1);
                            BankingTransaction deserialized = BankingTransaction.deserialize(data, StandardCharsets.ISO_8859_1);

                            assertEquals(original.getTransactionId(), deserialized.getTransactionId());
                            assertEquals(original.getAccountNumber(), deserialized.getAccountNumber());
                            assertEquals(original.getTransactionType().trim(), deserialized.getTransactionType().trim());
                            assertEquals(original.getTransactionDate(), deserialized.getTransactionDate());
                            assertEquals(original.getStatus().trim(), deserialized.getStatus().trim());

                            successCount.incrementAndGet();
                        }
                    } catch (Throwable t) {
                        errors.add(t);
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            completed = endLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();
            termination = executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertTrue(completed, "All threads should complete within timeout");
        assertTrue(termination, "All threads should be terminated");
        assertTrue(errors.isEmpty(), "No errors should occur. Errors: " + errors);
        assertEquals(NUM_THREADS * OPERATIONS_PER_THREAD, successCount.get());
    }

    @Test
    void testConcurrentMixedRecordTypes() throws Exception {
        AtomicInteger successCount;
        List<Throwable> errors;
        boolean completed;
        boolean termination;
        try (ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS)) {
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(NUM_THREADS);
            successCount = new AtomicInteger(0);
            errors = new CopyOnWriteArrayList<>();

            // Each thread processes different record types
            for (int threadId = 0; threadId < NUM_THREADS; threadId++) {
                final int id = threadId;
                executor.submit(() -> {
                    try {
                        startLatch.await();

                        for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
                            // Alternate between different record types
                            if (i % 3 == 0) {
                                testCustomerRecord(id * OPERATIONS_PER_THREAD + i);
                            } else if (i % 3 == 1) {
                                testBankingTransactionRecord(id * OPERATIONS_PER_THREAD + i);
                            } else {
                                testEmployeeRecord(id * OPERATIONS_PER_THREAD + i);
                            }
                            successCount.incrementAndGet();
                        }
                    } catch (Throwable t) {
                        errors.add(t);
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            completed = endLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();
            termination = executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertTrue(completed, "All threads should complete within timeout");
        assertTrue(termination, "All threads should be terminated");
        assertTrue(errors.isEmpty(), "No errors should occur. Errors: " + errors);
        assertEquals(NUM_THREADS * OPERATIONS_PER_THREAD, successCount.get());
    }

    @RepeatedTest(5)
    void testConcurrentSerializationConsistency() throws Exception {
        // Create a fixed customer that all threads will serialize
        CustomerRecord template = createCustomer(42);

        List<byte[]> results;
        boolean termination;
        try (ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS)) {
            CountDownLatch startLatch = new CountDownLatch(1);
            List<Future<byte[]>> futures = new ArrayList<>();

            // All threads serialize the same object
            for (int i = 0; i < NUM_THREADS; i++) {
                futures.add(executor.submit(() -> {
                    startLatch.await();
                    return template.serialize(Charset.forName("CP1047"));
                }));
            }

            startLatch.countDown();

            // Collect all results
            results = new ArrayList<>();
            for (Future<byte[]> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }

            executor.shutdown();
            termination = executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertTrue(termination, "All threads should be terminated");

        // All serialized bytes should be identical
        byte[] reference = results.getFirst();
        for (int i = 1; i < results.size(); i++) {
            assertArrayEquals(reference, results.get(i),
                    "Serialization of the same object should produce identical results in thread " + i);
        }
    }

    @Test
    void testHighContentionScenario() throws Exception {
        // Stress test with more threads and operations
        final int HIGH_THREAD_COUNT = 50;
        final int HIGH_OPS_COUNT = 50;

        AtomicInteger successCount;
        List<Throwable> errors;
        boolean completed;
        boolean termination;
        long duration;
        try (ExecutorService executor = Executors.newFixedThreadPool(HIGH_THREAD_COUNT)) {
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(HIGH_THREAD_COUNT);
            successCount = new AtomicInteger(0);
            errors = new CopyOnWriteArrayList<>();

            long startTime = System.currentTimeMillis();

            for (int threadId = 0; threadId < HIGH_THREAD_COUNT; threadId++) {
                final int id = threadId;
                executor.submit(() -> {
                    try {
                        startLatch.await();

                        for (int i = 0; i < HIGH_OPS_COUNT; i++) {
                            CustomerRecord original = createCustomer(id * HIGH_OPS_COUNT + i);
                            byte[] data = original.serialize(Charset.forName("CP1047"));
                            CustomerRecord deserialized = CustomerRecord.deserialize(data, Charset.forName("CP1047"));

                            assertEquals(original.getCustomerId(), deserialized.getCustomerId());
                            successCount.incrementAndGet();
                        }
                    } catch (Throwable t) {
                        errors.add(t);
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            completed = endLatch.await(60, TimeUnit.SECONDS);
            duration = System.currentTimeMillis() - startTime;

            executor.shutdown();
            termination = executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertTrue(completed, "All threads should complete within timeout");
        assertTrue(termination, "All threads should be terminated");
        assertTrue(errors.isEmpty(), "No errors should occur under high contention. Errors: " + errors);
        assertEquals(HIGH_THREAD_COUNT * HIGH_OPS_COUNT, successCount.get());

        // Performance assertion - should complete in reasonable time
        assertTrue(duration < 60000, "High contention test should complete in under 60 seconds. Took: " + duration + "ms");

        double opsPerSecond = (HIGH_THREAD_COUNT * HIGH_OPS_COUNT * 1000.0) / duration;
        log.info("High contention performance: {} ops/sec, duration: {}ms", String.format("%.2f", opsPerSecond), duration);
    }

    @Test
    void testConcurrentDeserializationOfSameData() throws Exception {
        // Serialize once
        CustomerRecord original = createCustomer(999);
        byte[] sharedData = original.serialize(Charset.forName("CP1047"));

        AtomicInteger successCount;
        List<Throwable> errors;
        boolean completed;
        boolean termination;
        try (ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS)) {
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(NUM_THREADS);
            successCount = new AtomicInteger(0);
            errors = new CopyOnWriteArrayList<>();

            // Multiple threads deserialize the same byte array
            for (int i = 0; i < NUM_THREADS; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();

                        for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                            CustomerRecord deserialized = CustomerRecord.deserialize(sharedData, Charset.forName("CP1047"));

                            assertEquals(original.getCustomerId(), deserialized.getCustomerId());
                            assertEquals(original.getCustomerName().trim(), deserialized.getCustomerName().trim());
                            assertEquals(original.getCustomerAge(), deserialized.getCustomerAge());

                            successCount.incrementAndGet();
                        }
                    } catch (Throwable t) {
                        errors.add(t);
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            completed = endLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();
            termination = executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertTrue(completed, "All threads should complete within timeout");
        assertTrue(termination, "All threads should be terminated");
        assertTrue(errors.isEmpty(), "No errors should occur. Errors: " + errors);
        assertEquals(NUM_THREADS * OPERATIONS_PER_THREAD, successCount.get());
    }

    @Test
    void testConcurrentWithDifferentCharsets() throws Exception {
        AtomicInteger successCount;
        List<Throwable> errors;
        boolean completed;
        boolean termination;
        try (ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS)) {
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(NUM_THREADS);
            successCount = new AtomicInteger(0);
            errors = new CopyOnWriteArrayList<>();

            Charset[] charsets = {
                    Charset.forName("CP1047"),
                    StandardCharsets.ISO_8859_1,
                    StandardCharsets.UTF_8
            };

            for (int threadId = 0; threadId < NUM_THREADS; threadId++) {
                final int id = threadId;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        Charset charset = charsets[id % charsets.length];

                        for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
                            BankingTransaction original = createBankingTransaction(id * OPERATIONS_PER_THREAD + i, "D");

                            byte[] data = original.serialize(charset);
                            BankingTransaction deserialized = BankingTransaction.deserialize(data, charset);

                            assertEquals(original.getTransactionId(), deserialized.getTransactionId());
                            assertEquals(original.getAccountNumber(), deserialized.getAccountNumber());

                            successCount.incrementAndGet();
                        }
                    } catch (Throwable t) {
                        errors.add(t);
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            completed = endLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();
            termination = executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertTrue(completed, "All threads should complete within timeout");
        assertTrue(termination, "All threads should be terminated");
        assertTrue(errors.isEmpty(), "No errors should occur with different charsets. Errors: " + errors);
        assertEquals(NUM_THREADS * OPERATIONS_PER_THREAD, successCount.get());
    }

    // Helper methods
    private CustomerRecord createCustomer(int id) {
        CustomerRecord customer = new CustomerRecord();
        customer.setCustomerId(10000 + id);
        customer.setCustomerName("CUSTOMER_" + String.format("%05d", id));
        customer.setCustomerAge(20 + (id % 60));
        customer.setCustomerBalance(new BigDecimal(1000 + id * 10));
        customer.setCustomerStatus("A");
        return customer;
    }

    private BankingTransaction createBankingTransaction(int id, String type) {
        BankingTransaction txn = new BankingTransaction();
        txn.setTransactionId(241101000L + id);
        txn.setAccountNumber(123456789L + id);
        txn.setTransactionType(type);
        txn.setTransactionDate(20241101);
        txn.setTransactionTime(120000 + (id * 100));
        txn.setAmount(new BigDecimal(100 + (id * 10)));
        txn.setBalanceBefore(new BigDecimal(5000 + (id * 100)));
        txn.setBalanceAfter(new BigDecimal(5100 + (id * 110)));
        txn.setStatus("C");
        txn.setDescription("Transaction " + id);
        txn.setBranchCode("BR" + String.format("%03d", id % 100));
        txn.setTellerId("T" + String.format("%05d", id % 1000));
        txn.setAuthorizationCode("AUTH" + id);
        return txn;
    }

    private EmployeeRecord createEmployeeRecord(int id) {
        EmployeeRecord employee = new EmployeeRecord();
        employee.setEmployeeId(1000 + id);
        employee.setFirstName("FIRST_" + id);
        employee.setMiddleInitial("M");
        employee.setLastName("LAST_" + id);
        employee.setDepartment("DEPT" + (id % 10));
        employee.setJobTitle("ENGINEER");
        employee.setSalary(new BigDecimal(50000 + (id * 100)));
        employee.setHireDate(20200101 + id);
        employee.setDateOfBirth(19900101 + id);
        return employee;
    }

    private void testCustomerRecord(int id) throws Exception {
        CustomerRecord original = createCustomer(id);
        byte[] data = original.serialize(Charset.forName("CP1047"));
        CustomerRecord deserialized = CustomerRecord.deserialize(data, Charset.forName("CP1047"));
        assertEquals(original.getCustomerId(), deserialized.getCustomerId());
    }

    private void testBankingTransactionRecord(int id) throws Exception {
        BankingTransaction original = createBankingTransaction(id, "D");
        byte[] data = original.serialize(StandardCharsets.ISO_8859_1);
        BankingTransaction deserialized = BankingTransaction.deserialize(data, StandardCharsets.ISO_8859_1);
        assertEquals(original.getTransactionId(), deserialized.getTransactionId());
    }

    private void testEmployeeRecord(int id) throws Exception {
        EmployeeRecord original = createEmployeeRecord(id);
        byte[] data = original.serialize(StandardCharsets.ISO_8859_1);
        EmployeeRecord deserialized = EmployeeRecord.deserialize(data, StandardCharsets.ISO_8859_1);
        assertEquals(original.getEmployeeId(), deserialized.getEmployeeId());
    }
}

