//! Custom error types for analytics-core with proper error handling
use datafusion::common::DataFusionError;
use std::fmt;

/// Custom error type for Analytics Core operations
#[derive(Debug)]
pub enum AnalyticsCoreError {
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

impl fmt::Display for AnalyticsCoreError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            AnalyticsCoreError::DataFusion(e) => write!(f, "DataFusion error: {}", e),
            AnalyticsCoreError::SqlParse(msg) => write!(f, "SQL parse error: {}", msg),
            AnalyticsCoreError::MdlValidation(msg) => write!(f, "MDL validation error: {}", msg),
            AnalyticsCoreError::InputValidation(msg) => write!(f, "Input validation error: {}", msg),
            AnalyticsCoreError::PermissionDenied(msg) => write!(f, "Permission denied: {}", msg),
            AnalyticsCoreError::ResourceExhausted(msg) => write!(f, "Resource exhausted: {}", msg),
            AnalyticsCoreError::Internal(msg) => write!(f, "Internal error: {}", msg),
        }
    }
}

impl std::error::Error for AnalyticsCoreError {
    fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        match self {
            AnalyticsCoreError::DataFusion(e) => Some(e),
            _ => None,
        }
    }
}

impl From<DataFusionError> for AnalyticsCoreError {
    fn from(err: DataFusionError) -> Self {
        AnalyticsCoreError::DataFusion(err)
    }
}

/// Result type alias for Analytics Core operations
pub type AnalyticsCoreResult<T> = Result<T, AnalyticsCoreError>;

/// Input validation utilities
pub mod validation {
    use super::AnalyticsCoreError;
    use super::AnalyticsCoreResult;

    /// Maximum SQL query length (1MB)
    pub const MAX_SQL_LENGTH: usize = 1_048_576;
    
    /// Maximum SQL query depth (nested subqueries)
    pub const MAX_SQL_DEPTH: usize = 100;

    /// Validate SQL input
    pub fn validate_sql(sql: &str) -> AnalyticsCoreResult<()> {
        if sql.is_empty() {
            return Err(AnalyticsCoreError::InputValidation("SQL query cannot be empty".to_string()));
        }

        if sql.len() > MAX_SQL_LENGTH {
            return Err(AnalyticsCoreError::InputValidation(format!(
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
    pub fn validate_mdl_size(mdl_json: &str) -> AnalyticsCoreResult<()> {
        const MAX_MDL_SIZE: usize = 10_485_760; // 10MB

        if mdl_json.len() > MAX_MDL_SIZE {
            return Err(AnalyticsCoreError::InputValidation(format!(
                "MDL JSON exceeds maximum size of {} bytes",
                MAX_MDL_SIZE
            )));
        }

        Ok(())
    }
}

