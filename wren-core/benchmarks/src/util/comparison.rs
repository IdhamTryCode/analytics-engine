//! Enhanced benchmark comparison utilities
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::path::Path;

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct QueryMetrics {
    pub query_id: String,
    pub iterations: Vec<f64>, // milliseconds
    pub mean: f64,
    pub median: f64,
    pub p95: f64,
    pub p99: f64,
    pub min: f64,
    pub max: f64,
    pub std_dev: f64,
}

impl QueryMetrics {
    pub fn from_iterations(query_id: String, iterations: Vec<f64>) -> Self {
        let mut sorted = iterations.clone();
        sorted.sort_by(|a, b| a.partial_cmp(b).unwrap());

        let mean = iterations.iter().sum::<f64>() / iterations.len() as f64;
        let median = if sorted.len() % 2 == 0 {
            (sorted[sorted.len() / 2 - 1] + sorted[sorted.len() / 2]) / 2.0
        } else {
            sorted[sorted.len() / 2]
        };

        let p95_idx = (sorted.len() as f64 * 0.95).ceil() as usize - 1;
        let p95 = sorted[p95_idx.min(sorted.len() - 1)];

        let p99_idx = (sorted.len() as f64 * 0.99).ceil() as usize - 1;
        let p99 = sorted[p99_idx.min(sorted.len() - 1)];

        let min = sorted[0];
        let max = sorted[sorted.len() - 1];

        let variance = iterations
            .iter()
            .map(|x| (x - mean).powi(2))
            .sum::<f64>()
            / iterations.len() as f64;
        let std_dev = variance.sqrt();

        Self {
            query_id,
            iterations,
            mean,
            median,
            p95,
            p99,
            min,
            max,
            std_dev,
        }
    }
}

#[derive(Debug, Serialize, Deserialize)]
pub struct BenchmarkComparison {
    pub baseline: String,
    pub comparison: String,
    pub queries: Vec<QueryComparison>,
    pub summary: ComparisonSummary,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct QueryComparison {
    pub query_id: String,
    pub baseline: QueryMetrics,
    pub comparison: QueryMetrics,
    pub change_pct: f64,
    pub change_abs: f64,
    pub status: ChangeStatus,
}

#[derive(Debug, Serialize, Deserialize, Clone, Copy)]
pub enum ChangeStatus {
    Faster,
    Slower,
    NoChange,
    Regressed, // Significant slowdown (>10%)
    Improved,  // Significant improvement (>10%)
}

#[derive(Debug, Serialize, Deserialize)]
pub struct ComparisonSummary {
    pub total_queries: usize,
    pub faster: usize,
    pub slower: usize,
    pub no_change: usize,
    pub improved: usize,
    pub regressed: usize,
    pub total_time_baseline: f64,
    pub total_time_comparison: f64,
    pub avg_time_baseline: f64,
    pub avg_time_comparison: f64,
    pub overall_change_pct: f64,
}

impl BenchmarkComparison {
    pub fn compare(
        baseline_path: impl AsRef<Path>,
        comparison_path: impl AsRef<Path>,
        baseline_name: String,
        comparison_name: String,
    ) -> Result<Self, Box<dyn std::error::Error>> {
        let baseline_data: serde_json::Value =
            serde_json::from_str(&std::fs::read_to_string(baseline_path)?)?;
        let comparison_data: serde_json::Value =
            serde_json::from_str(&std::fs::read_to_string(comparison_path)?)?;

        let baseline_queries = baseline_data["queries"]
            .as_array()
            .ok_or("Invalid baseline format")?;
        let comparison_queries = comparison_data["queries"]
            .as_array()
            .ok_or("Invalid comparison format")?;

        let mut query_comparisons = Vec::new();
        let mut baseline_map = HashMap::new();
        let mut comparison_map = HashMap::new();

        for query in baseline_queries {
            let query_id = query["query"].as_str().unwrap().to_string();
            let iterations: Vec<f64> = query["iterations"]
                .as_array()
                .unwrap()
                .iter()
                .map(|i| i["elapsed"].as_f64().unwrap())
                .collect();
            let metrics = QueryMetrics::from_iterations(query_id.clone(), iterations);
            baseline_map.insert(query_id.clone(), metrics);
        }

        for query in comparison_queries {
            let query_id = query["query"].as_str().unwrap().to_string();
            let iterations: Vec<f64> = query["iterations"]
                .as_array()
                .unwrap()
                .iter()
                .map(|i| i["elapsed"].as_f64().unwrap())
                .collect();
            let metrics = QueryMetrics::from_iterations(query_id.clone(), iterations);
            comparison_map.insert(query_id.clone(), metrics);
        }

        let all_query_ids: Vec<String> = baseline_map
            .keys()
            .chain(comparison_map.keys())
            .cloned()
            .collect::<std::collections::HashSet<_>>()
            .into_iter()
            .collect();

        let mut total_baseline = 0.0;
        let mut total_comparison = 0.0;
        let mut faster = 0;
        let mut slower = 0;
        let mut no_change = 0;
        let mut improved = 0;
        let mut regressed = 0;

        for query_id in &all_query_ids {
            let baseline = baseline_map.get(query_id);
            let comparison = comparison_map.get(query_id);

            if let (Some(b), Some(c)) = (baseline, comparison) {
                let change_abs = c.mean - b.mean;
                let change_pct = (change_abs / b.mean) * 100.0;

                let status = if change_pct.abs() < 1.0 {
                    no_change += 1;
                    ChangeStatus::NoChange
                } else if change_pct < -10.0 {
                    improved += 1;
                    faster += 1;
                    ChangeStatus::Improved
                } else if change_pct > 10.0 {
                    regressed += 1;
                    slower += 1;
                    ChangeStatus::Regressed
                } else if change_pct < 0.0 {
                    faster += 1;
                    ChangeStatus::Faster
                } else {
                    slower += 1;
                    ChangeStatus::Slower
                };

                total_baseline += b.mean;
                total_comparison += c.mean;

                query_comparisons.push(QueryComparison {
                    query_id: query_id.clone(),
                    baseline: b.clone(),
                    comparison: c.clone(),
                    change_pct,
                    change_abs,
                    status,
                });
            }
        }

        let total_queries = query_comparisons.len();
        let avg_baseline = if total_queries > 0 {
            total_baseline / total_queries as f64
        } else {
            0.0
        };
        let avg_comparison = if total_queries > 0 {
            total_comparison / total_queries as f64
        } else {
            0.0
        };
        let overall_change_pct = if avg_baseline > 0.0 {
            ((avg_comparison - avg_baseline) / avg_baseline) * 100.0
        } else {
            0.0
        };

        Ok(Self {
            baseline: baseline_name,
            comparison: comparison_name,
            queries: query_comparisons,
            summary: ComparisonSummary {
                total_queries,
                faster,
                slower,
                no_change,
                improved,
                regressed,
                total_time_baseline: total_baseline,
                total_time_comparison: total_comparison,
                avg_time_baseline: avg_baseline,
                avg_time_comparison: avg_comparison,
                overall_change_pct,
            },
        })
    }

    pub fn to_markdown(&self) -> String {
        let mut md = String::new();
        md.push_str(&format!(
            "# Benchmark Comparison: {} vs {}\n\n",
            self.baseline, self.comparison
        ));

        // Summary
        md.push_str("## Summary\n\n");
        md.push_str(&format!(
            "- **Total Queries**: {}\n",
            self.summary.total_queries
        ));
        md.push_str(&format!(
            "- **Faster**: {} ({:.1}%)\n",
            self.summary.faster,
            (self.summary.faster as f64 / self.summary.total_queries as f64) * 100.0
        ));
        md.push_str(&format!(
            "- **Slower**: {} ({:.1}%)\n",
            self.summary.slower,
            (self.summary.slower as f64 / self.summary.total_queries as f64) * 100.0
        ));
        md.push_str(&format!(
            "- **No Change**: {} ({:.1}%)\n",
            self.summary.no_change,
            (self.summary.no_change as f64 / self.summary.total_queries as f64) * 100.0
        ));
        md.push_str(&format!(
            "- **Improved (>10%)**: {}\n",
            self.summary.improved
        ));
        md.push_str(&format!("- **Regressed (>10%)**: {}\n\n", self.summary.regressed));

        md.push_str(&format!(
            "**Overall Performance**: {:.2}% change\n\n",
            self.summary.overall_change_pct
        ));

        // Detailed table
        md.push_str("## Detailed Results\n\n");
        md.push_str("| Query | Baseline (ms) | Comparison (ms) | Change | Status |\n");
        md.push_str("|-------|---------------|-----------------|--------|--------|\n");

        for qc in &self.queries {
            let status_str = match qc.status {
                ChangeStatus::Improved => "✅ Improved",
                ChangeStatus::Regressed => "❌ Regressed",
                ChangeStatus::Faster => "⬇️ Faster",
                ChangeStatus::Slower => "⬆️ Slower",
                ChangeStatus::NoChange => "➡️ No Change",
            };

            md.push_str(&format!(
                "| {} | {:.2} | {:.2} | {:.2}% | {} |\n",
                qc.query_id, qc.baseline.mean, qc.comparison.mean, qc.change_pct, status_str
            ));
        }

        md
    }
}


