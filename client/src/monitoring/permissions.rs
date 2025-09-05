use crate::logging::metrics::{MetricValue, MetricsStore};
use crate::platform::directories::DirectoryManager;
use std::collections::HashMap;
use std::sync::Arc;
use std::time::{Duration, SystemTime};
use tokio::sync::RwLock;
use tokio::time::interval;
use tracing::{error, info, warn};

pub struct PermissionMonitor {
    directory_manager: Arc<DirectoryManager>,
    check_interval: Duration,
    metrics: Arc<RwLock<MetricsStore>>,
}

impl PermissionMonitor {
    pub fn new(directory_manager: Arc<DirectoryManager>) -> Self {
        Self {
            directory_manager,
            check_interval: Duration::from_secs(3600), // Check every hour by default
            metrics: Arc::new(RwLock::new(MetricsStore::new())),
        }
    }

    pub fn with_interval(mut self, interval: Duration) -> Self {
        self.check_interval = interval;
        self
    }

    pub async fn start_monitoring(self) {
        info!(
            "Starting permission monitoring with interval: {:?}",
            self.check_interval
        );
        let mut interval = interval(self.check_interval);

        loop {
            interval.tick().await;
            self.perform_check().await;
        }
    }

    async fn perform_check(&self) {
        info!("Performing permission check");

        // Update last check timestamp
        if let Ok(mut metrics) = self.metrics.try_write() {
            metrics.record_gauge(
                "openframe_last_permission_check_timestamp",
                SystemTime::now()
                    .duration_since(SystemTime::UNIX_EPOCH)
                    .unwrap_or_default()
                    .as_secs() as f64,
                HashMap::new(),
            );
        }

        match self.directory_manager.perform_health_check() {
            Ok(()) => {
                info!("Permission check completed successfully");
            }
            Err(e) => {
                error!("Permission check failed: {:#}", e);

                // Increment error counter
                if let Ok(mut metrics) = self.metrics.try_write() {
                    metrics.record_counter("openframe_permission_errors_total", 1, HashMap::new());
                }

                // Attempt to fix permissions
                if let Err(fix_err) = self.directory_manager.fix_permissions() {
                    error!("Failed to fix permissions: {}", fix_err);
                } else {
                    info!("Successfully fixed permissions");
                    // Increment fix counter
                    if let Ok(mut metrics) = self.metrics.try_write() {
                        metrics.record_counter(
                            "openframe_permission_fixes_total",
                            1,
                            HashMap::new(),
                        );
                    }
                }
            }
        }
    }

    pub fn get_metrics(&self) -> Vec<(&str, f64)> {
        vec![
            ("openframe_permission_errors_total", 0.0),
            ("openframe_permission_fixes_total", 0.0),
            ("openframe_last_permission_check_timestamp", 0.0),
        ]
    }
}
