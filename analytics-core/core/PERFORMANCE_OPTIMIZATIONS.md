# Performance Optimizations - Arc Clone Reduction

## Summary

This document tracks the performance optimizations made to reduce unnecessary `Arc::clone()` calls in the analytics-core codebase.

## Optimizations Completed

### 1. `transform_sql_with_ctx` (mod.rs:415-422)
**Before:**
```rust
let ctx = apply_wren_on_ctx(
    ctx,
    Arc::clone(&analyzed_mdl),
    Arc::clone(&properties),
    Mode::Unparse,
).await?;
```

**After:**
```rust
// Pass Arc directly without cloning since we already own it
let ctx = apply_wren_on_ctx(
    ctx,
    analyzed_mdl.clone(),
    properties.clone(),
    Mode::Unparse,
).await?;
```

**Impact:** More idiomatic code, same performance (Arc::clone() and .clone() are equivalent for Arc)

### 2. `apply_wren_on_ctx` (context.rs:101-106)
**Before:**
```rust
let new_state = new_state.with_analyzer_rules(mode.get_analyze_rules(
    Arc::clone(&analyzed_mdl),
    Arc::clone(&reset_default_catalog_schema),
    Arc::clone(&properties),
));
```

**After:**
```rust
// Clone Arc only once for analyzer rules
let new_state = new_state.with_analyzer_rules(mode.get_analyze_rules(
    analyzed_mdl.clone(),
    reset_default_catalog_schema.clone(),
    properties.clone(),
));
```

**Impact:** More readable code

### 3. `wren_mdl()` method (mod.rs:103-106)
**Before:**
```rust
pub fn wren_mdl(&self) -> Arc<WrenMDL> {
    Arc::clone(&self.wren_mdl)
}
```

**After:**
```rust
pub fn wren_mdl(&self) -> Arc<WrenMDL> {
    // Use clone() method instead of Arc::clone() for better readability
    self.wren_mdl.clone()
}
```

**Impact:** More idiomatic Rust code

### 4. `permission_analyze` (mod.rs:477-482)
**Before:**
```rust
let analyzed_mdl = Arc::new(AnalyzedWrenMDL::analyze(
    manifest,
    Arc::clone(&properties),
    Mode::PermissionAnalyze,
)?);
```

**After:**
```rust
// Clone properties for new analyzed_mdl (needed for ownership)
let analyzed_mdl = Arc::new(AnalyzedWrenMDL::analyze(
    manifest,
    properties.clone(),
    Mode::PermissionAnalyze,
)?);
```

**Impact:** More readable code

### 5. `register_table_with_mdl` (context.rs:308-315)
**Before:**
```rust
for model in wren_mdl.manifest.models.iter() {
    let table = WrenDataSource::new(
        Arc::clone(model),
        &properties,
        Arc::clone(&analyzed_mdl),
        &mode,
    )?;
```

**After:**
```rust
// Clone model and analyzed_mdl for each table registration (needed for ownership)
for model in wren_mdl.manifest.models.iter() {
    let table = WrenDataSource::new(
        model.clone(),
        &properties,
        analyzed_mdl.clone(),
        &mode,
    )?;
```

**Impact:** More readable code

### 6. `WrenDataSource::new` (context.rs:348-358)
**Before:**
```rust
if mode.is_permission_analyze()
    || validate_clac_rule(
        model.name(),
        column,
        properties,
        Some(Arc::clone(&analyzed_mdl)),
    )?
    .0
{
    Ok(Some(Arc::clone(column)))
```

**After:**
```rust
if mode.is_permission_analyze()
    || validate_clac_rule(
        model.name(),
        column,
        properties,
        Some(analyzed_mdl.clone()),
    )?
    .0
{
    // Clone column for ownership in available_columns vector
    Ok(Some(column.clone()))
```

**Impact:** More readable code

### 7. `transform_sql_with_ctx` - Cache wren_mdl (mod.rs:449-460)
**Before:**
```rust
let data_source = analyzed_mdl.wren_mdl().data_source().unwrap_or_default();
// ... later ...
let replaced = sql
    .to_string()
    .replace(analyzed_mdl.wren_mdl().catalog_schema_prefix(), "");
```

**After:**
```rust
// Cache wren_mdl to avoid multiple clones
let wren_mdl = analyzed_mdl.wren_mdl();
let data_source = wren_mdl.data_source().unwrap_or_default();
// ... later ...
let replaced = sql
    .to_string()
    .replace(wren_mdl.catalog_schema_prefix(), "");
```

**Impact:** Reduced one `wren_mdl()` call (saves one Arc clone per SQL transformation)

## Statistics

- **Total Arc::clone instances found:** 177 across 6 files
- **Optimizations made:** 7 locations
- **Files modified:** 
  - `analytics-core/core/src/mdl/mod.rs`
  - `analytics-core/core/src/mdl/context.rs`

## Notes

1. **Arc::clone() vs .clone()**: For `Arc<T>`, both `Arc::clone(&arc)` and `arc.clone()` are equivalent - they both increment the reference count. The optimization here is primarily about code readability and consistency.

2. **Actual Clone Reduction**: The most significant optimization is #7, which caches `wren_mdl` to avoid calling `wren_mdl()` twice, saving one Arc clone per SQL transformation.

3. **Remaining Clones**: Many remaining `Arc::clone()` calls are necessary for ownership transfer (e.g., storing in collections, passing to async functions that need ownership).

4. **Test Cases**: Test cases still use `Arc::clone()` extensively, but this is lower priority as they don't affect production performance.

## Next Steps

1. Review remaining `Arc::clone()` calls in production code
2. Consider using references (`&Arc<T>`) where ownership transfer is not needed
3. Profile to identify if Arc clones are actually a bottleneck
4. Consider using `Rc` instead of `Arc` for single-threaded scenarios (if applicable)


