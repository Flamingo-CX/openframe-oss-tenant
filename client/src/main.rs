use anyhow::Result;
use clap::{Parser, Subcommand};
use openframe::platform::permissions::{Capability, PermissionUtils};
use openframe::{service::Service, Client};
use std::process;
use tokio::runtime::Runtime;
use tracing::{error, info, warn};

#[derive(Parser)]
#[command(author, version, about, long_about = None)]
struct Cli {
    #[command(subcommand)]
    command: Option<Commands>,
}

#[derive(Subcommand)]
enum Commands {
    /// Install the OpenFrame client as a system service
    Install,
    /// Uninstall the OpenFrame client service
    Uninstall,
    /// Run the OpenFrame client directly (not as a service)
    Run,
    /// Run as a service (used by service manager)
    #[command(hide = true)] // Hide from help text as this is only for internal use
    RunAsService,
    /// Check if the current process has the required permissions
    #[command(hide = true)] // Hide from help text as this is primarily for internal use
    CheckPermissions,
}

fn main() -> Result<()> {
    // Initialize logging first
    if let Err(e) = openframe::logging::init(None, None) {
        eprintln!("Failed to initialize logging: {}", e);
        process::exit(1);
    }

    // Add explicit startup log entry to verify logging is working
    info!("OpenFrame agent starting up");

    // Check if running with admin privileges
    let is_admin = PermissionUtils::is_admin();
    info!("Running with admin privileges: {}", is_admin);

    let cli = Cli::parse();
    let rt = Runtime::new()?;

    match cli.command {
        Some(Commands::Install) => {
            info!("Running install command");
            // Check for admin privileges - this is required for installation
            if !is_admin {
                error!("Admin/root privileges are required for service installation");
                // We could attempt automatic elevation here, but for now we'll just exit with an error
                eprintln!("Please run the installation with administrator/root privileges");
                process::exit(1);
            }

            rt.block_on(async {
                match Service::install().await {
                    Ok(_) => {
                        info!("OpenFrame client service installed successfully");
                        process::exit(0);
                    }
                    Err(e) => {
                        error!("Failed to install OpenFrame client service: {:#}", e);
                        process::exit(1);
                    }
                }
            });
        }
        Some(Commands::Uninstall) => {
            info!("Running uninstall command");
            // Check for admin privileges - this is required for uninstallation
            if !is_admin {
                error!("Admin/root privileges are required for service uninstallation");
                // We could attempt automatic elevation here, but for now we'll just exit with an error
                eprintln!("Please run the uninstallation with administrator/root privileges");
                process::exit(1);
            }

            rt.block_on(async {
                match Service::uninstall().await {
                    Ok(_) => {
                        info!("OpenFrame client service uninstalled successfully");
                        process::exit(0);
                    }
                    Err(e) => {
                        error!("Failed to uninstall OpenFrame client service: {:#}", e);
                        process::exit(1);
                    }
                }
            });
        }
        Some(Commands::Run) => {
            info!("Running in direct mode (without service wrapper)");

            // For direct mode, check capabilities but don't require admin
            // Just warn if we don't have certain capabilities
            check_capabilities_and_warn();

            // Run directly without service wrapper
            match Client::new() {
                Ok(client) => {
                    info!("Starting OpenFrame client in direct mode");
                    if let Err(e) = rt.block_on(client.start()) {
                        error!("Client failed: {:#}", e);
                        process::exit(1);
                    }
                }
                Err(e) => {
                    error!("Failed to initialize client: {:#}", e);
                    process::exit(1);
                }
            }
        }
        Some(Commands::RunAsService) => {
            info!("Running as service (called by service manager)");
            // When running as a service, we should already have the necessary permissions
            // But we'll still check and log any issues
            check_capabilities_and_warn();

            // This command is used when started by the service manager
            if let Err(e) = rt.block_on(Service::run_as_service()) {
                error!("Service failed: {:#}", e);
                process::exit(1);
            }
        }
        Some(Commands::CheckPermissions) => {
            // This command is used to check if we have the necessary permissions
            // Useful for diagnostics and troubleshooting
            println!("Admin privileges: {}", is_admin);
            println!(
                "Manage services capability: {}",
                PermissionUtils::has_capability(Capability::ManageServices)
            );
            println!(
                "Write system directories capability: {}",
                PermissionUtils::has_capability(Capability::WriteSystemDirectories)
            );
            println!(
                "Read system logs capability: {}",
                PermissionUtils::has_capability(Capability::ReadSystemLogs)
            );
            println!(
                "Write system logs capability: {}",
                PermissionUtils::has_capability(Capability::WriteSystemLogs)
            );

            if is_admin {
                process::exit(0);
            } else {
                process::exit(1);
            }
        }
        None => {
            info!("No command specified, running as service (legacy mode)");
            // Run as service by default for backward compatibility
            if let Err(e) = rt.block_on(Service::run_as_service()) {
                error!("Service failed: {:#}", e);
                process::exit(1);
            }
        }
    }

    // Add explicit shutdown log entry to verify logging is still working
    info!("OpenFrame agent shutting down");

    Ok(())
}

/// Check for capabilities and log warnings if we don't have them
fn check_capabilities_and_warn() {
    if !PermissionUtils::has_capability(Capability::ManageServices) {
        warn!("Process doesn't have capability to manage services");
    }

    if !PermissionUtils::has_capability(Capability::WriteSystemDirectories) {
        warn!("Process doesn't have capability to write to system directories");
    }

    if !PermissionUtils::has_capability(Capability::ReadSystemLogs) {
        warn!("Process doesn't have capability to read system logs");
    }

    if !PermissionUtils::has_capability(Capability::WriteSystemLogs) {
        warn!("Process doesn't have capability to write system logs");
    }
}
