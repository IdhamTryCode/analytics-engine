//! Performance optimization utilities
use std::sync::Arc;

/// Smart Arc cloning - only clone when necessary
/// This is a helper to reduce unnecessary Arc clones
pub trait SmartClone {
    type Output;
    fn smart_clone(&self) -> Self::Output;
}

impl<T> SmartClone for Arc<T> {
    type Output = Arc<T>;
    
    /// Only clone if we're not the last reference
    /// In most cases, we can just return a reference
    fn smart_clone(&self) -> Arc<T> {
        // For now, just use regular clone
        // In the future, we could use Arc::try_unwrap to avoid clones
        // when we're the only owner
        Arc::clone(self)
    }
}

/// String optimization utilities
pub mod string_ops {
    /// Pre-allocate string with estimated capacity
    pub fn with_capacity_for_replace(original: &str, pattern: &str, replacement: &str) -> String {
        let estimated_capacity = original.len() + 
            (replacement.len().saturating_sub(pattern.len())) * 
            original.matches(pattern).count();
        String::with_capacity(estimated_capacity)
    }

    /// Efficient string replacement with pre-allocated capacity
    pub fn replace_efficient(original: &str, pattern: &str, replacement: &str) -> String {
        // Early return if pattern not found
        if !original.contains(pattern) {
            return original.to_string();
        }
        
        // Pre-allocate with estimated capacity
        let capacity = with_capacity_for_replace(original, pattern, replacement).capacity();
        let mut result = String::with_capacity(capacity);
        
        // Manual replacement to use pre-allocated capacity
        let mut last_end = 0;
        for (start, _) in original.match_indices(pattern) {
            result.push_str(&original[last_end..start]);
            result.push_str(replacement);
            last_end = start + pattern.len();
        }
        result.push_str(&original[last_end..]);
        result
    }
}

/// Caching utilities
pub mod cache {
    use std::collections::HashMap;
    use std::hash::Hash;
    use std::sync::{Arc, RwLock};

    /// Simple in-memory cache with LRU-like behavior
    pub struct Cache<K, V> {
        data: Arc<RwLock<HashMap<K, V>>>,
        max_size: usize,
    }

    impl<K, V> Cache<K, V>
    where
        K: Hash + Eq + Clone,
    {
        pub fn new(max_size: usize) -> Self {
            Self {
                data: Arc::new(RwLock::new(HashMap::new())),
                max_size,
            }
        }

        pub fn get(&self, key: &K) -> Option<V>
        where
            V: Clone,
        {
            self.data.read().ok()?.get(key).cloned()
        }

        pub fn insert(&self, key: K, value: V) {
            let mut data = match self.data.write() {
                Ok(guard) => guard,
                Err(_) => return,
            };

            // Simple eviction: if cache is full, remove oldest (first) entry
            if data.len() >= self.max_size {
                if let Some(first_key) = data.keys().next().cloned() {
                    data.remove(&first_key);
                }
            }

            data.insert(key, value);
        }

        pub fn clear(&self) {
            if let Ok(mut data) = self.data.write() {
                data.clear();
            }
        }

        pub fn len(&self) -> usize {
            self.data.read().map(|d| d.len()).unwrap_or(0)
        }
    }
}

