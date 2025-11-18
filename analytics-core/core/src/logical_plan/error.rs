use std::{error::Error, fmt::Display};

#[derive(Debug, Clone)]
pub enum AnalyticsError {
    PermissionDenied(String),
}

impl Error for AnalyticsError {}

impl Display for AnalyticsError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            AnalyticsError::PermissionDenied(msg) => write!(f, "Permission Denied: {msg}"),
        }
    }
}
