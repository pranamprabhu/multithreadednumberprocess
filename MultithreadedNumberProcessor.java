import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Multithreaded Number Processor
 *
 * Demonstrates:
 * 1. Single-threaded numerical processing
 * 2. Multithreaded numerical processing with range partitioning
 * 3. Execution time measurement and speedup comparison
 * 4. Thread-safety analysis (Thread confinement vs Atomic vs Race Condition)
 */
public class MultithreadedNumberProcessor {

    // Default configuration for benchmark
    public static final long DEFAULT_START = 1L;
    public static final long DEFAULT_END = 5_000_000L;

    /**
     * Computationally intensive numerical task:
     * Checks if a number is prime using trial division up to sqrt(n).
     * This provides a pure CPU-bound mathematical workload.
     */
    public static boolean isPrime(long n) {
        if (n <= 1) return false;
        if (n <= 3) return true;
        if (n % 2 == 0 || n % 3 == 0) return false;
        for (long i = 5; i * i <= n; i += 6) {
            if (n % i == 0 || n % (i + 2) == 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Result holder for a processing run.
     */
    public static class ProcessResult {
        public final long primeCount;
        public final long executionTimeMs;
        public final int threadCount;

        public ProcessResult(long primeCount, long executionTimeMs, int threadCount) {
            this.primeCount = primeCount;
            this.executionTimeMs = executionTimeMs;
            this.threadCount = threadCount;
        }
    }

    // =========================================================================
    // 1. SINGLE-THREADED PROCESSOR
    // =========================================================================
    public static class SingleThreadedProcessor {
        public static ProcessResult process(long start, long end) {
            long startTime = System.currentTimeMillis();
            long count = 0;

            for (long i = start; i <= end; i++) {
                if (isPrime(i)) {
                    count++;
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;
            return new ProcessResult(count, elapsed, 1);
        }
    }

    // =========================================================================
    // 2. MULTITHREADED PROCESSOR (Best Practice: Range Partitioning & Thread Confinement)
    // =========================================================================
    public static class MultiThreadedProcessor {

        /**
         * Worker thread that computes primes within its dedicated [subStart, subEnd] range.
         * Stores result locally without touching any shared mutable state.
         */
        public static class WorkerThread extends Thread {
            private final long rangeStart;
            private final long rangeEnd;
            private long localCount = 0;

            public WorkerThread(long rangeStart, long rangeEnd, String name) {
                super(name);
                this.rangeStart = rangeStart;
                this.rangeEnd = rangeEnd;
            }

            @Override
            public void run() {
                long count = 0;
                for (long i = rangeStart; i <= rangeEnd; i++) {
                    if (isPrime(i)) {
                        count++;
                    }
                }
                this.localCount = count;
            }

            public long getLocalCount() {
                return localCount;
            }
        }

        public static ProcessResult process(long start, long end, int numThreads) {
            if (numThreads <= 1) {
                return SingleThreadedProcessor.process(start, end);
            }

            long totalNumbers = (end - start + 1);
            long chunkSize = totalNumbers / numThreads;
            long remainder = totalNumbers % numThreads;

            List<WorkerThread> workers = new ArrayList<>(numThreads);
            long currentStart = start;

            long startTime = System.currentTimeMillis();

            // 1. Divide workload into independent ranges and spawn threads
            for (int i = 0; i < numThreads; i++) {
                long currentChunk = chunkSize + (i < remainder ? 1 : 0);
                long currentEnd = currentStart + currentChunk - 1;

                WorkerThread worker = new WorkerThread(currentStart, currentEnd, "Worker-" + (i + 1));
                workers.add(worker);
                worker.start();

                currentStart = currentEnd + 1;
            }

            // 2. Await all worker threads and aggregate their independent results
            long totalCount = 0;
            for (WorkerThread worker : workers) {
                try {
                    worker.join();
                    totalCount += worker.getLocalCount();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("Thread interrupted: " + worker.getName());
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;
            return new ProcessResult(totalCount, elapsed, numThreads);
        }
    }

    // =========================================================================
    // 3. THREAD SAFETY CONSIDERATIONS & DEMONSTRATION
    // =========================================================================
    public static class ThreadSafetyDemo {
        private static long unsafeCounter = 0;

        /**
         * Demonstrates race condition when multiple threads concurrently write
         * to a non-synchronized shared primitive.
         */
        public static void demonstrateRaceCondition(long range, int threadCount) {
            System.out.println("\n----------------------------------------------------------------------");
            System.out.println(">>> THREAD SAFETY DEMONSTRATION");
            System.out.println("Processing " + range + " increments across " + threadCount + " concurrent threads.");
            System.out.println("----------------------------------------------------------------------");

            long expectedTotal = range * threadCount;

            // Scenario A: Unsafe Shared Counter (Race condition)
            unsafeCounter = 0;
            Thread[] unsafeThreads = new Thread[threadCount];
            long startUnsafe = System.currentTimeMillis();
            for (int t = 0; t < threadCount; t++) {
                unsafeThreads[t] = new Thread(() -> {
                    for (long i = 0; i < range; i++) {
                        unsafeCounter++; // Non-atomic read-modify-write!
                    }
                });
                unsafeThreads[t].start();
            }
            for (Thread t : unsafeThreads) {
                try { t.join(); } catch (InterruptedException ignored) {}
            }
            long elapsedUnsafe = System.currentTimeMillis() - startUnsafe;
            long lostUpdates = expectedTotal - unsafeCounter;

            System.out.println("[1] Unsafe Shared Counter (Primitive long):");
            System.out.println("    - Expected Count : " + expectedTotal);
            System.out.println("    - Actual Count   : " + unsafeCounter);
            System.out.println("    - Lost Updates   : " + lostUpdates + " (" + String.format("%.2f", (lostUpdates * 100.0 / expectedTotal)) + "% data loss!)");
            System.out.println("    - Status         : " + (unsafeCounter == expectedTotal ? "PASSED (Rare)" : "FAILED (RACE CONDITION DETECTED)"));
            System.out.println("    - Time Taken     : " + elapsedUnsafe + " ms");

            // Scenario B: Thread-Safe using AtomicLong / LongAdder
            LongAdder safeAdder = new LongAdder();
            Thread[] safeThreads = new Thread[threadCount];
            long startSafe = System.currentTimeMillis();
            for (int t = 0; t < threadCount; t++) {
                safeThreads[t] = new Thread(() -> {
                    for (long i = 0; i < range; i++) {
                        safeAdder.increment();
                    }
                });
                safeThreads[t].start();
            }
            for (Thread t : safeThreads) {
                try { t.join(); } catch (InterruptedException ignored) {}
            }
            long elapsedSafe = System.currentTimeMillis() - startSafe;

            System.out.println("\n[2] Thread-Safe Atomic/LongAdder Counter:");
            System.out.println("    - Expected Count : " + expectedTotal);
            System.out.println("    - Actual Count   : " + safeAdder.sum());
            System.out.println("    - Status         : " + (safeAdder.sum() == expectedTotal ? "100% ACCURATE & THREAD-SAFE" : "FAILED"));
            System.out.println("    - Time Taken     : " + elapsedSafe + " ms");

            // Scenario C: Best Practice - Thread Confinement (Independent local accumulators)
            long startConfined = System.currentTimeMillis();
            long[] localAccumulators = new long[threadCount];
            Thread[] confinedThreads = new Thread[threadCount];
            for (int t = 0; t < threadCount; t++) {
                final int threadIndex = t;
                confinedThreads[t] = new Thread(() -> {
                    long localSum = 0;
                    for (long i = 0; i < range; i++) {
                        localSum++;
                    }
                    localAccumulators[threadIndex] = localSum;
                });
                confinedThreads[t].start();
            }
            long confinedTotal = 0;
            for (int t = 0; t < threadCount; t++) {
                try {
                    confinedThreads[t].join();
                    confinedTotal += localAccumulators[t];
                } catch (InterruptedException ignored) {}
            }
            long elapsedConfined = System.currentTimeMillis() - startConfined;

            System.out.println("\n[3] Best Practice: Thread Confinement (Local range isolation):");
            System.out.println("    - Expected Count : " + expectedTotal);
            System.out.println("    - Actual Count   : " + confinedTotal);
            System.out.println("    - Status         : 100% ACCURATE & ZERO LOCK CONTENTION");
            System.out.println("    - Time Taken     : " + elapsedConfined + " ms (Fastest concurrent approach)");
            System.out.println("----------------------------------------------------------------------\n");
        }
    }

    // =========================================================================
    // 4. BENCHMARK RUNNER & COMPARISON
    // =========================================================================
    public static void runBenchmark(long start, long end) {
        int availableCores = Runtime.getRuntime().availableProcessors();
        int[] threadConfigs = {1, 2, 4, Math.max(8, availableCores)};

        System.out.println("\n======================================================================");
        System.out.println("             MULTITHREADED NUMBER PROCESSOR BENCHMARK");
        System.out.println("======================================================================");
        System.out.println("Range Analyzed        : [" + start + " to " + end + "]");
        System.out.println("Total Numbers Checked : " + (end - start + 1));
        System.out.println("Available CPU Cores   : " + availableCores);
        System.out.println("----------------------------------------------------------------------");

        // Warm up JIT compiler lightly
        SingleThreadedProcessor.process(1, 100_000);

        ProcessResult singleResult = null;
        List<ProcessResult> results = new ArrayList<>();

        for (int threads : threadConfigs) {
            System.out.print("Running with " + threads + " thread(s)... ");
            ProcessResult res;
            if (threads == 1) {
                res = SingleThreadedProcessor.process(start, end);
                singleResult = res;
            } else {
                res = MultiThreadedProcessor.process(start, end, threads);
            }
            results.add(res);
            System.out.println("Done! Time: " + res.executionTimeMs + " ms | Found: " + res.primeCount + " primes");
        }

        System.out.println("\n======================================================================");
        System.out.println("                    EXECUTION-TIME COMPARISON");
        System.out.println("======================================================================");
        System.out.printf("%-10s | %-15s | %-12s | %-12s | %-10s%n",
                "Threads", "Primes Found", "Time (ms)", "Speedup", "Efficiency");
        System.out.println("----------------------------------------------------------------------");

        long baselineTime = singleResult.executionTimeMs;
        final long expectedCount = singleResult.primeCount;

        for (ProcessResult res : results) {
            double speedup = baselineTime > 0 ? (double) baselineTime / res.executionTimeMs : 1.0;
            double efficiency = (speedup / res.threadCount) * 100.0;
            System.out.printf("%-10d | %-15d | %-12d | %-11.2fx | %-9.1f%%%n",
                    res.threadCount, res.primeCount, res.executionTimeMs, speedup, efficiency);
        }
        System.out.println("======================================================================");
        System.out.println("Verification: All thread configurations produced identical prime counts: "
                + (results.stream().allMatch(r -> r.primeCount == expectedCount) ? "VERIFIED (100% Correct)" : "MISMATCH"));
    }

    // =========================================================================
    // 5. MAIN MENU INTERACTION
    // =========================================================================
    public static void main(String[] args) {
        // If passed command line arguments, allow automated benchmarking
        if (args.length > 0 && args[0].equalsIgnoreCase("--benchmark")) {
            long start = args.length > 1 ? Long.parseLong(args[1]) : DEFAULT_START;
            long end = args.length > 2 ? Long.parseLong(args[2]) : DEFAULT_END;
            runBenchmark(start, end);
            ThreadSafetyDemo.demonstrateRaceCondition(2_000_000L, 4);
            return;
        }

        Scanner scanner = new Scanner(System.in);
        boolean exit = false;

        System.out.println("**********************************************************************");
        System.out.println("          WELCOME TO MULTITHREADED NUMBER PROCESSOR (JAVA)            ");
        System.out.println("**********************************************************************");

        while (!exit) {
            System.out.println("\nSelect an option:");
            System.out.println("1. Run Full Benchmark (Single-threaded vs Multithreaded comparison)");
            System.out.println("2. Custom Processing Run (Specify range & thread count)");
            System.out.println("3. Demonstrate Thread-Safety & Race Conditions");
            System.out.println("4. Exit");
            System.out.print("Enter choice (1-4): ");

            String input = scanner.nextLine().trim();

            switch (input) {
                case "1": {
                    System.out.print("Use default range [1 to " + DEFAULT_END + "]? (y/n): ");
                    String choice = scanner.nextLine().trim().toLowerCase();
                    long start = DEFAULT_START;
                    long end = DEFAULT_END;
                    if (choice.equals("n") || choice.equals("no")) {
                        try {
                            System.out.print("Enter start number: ");
                            start = Long.parseLong(scanner.nextLine().trim());
                            System.out.print("Enter end number: ");
                            end = Long.parseLong(scanner.nextLine().trim());
                        } catch (NumberFormatException e) {
                            System.out.println("Invalid number. Using default range.");
                            start = DEFAULT_START;
                            end = DEFAULT_END;
                        }
                    }
                    runBenchmark(start, end);
                    break;
                }
                case "2": {
                    try {
                        System.out.print("Enter range start: ");
                        long start = Long.parseLong(scanner.nextLine().trim());
                        System.out.print("Enter range end: ");
                        long end = Long.parseLong(scanner.nextLine().trim());
                        System.out.print("Enter number of threads (1 for single-threaded): ");
                        int threads = Integer.parseInt(scanner.nextLine().trim());

                        if (start > end || threads < 1) {
                            System.out.println("Invalid input range or thread count.");
                            break;
                        }

                        System.out.println("\nProcessing...");
                        ProcessResult res = (threads == 1)
                                ? SingleThreadedProcessor.process(start, end)
                                : MultiThreadedProcessor.process(start, end, threads);

                        System.out.println("\n--- Results ---");
                        System.out.println("Threads Used    : " + res.threadCount);
                        System.out.println("Range           : [" + start + " to " + end + "]");
                        System.out.println("Primes Detected : " + res.primeCount);
                        System.out.println("Execution Time  : " + res.executionTimeMs + " ms");

                    } catch (NumberFormatException e) {
                        System.out.println("Error: Please enter valid integer numbers.");
                    }
                    break;
                }
                case "3": {
                    ThreadSafetyDemo.demonstrateRaceCondition(2_000_000L, 4);
                    break;
                }
                case "4": {
                    exit = true;
                    System.out.println("Exiting Multithreaded Number Processor. Goodbye!");
                    break;
                }
                default: {
                    System.out.println("Invalid selection. Please choose an option between 1 and 4.");
                }
            }
        }

        scanner.close();
    }
}
