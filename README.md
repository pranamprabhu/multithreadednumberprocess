# Multithreaded Number Processor (Java)

## Overview
The **Multithreaded Number Processor** is a high-performance Java application designed to demonstrate the fundamentals of multithreading, concurrent execution, range partitioning, and thread safety.

It splits a computationally intensive numerical task (prime number identification across large integer ranges) across multiple threads and compares its execution performance, scalability, and efficiency against a baseline single-threaded implementation.

---

## Features & Deliverables
1. **Single-Threaded Implementation**:
   - Sequentially analyzes numbers from start to end on a single main thread.
   - Provides baseline execution time for performance metrics.

2. **Multithreaded Implementation**:
   - Divides the numerical workload into independent, disjoint sub-ranges.
   - Spawns worker threads that independently process their allocated chunk.
   - Aggregates results after thread synchronization (`Thread.join()`).

3. **Execution-Time Comparison**:
   - Accurately measures elapsed execution time in milliseconds (`System.currentTimeMillis()`).
   - Automatically computes **Speedup** ($T_{\text{single}} / T_{\text{multi}}$) and **Core Efficiency** across 1, 2, 4, and 8+ threads.
   - Validates correctness by verifying identical output across all thread counts.

4. **Thread-Safety Analysis & Demonstration**:
   - **Race Conditions**: Demonstrates un-synchronized shared mutable state leading to lost updates (~34% data loss).
   - **Atomic Aggregation**: Demonstrates synchronization via `java.util.concurrent.atomic.LongAdder`.
   - **Thread Confinement**: Demonstrates range isolation with thread-local accumulators, eliminating contention and yielding maximal throughput.

---

## Project Structure
```
MultithreadedNumberProcessor/
│── MultithreadedNumberProcessor.java  # Main application with single/multi-thread processors & benchmark
│── MultithreadedNumberProcessor.class # Compiled bytecode
│── README.md                          # Documentation and usage instructions
└── Report.md                          # Comprehensive project report & performance analysis
```

---

## Compilation and Execution

### 1. Compile
```powershell
javac MultithreadedNumberProcessor.java
```

### 2. Run Interactive Mode
```powershell
java MultithreadedNumberProcessor
```
Interactive options include:
- `1`: Run Full Benchmark (Single-threaded vs Multithreaded comparison table)
- `2`: Custom Processing Run (Specify custom start, end, and thread count)
- `3`: Demonstrate Thread Safety & Race Conditions
- `4`: Exit

### 3. Run Automated Benchmark Mode
```powershell
# Runs automated benchmark for range [1, 3000000]
java MultithreadedNumberProcessor --benchmark 1 3000000
```
