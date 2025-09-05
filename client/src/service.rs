use anyhow::{Context, Result};
use std::path::PathBuf;
use tokio::runtime::Runtime;
use tokio::time::{interval, Duration};
use tracing::{error, info, warn};

use crate::platform::permissions::{Capability, PermissionUtils};
use crate::service_adapter::{CrossPlatformServiceManager, ServiceConfig};
use crate::{logging, platform::DirectoryManager, Client};

const SERVICE_NAME: &str = "client";
const DISPLAY_NAME: &str = "OpenFrame Client Service";
const DESCRIPTION: &str = "OpenFrame client service for remote management and monitoring";

pub struct Service;

impl Service {
    pub fn new() -> Self {
        Self
    }

    /// Install the service on the current platform
    pub async fn install() -> Result<()> {
        // Check if we have admin privileges
        if !PermissionUtils::is_admin() {
            error!("Service installation requires admin/root privileges");
            return Err(anyhow::anyhow!(
                "Admin/root privileges required for service installation"
            ));
        }

        // Common code for all platforms
        info!("Installing OpenFrame service");
        let dir_manager = DirectoryManager::new();
        dir_manager
            .perform_health_check()
            .map_err(|e| anyhow::anyhow!("Directory health check failed: {}", e))?;

        // Get the current executable path
        let exec_path = std::env::current_exe().context("Failed to get current executable path")?;

        // Determine platform-specific user and group values
        let (user_name, group_name) = match std::env::consts::OS {
            "windows" => (Some("LocalSystem".to_string()), None),
            "macos" => (Some("root".to_string()), Some("wheel".to_string())),
            "linux" => (Some("root".to_string()), Some("root".to_string())),
            _ => (None, None),
        };

        // Create a full configuration for the service with all enhanced options
        let config = ServiceConfig {
            name: SERVICE_NAME.to_string(),
            display_name: DISPLAY_NAME.to_string(),
            description: DESCRIPTION.to_string(),
            exec_path,
            run_at_load: true,
            keep_alive: true,
            restart_on_crash: true,
            restart_throttle_seconds: 10,
            working_directory: Some(dir_manager.app_support_dir().to_path_buf()),
            stdout_path: Some(dir_manager.logs_dir().join("daemon_output.log")),
            stderr_path: Some(dir_manager.logs_dir().join("daemon_error.log")),
            user_name,
            group_name,
            file_limit: Some(4096),
            exit_timeout_seconds: Some(10),
            is_interactive: true,
            ..ServiceConfig::default()
        };

        // Create the service manager with our enhanced configuration
        let service = CrossPlatformServiceManager::with_config(config);

        // Call the cross-platform service manager to install
        service.install().context("Failed to install service")?;

        info!("OpenFrame service installed successfully");
        Ok(())
    }

    /// Uninstall the service on the current platform
    pub async fn uninstall() -> Result<()> {
        // Check if we have admin privileges
        if !PermissionUtils::is_admin() {
            error!("Service uninstallation requires admin/root privileges");
            return Err(anyhow::anyhow!(
                "Admin/root privileges required for service uninstallation"
            ));
        }

        // Common code for all platforms
        info!("Uninstalling OpenFrame service");

        // Get the current executable path
        let exec_path = std::env::current_exe().context("Failed to get current executable path")?;

        // Create the service manager
        let config = ServiceConfig {
            name: SERVICE_NAME.to_string(),
            display_name: DISPLAY_NAME.to_string(),
            description: DESCRIPTION.to_string(),
            exec_path,
            ..ServiceConfig::default()
        };

        let service = CrossPlatformServiceManager::with_config(config);

        // Call the cross-platform service manager to uninstall
        service.uninstall().context("Failed to uninstall service")?;

        // Clean up common directories
        let dir_manager = DirectoryManager::new();
        let _ = std::fs::remove_dir_all(dir_manager.app_support_dir());
        let _ = std::fs::remove_dir_all(dir_manager.logs_dir());

        info!("OpenFrame service uninstalled successfully");
        Ok(())
    }

    /// Run the service core logic
    pub async fn run() -> Result<()> {
        // Common code for all platforms
        info!("Starting OpenFrame service core");

        // Initialize directory manager based on environment
        let dir_manager = if std::env::var("OPENFRAME_DEV_MODE").is_ok() {
            info!("Service running in development mode, using user directories");
            DirectoryManager::for_development()
        } else {
            DirectoryManager::new()
        };

        // Check if we have capability to access required resources
        let _can_read_logs = PermissionUtils::has_capability(Capability::ReadSystemLogs);
        let can_write_logs = PermissionUtils::has_capability(Capability::WriteSystemLogs);

        if !can_write_logs {
            warn!("Process doesn't have privileges to write to system logs");
        }

        // Perform health check before starting
        if let Err(e) = dir_manager.perform_health_check() {
            error!("Directory health check failed: {:#}", e);
            return Err(e.into());
        }

        // Initialize the client
        let client = Client::new()?;


        // Start the client
        client.start().await
    }

    /// Run as a service on the current platform
    pub async fn run_as_service() -> Result<()> {
        // Check if we have necessary capabilities for running as a service
        if !PermissionUtils::has_capability(Capability::ManageServices)
            && !PermissionUtils::has_capability(Capability::WriteSystemDirectories)
        {
            // Log warning but continue - we might be running as a specialized service account
            warn!("Process doesn't have full administrative privileges");
        }

        // Log which platform we're running on
        let platform = match std::env::consts::OS {
            "windows" => "Windows Service",
            "macos" => "macOS LaunchDaemon",
            "linux" => "Linux systemd",
            _ => "Unknown platform",
        };

        info!("Running as {} service", platform);

        // For all platforms, run the main service function
        Self::run().await
    }
}
