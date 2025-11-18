//! Custom error types for analytics-core with proper error handling
use datafusion::common::DataFusionError;
use std::fmt;

/// Custom error type for Wren Core operations
#[derive(Debug)]
pub enum WrenCoreError {
    /// DataFusion related errors
    DataFusion(DataFusionError),
    /// SQL parsing errors
    SqlParse(String),
    /// MDL validation errors
    MdlValidation(String),
    /// Input validation errors
    InputValidation(String),
    /// Permission denied errors
    PermissionDenied(String),
    /// Resource exhaustion (memory, timeout, etc.)
    ResourceExhausted(String),
    /// Internal errors
    Internal(String),
}

impl fmt::Display for WrenCoreError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            WrenCoreError::DataFusion(e) => write!(f, "DataFusion error: {}", e),
            WrenCoreError::SqlParse(msg) => write!(f, "SQL parse error: {}", msg),
            WrenCoreError::MdlValidation(msg) => write!(f, "MDL validation error: {}", msg),
            WrenCoreError::InputValidation(msg) => write!(f, "Input validation error: {}", msg),
            WrenCoreError::PermissionDenied(msg) => write!(f, "Permission denied: {}", msg),
            WrenCoreError::ResourceExhausted(msg) => write!(f, "Resource exhausted: {}", msg),
            WrenCoreError::Internal(msg) => write!(f, "Internal error: {}", msg),
        }
    }
}

impl std::error::Error for WrenCoreError {
    fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        match self {
            WrenCoreError::DataFusion(e) => Some(e),
            _ => None,
        }
    }
}

impl From<DataFusionError> for WrenCoreError {
    fn from(err: DataFusionError) -> Self {
        WrenCoreError::DataFusion(err)
    }
}

/// Result type alias for Wren Core operations
pub type WrenCoreResult<T> = Result<T, WrenCoreError>;

/// Input validation utilities
pub mod validation {
    use super::WrenCoreError;
    use super::WrenCoreResult;

    /// Maximum SQL query length (1MB)
    pub const MAX_SQL_LENGTH: usize = 1_048_576;
    
    /// Maximum SQL query depth (nested subqueries)
    pub const MAX_SQL_DEPTH: usize = 100;

    /// Validate SQL input
    pub fn validate_sql(sql: &str) -> WrenCoreResult<()> {
        if sql.is_empty() {
            return Err(WrenCoreError::InputValidation("SQL query cannot be empty".to_string()));
        }

        if sql.len() > MAX_SQL_LENGTH {
            return Err(WrenCoreError::InputValidation(format!(
                "SQL query exceeds maximum length of {} bytes",
                MAX_SQL_LENGTH
            )));
        }

        // Check for potential SQL injection patterns (basic check)
        let dangerous_patterns = [
            ("; DROP", "Potential SQL injection detected"),
            ("; DELETE", "Potential SQL injection detected"),
            ("; UPDATE", "Potential SQL injection detected"),
            ("--", "SQL comment detected"),
        ];

        for (pattern, msg) in dangerous_patterns.iter() {
            if sql.contains(pattern) {
                // Log warning but don't block (false positives possible)
                log::warn!("{}: {}", msg, pattern);
            }
        }

        Ok(())
    }

    /// Validate MDL JSON size
    pub fn validate_mdl_size(mdl_json: &str) -> WrenCoreResult<()> {
        const MAX_MDL_SIZE: usize = 10_485_760; // 10MB

        if mdl_json.len() > MAX_MDL_SIZE {
            return Err(WrenCoreError::InputValidation(format!(
                "MDL JSON exceeds maximum size of {} bytes",
                MAX_MDL_SIZE
            )));
        }

        Ok(())
    }
}

