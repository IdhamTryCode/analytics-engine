//! Caching layer for expensive MDL computations
use crate::mdl::{AnalyzedAnalyticsMDL, AnalyticsMDL};
use crate::mdl::context::{Mode, SessionPropertiesRef};
use crate::mdl::lineage::Lineage;
use crate::mdl::manifest::Manifest;
use crate::performance::cache::Cache;
use datafusion::error::Result;
use log::debug;
use std::hash::{Hash, Hasher};
use std::sync::{Arc, OnceLock};

/// Cache key for Lineage computation
#[derive(Hash, PartialEq, Eq, Clone)]
struct LineageCacheKey {
    manifest_hash: u64,
}

/// Cache key for AnalyzedAnalyticsMDL computation
#[derive(Hash, PartialEq, Eq, Clone)]
struct AnalyzedMDLCacheKey {
    manifest_hash: u64,
    properties_hash: u64,
    mode: Mode,
}

impl Hash for Mode {
    fn hash<H: Hasher>(&self, state: &mut H) {
        match self {
            Mode::LocalRuntime => 0u8.hash(state),
            Mode::Unparse => 1u8.hash(state),
            Mode::PermissionAnalyze => 2u8.hash(state),
        }
    }
}

/// Compute hash for Manifest
fn hash_manifest(manifest: &Manifest) -> u64 {
    use std::collections::hash_map::DefaultHasher;
    let mut hasher = DefaultHasher::new();
    manifest.hash(&mut hasher);
    hasher.finish()
}

/// Compute hash for SessionPropertiesRef
fn hash_properties(properties: &SessionPropertiesRef) -> u64 {
    use std::collections::hash_map::DefaultHasher;
    let mut hasher = DefaultHasher::new();
    // Sort keys for consistent hashing
    let mut sorted: Vec<_> = properties.iter().collect();
    sorted.sort_by_key(|(k, _)| k.clone());
    for (k, v) in sorted {
        k.hash(&mut hasher);
        if let Some(v) = v {
            v.hash(&mut hasher);
        } else {
            None::<String>.hash(&mut hasher);
        }
    }
    hasher.finish()
}

// Global caches - initialized lazily
static LINEAGE_CACHE: OnceLock<Cache<LineageCacheKey, Arc<Lineage>>> = OnceLock::new();
static ANALYZED_MDL_CACHE: OnceLock<Cache<AnalyzedMDLCacheKey, Arc<AnalyzedAnalyticsMDL>>> = OnceLock::new();

/// Get or initialize Lineage cache
fn get_lineage_cache() -> &'static Cache<LineageCacheKey, Arc<Lineage>> {
    LINEAGE_CACHE.get_or_init(|| {
        // Cache size: 100 entries (adjust based on typical usage)
        Cache::new(100)
    })
}

/// Get or initialize AnalyzedAnalyticsMDL cache
fn get_analyzed_mdl_cache() -> &'static Cache<AnalyzedMDLCacheKey, Arc<AnalyzedAnalyticsMDL>> {
    ANALYZED_MDL_CACHE.get_or_init(|| {
        // Cache size: 50 entries (analyzed MDL is larger, so smaller cache)
        Cache::new(50)
    })
}

/// Compute Lineage with caching
pub fn compute_lineage_cached(mdl: &AnalyticsMDL) -> Result<Arc<Lineage>> {
    let cache_key = LineageCacheKey {
        manifest_hash: hash_manifest(&mdl.manifest),
    };

    // Try to get from cache
    if let Some(cached) = get_lineage_cache().get(&cache_key) {
        debug!("Lineage cache hit for manifest hash: {}", cache_key.manifest_hash);
        return Ok(cached);
    }

    // Cache miss - compute lineage
    debug!("Lineage cache miss, computing...");
    let lineage = Arc::new(Lineage::new(mdl)?);
    
    // Store in cache
    get_lineage_cache().insert(cache_key.clone(), lineage.clone());
    
    Ok(lineage)
}

/// Compute AnalyzedAnalyticsMDL with caching
pub fn compute_analyzed_mdl_cached(
    manifest: Manifest,
    properties: SessionPropertiesRef,
    mode: Mode,
) -> Result<Arc<AnalyzedAnalyticsMDL>> {
    // Compute cache key (using references to avoid consuming values)
    let cache_key = AnalyzedMDLCacheKey {
        manifest_hash: hash_manifest(&manifest),
        properties_hash: hash_properties(&properties),
        mode: mode.clone(),
    };

    // Try to get from cache
    if let Some(cached) = get_analyzed_mdl_cache().get(&cache_key) {
        debug!(
            "AnalyzedAnalyticsMDL cache hit for manifest hash: {}, properties hash: {}, mode: {:?}",
            cache_key.manifest_hash, cache_key.properties_hash, cache_key.mode
        );
        return Ok(cached);
    }

    // Cache miss - compute analyzed MDL (values are consumed here)
    debug!("AnalyzedAnalyticsMDL cache miss, computing...");
    let analyzed_mdl = Arc::new(AnalyzedAnalyticsMDL::analyze_uncached(manifest, properties, mode)?);
    
    // Store in cache
    get_analyzed_mdl_cache().insert(cache_key.clone(), analyzed_mdl.clone());
    
    Ok(analyzed_mdl)
}

/// Clear all caches (useful for testing or memory management)
pub fn clear_caches() {
    if let Some(cache) = LINEAGE_CACHE.get() {
        cache.clear();
    }
    if let Some(cache) = ANALYZED_MDL_CACHE.get() {
        cache.clear();
    }
}

/// Get cache statistics (for monitoring)
pub fn get_cache_stats() -> (usize, usize) {
    let lineage_size = LINEAGE_CACHE.get().map(|c| c.len()).unwrap_or(0);
    let analyzed_size = ANALYZED_MDL_CACHE.get().map(|c| c.len()).unwrap_or(0);
    (lineage_size, analyzed_size)
}

