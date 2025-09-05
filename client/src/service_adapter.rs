use anyhow::{Context, Result};
use plist::Dictionary;
use serde_json;
use service_manager::{
    ServiceInstallCtx, ServiceLabel, ServiceManager, ServiceStartCtx, ServiceStopCtx,
    ServiceUninstallCtx,
};
use std::collections::HashMap;
use std::ffi::OsString;
use std::path::{Path, PathBuf};
use std::str::FromStr;
use tracing::{debug, info, warn};

#[derive(Debug, Clone)]
pub struct ServiceConfig {
    // Basic service information
    pub name: String,
    pub display_name: String,
    pub description: String,
    pub exec_path: PathBuf,

    // Process control
    pub run_at_load: bool,
    pub keep_alive: bool,
    pub restart_on_crash: bool,
    pub restart_throttle_seconds: u32,

    // Environment
    pub working_directory: Option<PathBuf>,
    pub environment_vars: Vec<(String, String)>,

    // Logging
    pub stdout_path: Option<PathBuf>,
    pub stderr_path: Option<PathBuf>,

    // Identity
    pub user_name: Option<String>,
    pub group_name: Option<String>,

    // Resource control
    pub file_limit: Option<u32>,
    pub exit_timeout_seconds: Option<u32>,

    // Process type - maps to Interactive on macOS
    pub is_interactive: bool,
}

impl Default for ServiceConfig {
    fn default() -> Self {
        Self {
            name: "".to_string(),
            display_name: "".to_string(),
            description: "".to_string(),
            exec_path: PathBuf::new(),
            run_at_load: true,
            keep_alive: true,
            restart_on_crash: true,
            restart_throttle_seconds: 10,
            working_directory: None,
            environment_vars: vec![],
            stdout_path: None,
            stderr_path: None,
            user_name: None,
            group_name: None,
            file_limit: None,
            exit_timeout_seconds: None,
            is_interactive: true,
        }
    }
}

pub struct CrossPlatformServiceManager {
    pub config: ServiceConfig,
}

impl CrossPlatformServiceManager {
    pub fn new(name: &str, display_name: &str, description: &str, exec_path: PathBuf) -> Self {
        Self {
            config: ServiceConfig {
                name: name.to_string(),
                display_name: display_name.to_string(),
                description: description.to_string(),
                exec_path,
                ..ServiceConfig::default()
            },
        }
    }

    pub fn with_config(config: ServiceConfig) -> Self {
        Self { config }
    }

    pub fn set_stdout_path(&mut self, path: PathBuf) -> &mut Self {
        self.config.stdout_path = Some(path);
        self
    }

    pub fn set_stderr_path(&mut self, path: PathBuf) -> &mut Self {
        self.config.stderr_path = Some(path);
        self
    }

    pub fn set_working_directory(&mut self, path: PathBuf) -> &mut Self {
        self.config.working_directory = Some(path);
        self
    }

    pub fn set_user(&mut self, user: &str) -> &mut Self {
        self.config.user_name = Some(user.to_string());
        self
    }

    pub fn set_group(&mut self, group: &str) -> &mut Self {
        self.config.group_name = Some(group.to_string());
        self
    }

    pub fn set_restart_throttle(&mut self, seconds: u32) -> &mut Self {
        self.config.restart_throttle_seconds = seconds;
        self
    }

    pub fn set_file_limit(&mut self, limit: u32) -> &mut Self {
        self.config.file_limit = Some(limit);
        self
    }

    pub fn set_exit_timeout(&mut self, timeout: u32) -> &mut Self {
        self.config.exit_timeout_seconds = Some(timeout);
        self
    }

    pub fn install(&self) -> Result<()> {
        // Create a service label - this is what the service manager uses to identify the service
        let label = ServiceLabel::from_str(&format!(
            "com.openframe.{}",
            self.config.name.to_lowercase()
        ))
        .context("Failed to create service label")?;

        // Get the native service manager for this platform
        let manager = <dyn ServiceManager>::native()
            .context("Failed to detect native service management platform")?;

        // Set working directory to specified one or default
        let working_dir = self
            .config
            .working_directory
            .clone()
            .unwrap_or_else(|| self.get_app_support_dir());

        debug!(
            "Setting service working directory to: {}",
            working_dir.display()
        );

        // Get environment variables to pass to the service
        let environment = self.config.environment_vars.clone();

        // Create the installation context with full configuration
        let mut ctx = ServiceInstallCtx {
            label: label.clone(),
            program: self.config.exec_path.clone(),
            args: vec![OsString::from("run-as-service")],
            contents: None,
            username: self.get_service_username(),
            working_directory: Some(working_dir),
            environment: Some(environment),
            autostart: self.config.run_at_load,
            disable_restart_on_failure: !self.config.restart_on_crash,
        };

        // Apply platform-specific configuration
        self.apply_platform_specific_config(&mut ctx);

        // Create any needed directories for logs
        self.create_platform_specific_files()?;

        // Install the service using the platform's native service manager
        info!("Installing service with full configuration via CrossPlatformServiceManager");
        manager.install(ctx).context("Failed to install service")?;

        // After installation, start the service to ensure it's running
        self.start()?;

        Ok(())
    }

    pub fn uninstall(&self) -> Result<()> {
        // Create a service label
        let label = ServiceLabel::from_str(&format!(
            "com.openframe.{}",
            self.config.name.to_lowercase()
        ))
        .context("Failed to create service label")?;

        // Get the native service manager for this platform
        let manager = <dyn ServiceManager>::native()
            .context("Failed to detect native service management platform")?;

        // First try to stop the service if it's running
        let _ = self.stop();

        // Create the uninstallation context
        let ctx = ServiceUninstallCtx { label };

        // Remove platform-specific files
        self.remove_platform_specific_files();

        // Uninstall the service
        info!("Uninstalling service via CrossPlatformServiceManager");
        manager
            .uninstall(ctx)
            .context("Failed to uninstall service")?;

        Ok(())
    }

    pub fn start(&self) -> Result<()> {
        // Create a service label
        let label = ServiceLabel::from_str(&format!(
            "com.openframe.{}",
            self.config.name.to_lowercase()
        ))
        .context("Failed to create service label")?;

        // Get the native service manager for this platform
        let manager = <dyn ServiceManager>::native()
            .context("Failed to detect native service management platform")?;

        // Create the start context
        let ctx = ServiceStartCtx { label };

        // Start the service
        info!("Starting service via CrossPlatformServiceManager");
        manager.start(ctx).context("Failed to start service")?;

        Ok(())
    }

    pub fn stop(&self) -> Result<()> {
        // Create a service label
        let label = ServiceLabel::from_str(&format!(
            "com.openframe.{}",
            self.config.name.to_lowercase()
        ))
        .context("Failed to create service label")?;

        // Get the native service manager for this platform
        let manager = <dyn ServiceManager>::native()
            .context("Failed to detect native service management platform")?;

        // Create the stop context
        let ctx = ServiceStopCtx { label };

        // Stop the service
        info!("Stopping service via CrossPlatformServiceManager");
        manager.stop(ctx).context("Failed to stop service")?;

        Ok(())
    }

    // Platform-specific helpers

    fn add_platform_specific_env(&self, _environment: &mut Vec<(String, String)>) {
        #[cfg(target_os = "macos")]
        {
            // Add any macOS-specific environment variables
        }

        #[cfg(target_os = "windows")]
        {
            // Add any Windows-specific environment variables
        }

        #[cfg(all(unix, not(target_os = "macos")))]
        {
            // Add any Linux-specific environment variables
        }
    }

    fn apply_platform_specific_config(&self, ctx: &mut ServiceInstallCtx) {
        #[cfg(target_os = "macos")]
        {
            // For macOS, we need to create a proper plist using the plist crate
            let mut dict = Dictionary::new();

            // The Label is required and should match our service label
            dict.insert(
                "Label".into(),
                plist::Value::String(format!("com.openframe.{}", self.config.name.to_lowercase())),
            );

            // Program and arguments are required
            // Note: We omit Program and just use ProgramArguments according to the example
            let mut args = Vec::new();
            args.push(plist::Value::String(
                self.config.exec_path.to_string_lossy().to_string(),
            ));
            args.push(plist::Value::String("run-as-service".to_string()));
            dict.insert("ProgramArguments".into(), plist::Value::Array(args));

            // Basic service configuration
            dict.insert(
                "RunAtLoad".into(),
                plist::Value::Boolean(self.config.run_at_load),
            );

            // KeepAlive as a dictionary with SuccessfulExit and Crashed keys
            let mut keep_alive_dict = Dictionary::new();
            keep_alive_dict.insert("SuccessfulExit".into(), plist::Value::Boolean(false));
            keep_alive_dict.insert("Crashed".into(), plist::Value::Boolean(true));
            dict.insert(
                "KeepAlive".into(),
                plist::Value::Dictionary(keep_alive_dict),
            );

            // Add stdout/stderr paths if configured
            if let Some(stdout_path) = &self.config.stdout_path {
                dict.insert(
                    "StandardOutPath".into(),
                    plist::Value::String(stdout_path.to_string_lossy().to_string()),
                );
            }

            if let Some(stderr_path) = &self.config.stderr_path {
                dict.insert(
                    "StandardErrorPath".into(),
                    plist::Value::String(stderr_path.to_string_lossy().to_string()),
                );
            }

            // Add resource limits if configured
            if let Some(limit) = self.config.file_limit {
                let mut limits_dict = Dictionary::new();
                limits_dict.insert("NumberOfFiles".into(), plist::Value::Integer(limit.into()));
                dict.insert(
                    "SoftResourceLimits".into(),
                    plist::Value::Dictionary(limits_dict),
                );
            }

            // Add process type if specified
            if self.config.is_interactive {
                dict.insert(
                    "ProcessType".into(),
                    plist::Value::String("Interactive".to_string()),
                );
            }

            // Handle restart settings
            if self.config.restart_on_crash {
                dict.insert(
                    "ThrottleInterval".into(),
                    plist::Value::Integer(self.config.restart_throttle_seconds.into()),
                );
            }

            // Add ExitTimeOut if configured
            if let Some(timeout) = self.config.exit_timeout_seconds {
                dict.insert("ExitTimeOut".into(), plist::Value::Integer(timeout.into()));
            } else {
                // Add default ExitTimeOut of 10 seconds
                dict.insert("ExitTimeOut".into(), 10.into());
            }

            // Add AbandonProcessGroup
            dict.insert("AbandonProcessGroup".into(), plist::Value::Boolean(false));

            // Add specific user/group if provided
            if let Some(username) = &self.config.user_name {
                dict.insert("UserName".into(), plist::Value::String(username.clone()));
            }

            if let Some(group_name) = &self.config.group_name {
                dict.insert("GroupName".into(), plist::Value::String(group_name.clone()));
            }

            // Set working directory if provided
            if let Some(working_dir) = &self.config.working_directory {
                dict.insert(
                    "WorkingDirectory".into(),
                    plist::Value::String(working_dir.to_string_lossy().to_string()),
                );
            }

            // Convert plist dictionary to XML string
            if !dict.is_empty() {
                let value = plist::Value::Dictionary(dict);
                let mut xml = Vec::new();
                match plist::to_writer_xml(&mut xml, &value) {
                    Ok(_) => match String::from_utf8(xml) {
                        Ok(plist_xml) => {
                            debug!("Setting macOS plist configuration: {}", plist_xml);
                            ctx.contents = Some(plist_xml);
                        }
                        Err(e) => {
                            warn!("Failed to convert plist XML to string: {:#}", e);
                        }
                    },
                    Err(e) => {
                        warn!("Failed to serialize macOS service options to plist: {:#}", e);
                    }
                }
            }
        }

        #[cfg(target_os = "windows")]
        {
            // Windows-specific settings would be applied here
            // Windows service manager doesn't support all the same options
        }

        #[cfg(all(unix, not(target_os = "macos")))]
        {
            let mut advanced_options = HashMap::new();

            // Add stdout/stderr paths if configured
            if let Some(stdout_path) = &self.config.stdout_path {
                advanced_options.insert("StandardOutput", "file".to_string());
                advanced_options.insert(
                    "StandardOutputPath",
                    stdout_path.to_string_lossy().to_string(),
                );
            }

            if let Some(stderr_path) = &self.config.stderr_path {
                advanced_options.insert("StandardError", "file".to_string());
                advanced_options.insert(
                    "StandardErrorPath",
                    stderr_path.to_string_lossy().to_string(),
                );
            }

            // Add resource limits if configured
            if let Some(limit) = self.config.file_limit {
                advanced_options.insert("LimitNOFILE", limit.to_string());
            }

            // Handle restart settings
            if self.config.restart_on_crash {
                advanced_options.insert("Restart", "on-failure".to_string());
                advanced_options.insert(
                    "RestartSec",
                    self.config.restart_throttle_seconds.to_string(),
                );
            }

            // Serialize the advanced options to JSON Value and pass as contents
            if !advanced_options.is_empty() {
                match serde_json::to_string(&advanced_options) {
                    Ok(json_string) => {
                        debug!("Setting Linux advanced options: {}", json_string);
                        ctx.contents = Some(json_string);
                    }
                    Err(e) => {
                        warn!("Failed to serialize Linux service options: {:#}", e);
                    }
                }
            }
        }
    }

    fn create_platform_specific_files(&self) -> Result<()> {
        // Create any required directories for log files
        if let Some(stdout_path) = &self.config.stdout_path {
            if let Some(parent) = stdout_path.parent() {
                std::fs::create_dir_all(parent).ok();
            }
        }

        if let Some(stderr_path) = &self.config.stderr_path {
            if let Some(parent) = stderr_path.parent() {
                std::fs::create_dir_all(parent).ok();
            }
        }

        Ok(())
    }

    fn remove_platform_specific_files(&self) {
        // Nothing to do here - service-manager will handle cleanup
    }

    fn get_app_support_dir(&self) -> PathBuf {
        #[cfg(target_os = "macos")]
        {
            PathBuf::from("/Library/Application Support/OpenFrame")
        }

        #[cfg(target_os = "windows")]
        {
            let programdata =
                std::env::var("PROGRAMDATA").unwrap_or_else(|_| "C:\\ProgramData".to_string());
            PathBuf::from(programdata).join("OpenFrame")
        }

        #[cfg(all(unix, not(target_os = "macos")))]
        {
            PathBuf::from("/var/lib/openframe")
        }
    }

    fn get_service_username(&self) -> Option<String> {
        if let Some(username) = &self.config.user_name {
            return Some(username.clone());
        }

        #[cfg(target_os = "macos")]
        {
            Some("root".to_string())
        }

        #[cfg(target_os = "windows")]
        {
            Some("LocalSystem".to_string())
        }

        #[cfg(all(unix, not(target_os = "macos")))]
        {
            Some("root".to_string())
        }
    }
}
