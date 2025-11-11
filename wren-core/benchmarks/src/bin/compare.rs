//! Benchmark comparison tool
use std::path::PathBuf;
use structopt::StructOpt;
use wren_benchmarks::util::comparison::BenchmarkComparison;

#[derive(StructOpt, Debug)]
#[structopt(name = "compare", about = "Compare benchmark results")]
struct Opt {
    /// Baseline benchmark result file
    #[structopt(short, long)]
    baseline: PathBuf,

    /// Comparison benchmark result file
    #[structopt(short, long)]
    comparison: PathBuf,

    /// Baseline name (e.g., "main", "before")
    #[structopt(short = "b", long, default_value = "baseline")]
    baseline_name: String,

    /// Comparison name (e.g., "feature-branch", "after")
    #[structopt(short = "c", long, default_value = "comparison")]
    comparison_name: String,

    /// Output format: json, markdown, or table
    #[structopt(short, long, default_value = "table")]
    format: String,

    /// Output file (optional, prints to stdout if not specified)
    #[structopt(short, long)]
    output: Option<PathBuf>,
}

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let opt = Opt::from_args();

    let comparison = BenchmarkComparison::compare(
        &opt.baseline,
        &opt.comparison,
        opt.baseline_name,
        opt.comparison_name,
    )?;

    let output = match opt.format.as_str() {
        "json" => serde_json::to_string_pretty(&comparison)?,
        "markdown" => comparison.to_markdown(),
        "table" => format_comparison_table(&comparison),
        _ => return Err("Invalid format. Use: json, markdown, or table".into()),
    };

    if let Some(output_path) = opt.output {
        std::fs::write(output_path, output)?;
        println!("Comparison written to file");
    } else {
        println!("{}", output);
    }

    Ok(())
}

fn format_comparison_table(comparison: &BenchmarkComparison) -> String {
    use comfy_table::{Cell, Table};

    let mut table = Table::new();
    table.set_header(vec![
        "Query",
        "Baseline (ms)",
        "Comparison (ms)",
        "Change %",
        "Status",
    ]);

    for qc in &comparison.queries {
        let status = match qc.status {
            wren_benchmarks::util::comparison::ChangeStatus::Improved => "✅ Improved",
            wren_benchmarks::util::comparison::ChangeStatus::Regressed => "❌ Regressed",
            wren_benchmarks::util::comparison::ChangeStatus::Faster => "⬇️ Faster",
            wren_benchmarks::util::comparison::ChangeStatus::Slower => "⬆️ Slower",
            wren_benchmarks::util::comparison::ChangeStatus::NoChange => "➡️ No Change",
        };

        table.add_row(vec![
            Cell::new(&qc.query_id),
            Cell::new(format!("{:.2}", qc.baseline.mean)),
            Cell::new(format!("{:.2}", qc.comparison.mean)),
            Cell::new(format!("{:.2}%", qc.change_pct)),
            Cell::new(status),
        ]);
    }

    let mut result = format!(
        "Comparing {} vs {}\n",
        comparison.baseline, comparison.comparison
    );
    result.push_str("=".repeat(80).as_str());
    result.push('\n');
    result.push_str(&table.to_string());
    result.push('\n');
    result.push_str(&format!(
        "Summary: {} queries, {} faster, {} slower, {} improved, {} regressed\n",
        comparison.summary.total_queries,
        comparison.summary.faster,
        comparison.summary.slower,
        comparison.summary.improved,
        comparison.summary.regressed
    ));
    result.push_str(&format!(
        "Overall: {:.2}% change\n",
        comparison.summary.overall_change_pct
    ));

    result
}


