# Benchmark Guide - Before vs After Comparison

## Quick Start

### 1. Run Baseline Benchmark (Before Improvements)

```bash
cd analytics-core/benchmarks

# Run all TPC-H queries with 20 iterations
cargo run --release --bin tpch -- benchmark --all-queries -i 20 -o results/baseline.json

# Or run specific query
cargo run --release --bin tpch -- benchmark --query 1 -i 20 -o results/baseline_q1.json
```

### 2. Apply Improvements

Lakukan improvements sesuai dengan `IMPROVEMENTS.md`:
- Security fixes
- Performance optimizations
- Code improvements

### 3. Run Comparison Benchmark (After Improvements)

```bash
# Run same queries after improvements
cargo run --release --bin tpch -- benchmark --all-queries -i 20 -o results/improved.json
```

### 4. Compare Results

```bash
# Generate comparison report
cargo run --release --bin compare -- \
  --baseline results/baseline.json \
  --comparison results/improved.json \
  --baseline-name "Before Improvements" \
  --comparison-name "After Improvements" \
  --format markdown \
  --output results/comparison.md

# Or view as table
cargo run --release --bin compare -- \
  --baseline results/baseline.json \
  --comparison results/improved.json \
  --format table

# Or export as JSON for further analysis
cargo run --release --bin compare -- \
  --baseline results/baseline.json \
  --comparison results/improved.json \
  --format json \
  --output results/comparison.json
```

## Understanding Results

### Metrics Explained

- **Mean**: Average execution time across all iterations
- **Median**: Middle value (50th percentile)
- **P95**: 95th percentile (95% of queries are faster than this)
- **P99**: 99th percentile (99% of queries are faster than this)
- **Std Dev**: Standard deviation (measure of variance)

### Status Indicators

- ✅ **Improved**: >10% faster
- ❌ **Regressed**: >10% slower
- ⬇️ **Faster**: 1-10% faster
- ⬆️ **Slower**: 1-10% slower
- ➡️ **No Change**: <1% difference

### Example Output

```
Comparing Before Improvements vs After Improvements
================================================================================
| Query | Baseline (ms) | Comparison (ms) | Change % | Status        |
|-------|---------------|-----------------|----------|---------------|
| Q1    | 4.25          | 3.89           | -8.47%   | ⬇️ Faster     |
| Q2    | 11.25         | 10.12          | -10.04%  | ✅ Improved   |
| Q3    | 5.03          | 5.15           | 2.39%    | ⬆️ Slower     |
...

Summary: 22 queries, 15 faster, 5 slower, 3 improved, 0 regressed
Overall: -5.23% change (faster)
```

## Advanced Usage

### Custom Benchmark Queries

Create custom benchmark queries in `analytics-core/benchmarks/src/wren/`:

```rust
// src/wren/custom.rs
pub fn custom_query_1() -> String {
    r#"
    SELECT ...
    "#
    .to_string()
}
```

### Profiling Integration

Use the profiler to identify bottlenecks:

```rust
use wren_benchmarks::util::profiling::Profiler;

let mut profiler = Profiler::new();
// ... do work ...
profiler.checkpoint("sql_parsing");
// ... do more work ...
profiler.checkpoint("planning");
// ... do more work ...
profiler.checkpoint("execution");

println!("{}", profiler.to_string());
```

### Continuous Benchmarking

Add to CI/CD pipeline:

```yaml
# .github/workflows/benchmark.yml
name: Benchmark

on:
  pull_request:
    paths:
      - 'analytics-core/**'

jobs:
  benchmark:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Run baseline
        run: |
          cd analytics-core/benchmarks
          cargo run --release --bin tpch -- benchmark --all-queries -i 10 -o baseline.json
      - name: Run comparison
        run: |
          cd analytics-core/benchmarks
          cargo run --release --bin tpch -- benchmark --all-queries -i 10 -o comparison.json
      - name: Compare
        run: |
          cd analytics-core/benchmarks
          cargo run --release --bin compare -- \
            --baseline baseline.json \
            --comparison comparison.json \
            --format markdown \
            --output comparison.md
      - name: Upload results
        uses: actions/upload-artifact@v3
        with:
          name: benchmark-results
          path: analytics-core/benchmarks/comparison.md
```

## Troubleshooting

### Benchmark fails to compile
- Ensure you're using `--release` flag
- Check that all dependencies are up to date: `cargo update`

### Results seem inconsistent
- Increase iteration count (`-i 50` or more)
- Ensure system is idle (no other processes running)
- Use `taskset` to pin to specific CPU cores

### Comparison tool errors
- Ensure both JSON files have same query IDs
- Check JSON format is valid
- Verify file paths are correct

## Best Practices

1. **Consistent Environment**: Run benchmarks on same machine with same configuration
2. **Multiple Iterations**: Use at least 20 iterations for statistical significance
3. **Warm-up**: First iteration is often slower, consider discarding it
4. **Documentation**: Document any system changes between benchmark runs
5. **Version Control**: Commit benchmark results with code changes
6. **Regression Testing**: Set thresholds for acceptable performance changes

## Performance Targets

Based on analysis, target improvements:

- **Security**: 0 panics from `unwrap()` in production code
- **Performance**: 
  - Reduce unnecessary clones by 50%
  - Improve query execution time by 10-20%
  - Reduce memory usage by 20-30%

## Next Steps

1. Run baseline benchmarks
2. Apply improvements incrementally
3. Measure after each improvement
4. Document findings
5. Iterate based on results


