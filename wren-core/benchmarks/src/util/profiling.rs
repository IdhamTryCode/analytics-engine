//! Performance profiling utilities
use std::time::{Duration, Instant};

/// Simple performance profiler
pub struct Profiler {
    start: Instant,
    checkpoints: Vec<(String, Duration)>,
}

impl Profiler {
    pub fn new() -> Self {
        Self {
            start: Instant::now(),
            checkpoints: Vec::new(),
        }
    }

    pub fn checkpoint(&mut self, name: impl Into<String>) {
        let elapsed = self.start.elapsed();
        self.checkpoints.push((name.into(), elapsed));
    }

    pub fn get_checkpoints(&self) -> &[(String, Duration)] {
        &self.checkpoints
    }

    pub fn total_elapsed(&self) -> Duration {
        self.start.elapsed()
    }

    pub fn to_string(&self) -> String {
        let mut result = String::new();
        result.push_str("Profiling Results:\n");
        
        let mut prev = Duration::ZERO;
        for (name, elapsed) in &self.checkpoints {
            let diff = *elapsed - prev;
            result.push_str(&format!(
                "  {}: {:.2}ms (diff: {:.2}ms)\n",
                name,
                elapsed.as_secs_f64() * 1000.0,
                diff.as_secs_f64() * 1000.0
            ));
            prev = *elapsed;
        }
        
        result.push_str(&format!(
            "  Total: {:.2}ms\n",
            self.total_elapsed().as_secs_f64() * 1000.0
        ));
        
        result
    }
}

impl Default for Profiler {
    fn default() -> Self {
        Self::new()
    }
}

/// Memory usage tracking (basic)
pub struct MemoryTracker {
    initial_memory: Option<usize>,
}

impl MemoryTracker {
    pub fn new() -> Self {
        Self {
            initial_memory: None,
        }
    }

    pub fn snapshot(&mut self) {
        // Note: This is a placeholder. Real implementation would use
        // platform-specific APIs to get actual memory usage
        self.initial_memory = Some(0);
    }

    pub fn get_usage(&self) -> Option<usize> {
        self.initial_memory
    }
}

impl Default for MemoryTracker {
    fn default() -> Self {
        Self::new()
    }
}


