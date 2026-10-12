# Project Report: Multithreaded Number Processor

## 1. Objective
The primary objective of this project is to explore concurrent execution and basic multithreading principles in Java. By partitioning a large numerical computation across multiple worker threads, the project evaluates:
- Work distribution across CPU cores.
- Performance speedup versus single-threaded baseline execution.
- Pitfalls of shared mutable state (race conditions).
- Best practices for thread safety, synchronization, and thread confinement.

---

## 2. Problem Description & Workload Selection
Numerical tasks that are **CPU-bound** are ideal candidates for multithreaded acceleration because they are limited by processing power rather than I/O bottlenecks. 

For this benchmark, the application tests primality across a large integer range $[1, N]$ using the trial division algorithm (checking factors up to $\sqrt{n}$ with the $6k \pm 1$ optimization). The computational complexity scales non-linearly with number magnitude, providing a realistic, heavy CPU workload.

---

## 3. Implementation Architecture

### 3.1 Single-Threaded Version
- **Mechanism:** A single thread sequentially iterates over the range $[start, end]$ and tests each number for primality.
- **Characteristics:**
  - Simple, predictable, and requires no synchronization overhead.
  - Limited to utilizing only **a single CPU core** (12.5% of an 8-core CPU), leaving the remaining hardware cores idle.

### 3.2 Multithreaded Version
- **Workload Partitioning:** The master range $[start, end]$ is partitioned into $T$ disjoint contiguous sub-ranges:
  $$\text{Chunk Size} = \lfloor \frac{\text{Total Numbers}}{T} \rfloor$$
- **Worker Execution:** Each worker thread is an instance of `Thread` assigned its own `rangeStart` and `rangeEnd`.
- **Result Joining:** The main thread starts all workers concurrently and invokes `worker.join()` on each thread before accumulating the individual counts.

---

## 4. Execution-Time Comparison & Benchmarks

The benchmark was executed on an **8-Core AMD/Intel Processor** testing $3,000,000$ numbers in the range $[1, 3,000,000]$:

| Threads | Primes Count | Execution Time (ms) | Speedup Factor | Core Efficiency (%) |
| :---: | :---: | :---: | :---: | :---: |
| **1 (Single)** | 216,816 | 1,002 ms | **1.00x** | 100.0% |
| **2** | 216,816 | 724 ms | **1.38x** | 69.2% |
| **4** | 216,816 | 461 ms | **2.17x** | 54.3% |
| **8** | 216,816 | 369 ms | **2.72x** | 33.9% |

### Observations:
1. **Mathematical Correctness:** All thread configurations produced the exact same prime count (**216,816 primes**), proving that range partitioning preserved exact calculation integrity.
2. **Speedup:** Execution time dropped from **1,002 ms** down to **369 ms** (a **2.72x speedup** on 8 threads).
3. **Diminishing Returns (Amdahl's Law):** The scaling is sub-linear due to thread creation/joining overhead, context switching, and the non-uniform computational density across numerical ranges (higher numbers take longer to evaluate primality than lower numbers).

---

## 5. Thread-Safety Considerations

Concurrency introduces the risk of data races when multiple threads access and mutate shared resources simultaneously. The project analyzes and benchmarks three concurrency strategies:

### 5.1 Scenario A: Naive Shared Mutable State (Race Condition)
```java
// Multiple threads executing:
unsafeCounter++; // Non-atomic: read -> modify -> write
```
- **Observed Behavior:**
  - Expected Count: 8,000,000
  - Actual Count: 5,275,154
  - **Data Loss: ~34.06%** lost updates due to overlapping memory writes.
- **Root Cause:** In the Java Memory Model, `++` is not an atomic operation. Two threads reading the same register value before writing back overwrite each other's increments.

### 5.2 Scenario B: Atomic / Thread-Safe Counters
```java
LongAdder safeAdder = new LongAdder();
safeAdder.increment();
```
- **Observed Behavior:**
  - Expected: 8,000,000 | Actual: 8,000,000 (100% accurate).
  - Time: 62 ms.
- **Analysis:** `LongAdder` maintains a striped array of cells to reduce contention among threads, merging them into a final sum without blocking locks.

### 5.3 Scenario C: Thread Confinement / Range Isolation (Best Practice)
```java
// Each worker accumulates locally in a private variable:
long localCount = 0;
// Only combined after worker.join()
```
- **Observed Behavior:**
  - Expected: 8,000,000 | Actual: 8,000,000 (100% accurate).
  - Time: **26 ms (Fastest)**.
- **Analysis:** Eliminating shared mutable state entirely removes the need for cache-line invalidation, bus locking, or atomic CAS instructions. This allows threads to run at peak processor speed.

---

## 6. Key Takeaways
1. **Divide and Conquer:** Data parallelism with independent range partitioning allows CPU-heavy algorithms to scale efficiently across multiple cores.
2. **Avoid Shared State:** The most performant and bug-free multithreaded designs avoid shared mutable state wherever possible (Thread Confinement).
3. **Synchronization Trade-offs:** When shared counters are mandatory, specialized concurrent data structures like `LongAdder` or `AtomicLong` prevent data corruption while minimizing thread contention.
