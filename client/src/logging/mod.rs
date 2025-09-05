/// Cross-platform logging system
///
/// This module implements a robust, cross-platform logging system using the tracing
/// library. Key features:
///
/// - Platform-specific log paths (Windows, macOS, Linux)
/// - Automatic log rotation and compression
/// - Configurable output format (text one-liners by default, optional JSON)
/// - Metrics collection via the metrics submodule
/// - Fallback manual logging when tracing fails
///
/// The logging system is initialized via the `init()` function and should be
/// called early in the application lifecycle.
pub mod metrics;
pub mod shipping;

use crate::platform::{DirectoryError, DirectoryManager};
use chrono::Utc;
use flate2::write::GzEncoder;
use flate2::Compression;
use metrics::{MetricValue, MetricsLayer, MetricsStore};
use serde::Serialize;
use shipping::LogShipper;
use std::collections::HashMap;
use std::fs;
use std::io::{self, Read, Write};
use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::sync::Mutex;
use std::time::{SystemTime, UNIX_EPOCH};
use tokio::sync::RwLock;
use tracing::{error, info, warn, Event, Level, Metadata, Subscriber};
use tracing_appender::rolling::{RollingFileAppender, Rotation};
use tracing_subscriber::{
    fmt::{self},
    layer::SubscriberExt,
    prelude::*,
    EnvFilter, Layer, Registry,
};

// Add non-blocking file writer guard to keep file logging alive
use tracing_appender::non_blocking::{self, WorkerGuard};

#[derive(Debug, Serialize)]
struct LogEntry {
    timestamp: String,
    level: String,
    target: String,
    module_path: Option<String>,
    file: Option<String>,
    line: Option<u32>,
    thread: String,
    message: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    error: Option<String>,
    #[serde(flatten)]
    context: serde_json::Value,
}

// Global file logger for direct writes when tracing fails
static LOG_FILE: std::sync::OnceLock<Arc<Mutex<Option<std::fs::File>>>> =
    std::sync::OnceLock::new();

// Keep non-blocking worker guard alive for file logging
static LOG_GUARD: std::sync::OnceLock<WorkerGuard> = std::sync::OnceLock::new();

// Get access to the global log file for manual writing
fn get_log_file() -> Arc<Mutex<Option<std::fs::File>>> {
    LOG_FILE.get().cloned().unwrap_or_else(|| {
        let file = Arc::new(Mutex::new(None));
        let _ = LOG_FILE.set(file.clone());
        file
    })
}

// Manual log function that writes directly to file - fallback when tracing fails
pub fn manual_log(level: &str, message: &str) {
    let timestamp = chrono::Utc::now().to_rfc3339_opts(chrono::SecondsFormat::Micros, true);
    // Write a simple single-line text log
    let line = format!("{} {} [{}] {}", timestamp, level, "manual", message);

    if let Ok(mut file_lock) = get_log_file().lock() {
        if let Some(ref mut file) = *file_lock {
            let _ = writeln!(file, "{}", line);
            let _ = file.flush();
        }
    }

    // Also write to stdout for capture by LaunchDaemon
    println!("{}", line);
}

pub struct JsonLayer {
    writer: Arc<std::sync::Mutex<std::fs::File>>,
    metrics: Arc<RwLock<MetricsStore>>,
}

impl JsonLayer {
    fn new(log_file: PathBuf) -> std::io::Result<Self> {
        let file = std::fs::OpenOptions::new()
            .create(true)
            .append(true)
            .open(log_file)?;

        Ok(Self {
            writer: Arc::new(std::sync::Mutex::new(file)),
            metrics: Arc::new(RwLock::new(MetricsStore::new())),
        })
    }
}

impl<S> Layer<S> for JsonLayer
where
    S: tracing::Subscriber,
{
    fn on_event(
        &self,
        event: &tracing::Event<'_>,
        _ctx: tracing_subscriber::layer::Context<'_, S>,
    ) {
        let mut visitor = JsonVisitor::default();
        event.record(&mut visitor);

        let level = event.metadata().level().to_string();

        // Update metrics based on log level
        let mut labels = HashMap::new();
        labels.insert("level".to_string(), level.clone());

        if let Ok(mut metrics) = self.metrics.try_write() {
            metrics.record_counter("log_count", 1, labels);
        }

        // Store message content in a separate variable before using it
        let message_content = visitor.message.clone().unwrap_or_default();

        let log_entry = LogEntry {
            timestamp: chrono::Utc::now().to_rfc3339_opts(chrono::SecondsFormat::Micros, true),
            level,
            target: event.metadata().target().to_string(),
            module_path: event.metadata().module_path().map(|s| s.to_string()),
            file: event.metadata().file().map(|s| s.to_string()),
            line: event.metadata().line(),
            thread: format!("{:?}", std::thread::current().id()),
            message: message_content.clone(),
            error: visitor.error,
            context: serde_json::Value::Object(visitor.fields),
        };

        if let Ok(json) = serde_json::to_string(&log_entry) {
            if let Ok(mut file) = self.writer.lock() {
                let _ = writeln!(file, "{}", json);
                let _ = file.flush();
            }

            // Also write to stdout for capture by LaunchDaemon
            // Use stdout for INFO or lower level logs, stderr for warnings/errors
            if event.metadata().level() <= &Level::INFO {
                println!("{}", json);
            } else {
                eprintln!("{}", json);
            }
        }

        // Don't write to manual log as backup - this was causing duplicate entries
        // manual_log(&event.metadata().level().to_string(), &message_content);
    }
}

#[derive(Default)]
struct JsonVisitor {
    message: Option<String>,
    error: Option<String>,
    fields: serde_json::Map<String, serde_json::Value>,
}

impl tracing::field::Visit for JsonVisitor {
    fn record_str(&mut self, field: &tracing::field::Field, value: &str) {
        if field.name() == "message" {
            self.message = Some(value.to_string());
            // Don't add message to fields as we'll use the dedicated message field
        } else if field.name() == "error" {
            self.error = Some(value.to_string());
        } else {
            self.fields.insert(field.name().to_string(), value.into());
        }
    }

    fn record_debug(&mut self, field: &tracing::field::Field, value: &dyn std::fmt::Debug) {
        if field.name() == "message" {
            // Capture message content when it comes through record_debug as well
            self.message = Some(format!("{:?}", value));
        } else {
            self.fields
                .insert(field.name().to_string(), format!("{:?}", value).into());
        }
    }
}

/// Initialize logging with optional endpoint and agent ID
pub fn init(log_endpoint: Option<String>, agent_id: Option<String>) -> std::io::Result<()> {
    // Check if logging is already initialized
    static INIT: std::sync::Once = std::sync::Once::new();
    let mut init_result = Ok(());

    INIT.call_once(|| {
        // Initialize directory manager based on environment
        let dir_manager = if std::env::var("OPENFRAME_DEV_MODE").is_ok() {
            // In development mode, use user logs directory to avoid permission issues
            eprintln!("Running in development mode, using user logs directory");
            DirectoryManager::for_development()
        } else {
            // In normal mode, use system logs directory
            DirectoryManager::new()
        };

        if let Err(e) = dir_manager.perform_health_check() {
            init_result = Err(std::io::Error::new(
                std::io::ErrorKind::Other,
                format!("Failed to initialize logging directories: {}", e),
            ));
            eprintln!("ERROR: Failed to initialize logging directories: {}", e);
            return;
        }

        // Get the log file path from the directory manager
        let log_file_path = get_log_file_path(&dir_manager);

        eprintln!("Initializing logging to {}", log_file_path.display());

        // Initialize the log file for manual writing when tracing fails
        match std::fs::OpenOptions::new()
            .create(true)
            .append(true)
            .open(&log_file_path)
        {
            Ok(file) => {
                let _ = LOG_FILE.set(Arc::new(Mutex::new(Some(file))));
            }
            Err(e) => {
                eprintln!("ERROR: Failed to open log file: {}", e);
                init_result = Err(e);
                return;
            }
        }

        // Try to compress old log files in a background thread
        let dir_manager_clone = dir_manager.clone();
        std::thread::spawn(move || {
            // This runs in a background thread so we just log any errors
            loop {
                if let Err(e) = compress_old_logs(&dir_manager_clone) {
                    log::error!("Error compressing old logs: {:#}", e);
                }
                // Check for files to compress every hour
                std::thread::sleep(std::time::Duration::from_secs(3600));
            }
        });

        // Create metrics layer and store
        let (metrics_layer, metrics_store) = metrics::MetricsLayer::new();

        // Set global metrics store for later access
        let _ = METRICS_STORE.set(Arc::clone(&metrics_store));

        // Set up the full tracing subscriber
        let env_filter =
            EnvFilter::try_from_default_env().unwrap_or_else(|_| EnvFilter::new("info"));

        // Decide output format: default to text (one-liners). Set OPENFRAME_LOG_FORMAT=json to use JSON
        let format = std::env::var("OPENFRAME_LOG_FORMAT").unwrap_or_else(|_| "text".into());

        if format.eq_ignore_ascii_case("json") {
            // JSON structured logging (legacy behavior)
            // Create a JSON layer for structured logging
            let json_layer = match JsonLayer::new(log_file_path.clone()) {
                Ok(layer) => layer,
                Err(e) => {
                    eprintln!("ERROR: Failed to create JSON logging layer: {}", e);
                    init_result = Err(e);
                    return;
                }
            };

            let subscriber = Registry::default()
                .with(env_filter)
                .with(json_layer)
                .with(metrics_layer);

            if let Err(e) = tracing::subscriber::set_global_default(subscriber) {
                eprintln!("ERROR: Failed to set global tracing subscriber: {}", e);
                init_result = Err(std::io::Error::new(
                    std::io::ErrorKind::Other,
                    format!("Failed to set global tracing subscriber: {}", e),
                ));
                return;
            }
        } else {
            // Text one-liner logging to stdout and file
            // Non-blocking file writer
            let file = std::fs::OpenOptions::new()
                .create(true)
                .append(true)
                .open(&log_file_path)
                .expect("failed to open log file for text logging");
            let (file_writer, guard) = non_blocking::NonBlockingBuilder::default()
                .lossy(false)
                .thread_name("of-log-writer")
                .finish(file);
            let _ = LOG_GUARD.set(guard); // keep guard alive

            // stdout layer (compact, single-line)
            let stdout_layer = fmt::layer()
                .with_target(true)
                .with_level(true)
                .compact()
                .with_ansi(false);

            // file layer (compact, single-line)
            let file_layer = fmt::layer()
                .with_target(true)
                .with_level(true)
                .compact()
                .with_ansi(false)
                .with_writer(file_writer);

            let subscriber = Registry::default()
                .with(env_filter)
                .with(stdout_layer)
                .with(file_layer)
                .with(metrics_layer);

            if let Err(e) = tracing::subscriber::set_global_default(subscriber) {
                eprintln!("ERROR: Failed to set global tracing subscriber: {}", e);
                init_result = Err(std::io::Error::new(
                    std::io::ErrorKind::Other,
                    format!("Failed to set global tracing subscriber: {}", e),
                ));
                return;
            }
        }

        // Force an initial log entry with explicit info level to ensure logging is working
        tracing::info!("OpenFrame logging system initialized");
        manual_log("INFO", "Logging system initialized");

        // Initialize log shipping if endpoint is provided
        if let Some(endpoint) = log_endpoint {
            if let Some(agent) = agent_id.clone() {
                // Create a log shipper instance
                let shipper = shipping::LogShipper::new(endpoint.clone(), agent.clone());
                // No need to do anything else, shipper already starts itself with its background task
                tracing::info!("Log shipping initialized to endpoint: {}", endpoint);
            }
        }
    });

    init_result
}

// Static storage for metrics
static METRICS_STORE: std::sync::OnceLock<Arc<RwLock<MetricsStore>>> = std::sync::OnceLock::new();

/// Get access to the metrics store
pub fn get_metrics_store() -> Option<Arc<RwLock<MetricsStore>>> {
    METRICS_STORE.get().cloned()
}

/// Try to compress old log files
fn compress_old_logs(dir_manager: &DirectoryManager) -> io::Result<()> {
    // If we fail to open the log dir that's okay, just return
    let log_dir = match dir_manager.logs_dir().canonicalize() {
        Ok(dir) => dir,
        Err(e) => {
            log::error!("Failed to get logs directory: {:#}", e);
            return Ok(());
        }
    };

    // Find log files older than 1 day and compress them
    let mut entries = match fs::read_dir(log_dir) {
        Ok(entries) => entries,
        Err(e) => {
            log::error!("Failed to read logs directory: {:#}", e);
            return Ok(());
        }
    };

    let one_day_ago = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_secs() as i64
        - 86400;

    while let Some(entry) = entries.next() {
        let entry = match entry {
            Ok(entry) => entry,
            Err(e) => {
                log::error!(
                    "Failed to read directory entry: {}",
                    e
                );
                continue;
            }
        };

        let metadata = match entry.metadata() {
            Ok(metadata) => metadata,
            Err(e) => {
                log::error!(
                    "Failed to get metadata for {}: {}",
                    entry.path().display(),
                    e
                );
                continue;
            }
        };

        // Skip directories and non-log files
        if metadata.is_dir() || !entry.file_name().to_string_lossy().ends_with(".log") {
            continue;
        }

        // Skip if already compressed
        if entry.file_name().to_string_lossy().ends_with(".gz") {
            continue;
        }

        // Skip if modified in the last day
        let modified = match metadata.modified() {
            Ok(time) => match time.duration_since(UNIX_EPOCH) {
                Ok(duration) => duration.as_secs() as i64,
                Err(e) => {
                    log::error!(
                        "Failed to get modification time for {}: {}",
                        entry.path().display(),
                        e
                    );
                    continue;
                }
            },
            Err(e) => {
                log::error!(
                    "Failed to get modification time for {}: {}",
                    entry.path().display(),
                    e
                );
                continue;
            }
        };

        if modified > one_day_ago {
            continue;
        }

        // Compress the file
        if let Err(e) = compress_log_file(&entry.path()) {
            log::error!("Failed to compress {}: {}", entry.path().display(), e);
        } else {
            log::info!("Compressed old log file: {}", entry.path().display());
        }
    }

    Ok(())
}

/// Check if the given log file is the current day's log
fn is_current_log(filename: &str) -> bool {
    let today = chrono::Local::now().format("%Y-%m-%d").to_string();
    filename.contains(&today)
}

/// Compress a log file using gzip compression
fn compress_log_file(path: &PathBuf) -> std::io::Result<()> {
    let mut input = fs::File::open(path)?;
    let mut contents = Vec::new();
    input.read_to_end(&mut contents)?;

    let gz_path = path.with_extension("log.gz");
    let output = fs::File::create(&gz_path)?;
    let mut encoder = GzEncoder::new(output, Compression::default());
    encoder.write_all(&contents)?;
    encoder.finish()?;

    // Remove the original file after successful compression
    fs::remove_file(path)?;

    Ok(())
}

/// Get the current log file path
pub fn get_log_file_path(dir_manager: &DirectoryManager) -> PathBuf {
    // In development mode, use the user logs directory instead of system logs
    if std::env::var("OPENFRAME_DEV_MODE").is_ok() {
        dir_manager.user_logs_dir().join("openframe.log")
    } else {
        dir_manager.logs_dir().join("openframe.log")
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Read;
    use tempfile::tempdir;
    use tracing::{debug, error, info, trace, warn};

    #[test]
    fn test_structured_logging() -> std::io::Result<()> {
        let temp_dir = tempdir()?;
        let log_file = temp_dir.path().join("test.log");

        let json_layer = JsonLayer::new(log_file.clone())?;
        let subscriber = Registry::default().with(json_layer);

        tracing::subscriber::set_global_default(subscriber).expect("Failed to set subscriber");

        // Log messages with different levels and context
        error!(error = "test error", "Error message");
        warn!(user = "test_user", "Warning message");
        info!(request_id = 123, "Info message");
        debug!(status = "pending", "Debug message");
        trace!(correlation_id = "abc", "Trace message");

        // Read and verify log file contents
        let mut file = std::fs::File::open(log_file)?;
        let mut contents = String::new();
        file.read_to_string(&mut contents)?;

        // Verify each log level appears in the file
        assert!(contents.contains(r#""level":"ERROR"#));
        assert!(contents.contains(r#""level":"WARN"#));
        assert!(contents.contains(r#""level":"INFO"#));
        assert!(contents.contains(r#""level":"DEBUG"#));
        assert!(contents.contains(r#""level":"TRACE"#));

        // Verify custom fields are included
        assert!(contents.contains(r#""error":"test error"#));
        assert!(contents.contains(r#""user":"test_user"#));
        assert!(contents.contains(r#""request_id":"123"#));
        assert!(contents.contains(r#""status":"pending"#));
        assert!(contents.contains(r#""correlation_id":"abc"#));

        Ok(())
    }
}
