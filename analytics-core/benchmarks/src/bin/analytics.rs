use datafusion::error::Result;
use structopt::StructOpt;
use analytics_benchmarks::analytics;

#[derive(Debug, StructOpt)]
#[structopt(name = "ANALYTICS", about = "ANALYTICS Benchmarks.")]
enum AnalyticsOpt {
    Benchmark(analytics::run::RunOpt),
}

#[tokio::main]
async fn main() -> Result<()> {
    env_logger::init();
    match AnalyticsOpt::from_args() {
        AnalyticsOpt::Benchmark(opt) => opt.run().await,
    }
}
