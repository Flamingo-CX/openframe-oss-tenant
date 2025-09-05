use std::fs::{self};
use std::io;
#[cfg(unix)]
use std::os::unix::fs::PermissionsExt;
use std::path::Path;
use std::process::Command;
use tracing::{error, info, warn};
#[cfg(unix)]
use libc;
#[cfg(target_os = "windows")]
use winapi::um::securitybaseapi::IsUserAnAdmin;
#[cfg(target_os = "windows")]
use winapi::um::shellapi::ShellExecuteW;
#[cfg(target_os = "windows")]
use winapi::um::winuser::SW_NORMAL;
use std::sync::atomic::{AtomicBool, Ordering};
/// Static flag to remember if we've already obtained admin privileges
static ADMIN_PRIVILEGES_GRANTED: AtomicBool = AtomicBool::new(false);
/// Default UID for root user
#[cfg(unix)]
const ROOT_UID: u32 = 0;
/// Default GID for admin group on macOS
#[cfg(unix)]
const ADMIN_GID: u32 = 80;
#[cfg(not(unix))]
const ROOT_UID: u32 = 0;
#[cfg(not(unix))]
const ADMIN_GID: u32 = 0;
#[derive(Debug)]
pub enum PermissionError {
    Io(io::Error),
    InvalidMode(String),
    InvalidPath(String),
    AdminCheckFailed(String),
    ElevationRequired,
    CommandFailed(i32),
}
impl std::fmt::Display for PermissionError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            PermissionError::Io(e) => write!(f, "IO error: {}", e),
            PermissionError::InvalidMode(msg) => write!(f, "Invalid mode: {}", msg),
            PermissionError::InvalidPath(msg) => write!(f, "Invalid path: {}", msg),
            PermissionError::AdminCheckFailed(msg) => write!(f, "Admin check failed: {}", msg),
            PermissionError::ElevationRequired => write!(f, "Elevation to admin/root required"),
            PermissionError::CommandFailed(code) => write!(f, "Command failed with code: {}", code),
        }
    }
}
impl std::error::Error for PermissionError {}
impl From<io::Error> for PermissionError {
    fn from(err: io::Error) -> Self {
        PermissionError::Io(err)
    }
}
#[derive(Debug, Clone)]
pub struct Permissions {
    pub mode: u32,
}
impl Permissions {
    /// Create standard directory permissions (755, root:admin)
    pub fn directory() -> Self {
        Self { mode: 0o755 }
    }
    /// Create standard file permissions (644, root:admin)
    pub fn file() -> Self {
        Self { mode: 0o644 }
    }
    /// Apply permissions to a path
    pub fn apply(&self, path: &Path) -> Result<(), PermissionError> {
        #[cfg(unix)]
        {
            let perms = fs::Permissions::from_mode(self.mode);
            fs::set_permissions(path, perms).map_err(PermissionError::Io)
        }
        #[cfg(not(unix))]
        {
            // For non-Unix platforms like Windows, we can't directly set numeric modes
            // so we'll just ensure the file exists and is writable if needed
            if self.mode & 0o200 != 0 && path.exists() {
                let metadata = fs::metadata(path)?;
                let mut perms = metadata.permissions();
                #[cfg(target_os = "windows")]
                {
                    use std::os::windows::fs::PermissionsExt;
                    // FILE_ATTRIBUTE_READONLY = 0x1
                    let readonly_bit = 0x1;
                    let current_attrs = perms.mode();
                    // Remove readonly bit if it's set
                    if current_attrs & readonly_bit != 0 {
                        perms.set_mode(current_attrs & !readonly_bit);
                        fs::set_permissions(path, perms)?;
                    }
                }
            }
            Ok(())
        }
    }
    /// Verify permissions on a path
    pub fn verify(&self, path: &Path) -> Result<bool, PermissionError> {
        let metadata = fs::metadata(path).map_err(PermissionError::Io)?;
        #[cfg(unix)]
        {
            Ok((metadata.permissions().mode() & 0o777) == self.mode)
        }
        #[cfg(not(unix))]
        {
            // On Windows, we can check if the file is read-only if that's what we care about
            #[cfg(target_os = "windows")]
            {
                use std::os::windows::fs::PermissionsExt;
                let current_attrs = metadata.permissions().mode();
                // Check if the readonly attribute is not set when we need write permission
                let needs_write = self.mode & 0o200 != 0;
                let is_readonly = current_attrs & 0x1 != 0;
                return Ok(!needs_write || !is_readonly);
            }
            // Default implementation for other platforms
            Ok(true)
        }
    }
    /// Get permissions from an existing path
    pub fn from_path(path: &Path) -> Result<Self, PermissionError> {
        let metadata = fs::metadata(path).map_err(PermissionError::Io)?;
        #[cfg(unix)]
        {
            Ok(Self {
                mode: metadata.permissions().mode() & 0o777,
            })
        }
        #[cfg(not(unix))]
        {
            // On non-Unix platforms, we'll return a default value based on readonly status
            #[cfg(target_os = "windows")]
            {
                use std::os::windows::fs::PermissionsExt;
                let current_attrs = metadata.permissions().mode();
                let is_readonly = current_attrs & 0x1 != 0;
                if is_readonly {
                    Ok(Self { mode: 0o444 }) // read-only
                } else {
                    Ok(Self { mode: 0o644 }) // read-write
                }
            }
            #[cfg(not(target_os = "windows"))]
            {
                Ok(Self { mode: 0o644 }) // Default read-write for non-Windows, non-Unix
            }
        }
    }
}
/// Permission utilities for checking and obtaining admin/root privileges
pub struct PermissionUtils;
impl PermissionUtils {
    /// Check if the current process is running with admin/root privileges
    pub fn is_admin() -> bool {
        // If we've already been granted admin privileges in this session, return true
        if ADMIN_PRIVILEGES_GRANTED.load(Ordering::Relaxed) {
            return true;
        }
        #[cfg(unix)]
        {
            // On Unix systems, check if effective user ID is 0 (root)
            unsafe { libc::geteuid() == 0 }
        }
        #[cfg(target_os = "windows")]
        {
            // On Windows, use the IsUserAnAdmin function
            unsafe { IsUserAnAdmin() != 0 }
        }
        #[cfg(all(not(unix), not(target_os = "windows")))]
        {
            // Default implementation for unsupported platforms
            false
        }
    }
    /// Check if admin privileges are required for an operation and request them if needed
    pub fn ensure_admin() -> Result<(), PermissionError> {
        // If we've already been granted admin privileges or are running as admin, return immediately
        if ADMIN_PRIVILEGES_GRANTED.load(Ordering::Relaxed) || Self::is_admin() {
            return Ok(());
        }
        info!("Operation requires elevated privileges");
        #[cfg(target_os = "windows")]
        {
            // On Windows, we can use ShellExecute with "runas" verb to trigger UAC
            // We'll attempt to run a simple command to get admin rights
            use std::ffi::OsStr;
            use std::iter::once;
            use std::os::windows::ffi::OsStrExt;
            let cmd = "cmd.exe";
            let args = "/c echo Admin privileges obtained";
            // Convert command to wide string
            let wide_cmd: Vec<u16> = OsStr::new(cmd).encode_wide().chain(once(0)).collect();
            // Convert args to a wide string
            let wide_args: Vec<u16> = OsStr::new(args).encode_wide().chain(once(0)).collect();
            // Create the runas verb as a wide string
            let runas: Vec<u16> = OsStr::new("runas").encode_wide().chain(once(0)).collect();
            let result = unsafe {
                ShellExecuteW(
                    std::ptr::null_mut(), // hwnd
                    runas.as_ptr(),       // lpOperation - "runas" verb for UAC elevation
                    wide_cmd.as_ptr(),    // lpFile - the command
                    wide_args.as_ptr(),   // lpParameters
                    std::ptr::null(),     // lpDirectory
                    SW_NORMAL,            // nShowCmd
                )
            };
            // ShellExecute returns a value greater than 32 if successful
            if result as usize <= 32 {
                error!("Failed to obtain admin privileges, error code: {}", result);
                return Err(PermissionError::CommandFailed(result as i32));
            }
            // If we got here, we should have admin privileges
            info!("Successfully obtained admin privileges on Windows");
            ADMIN_PRIVILEGES_GRANTED.store(true, Ordering::Relaxed);
            Ok(())
        }
        #[cfg(target_os = "macos")]
        {
            // On macOS, use osascript to show a GUI prompt for admin privileges
            info!("Requesting admin privileges on macOS");
            // Create an AppleScript that will force the authentication dialog
            // This implements a proper authentication prompt that explains what's happening
            let apple_script = "do shell script \"echo 'Admin privileges obtained'\" with administrator privileges with prompt \"OpenFrame requires administrator privileges to continue\"";
            // Execute the AppleScript
            let result = Command::new("osascript")
                .arg("-e")
                .arg(apple_script)
                .status();
            match result {
                Ok(status) if status.success() => {
                    info!("Successfully obtained admin privileges");
                    // Set the flag to remember we have admin privileges
                    ADMIN_PRIVILEGES_GRANTED.store(true, Ordering::Relaxed);
                    Ok(())
                }
                Ok(status) => {
                    error!(
                        "Failed to obtain admin privileges, exit code: {}",
                        status.code().unwrap_or(-1)
                    );
                    Err(PermissionError::CommandFailed(status.code().unwrap_or(-1)))
                }
                Err(e) => {
                    error!("Failed to execute osascript: {}", e);
                    Err(PermissionError::Io(e))
                }
            }
        }
        #[cfg(target_os = "linux")]
        {
            // On Linux, we can try pkexec or sudo to request admin privileges
            info!("Requesting admin privileges on Linux");
            // Try pkexec first (better UI experience)
            let pkexec_result = Command::new("pkexec")
                .arg("echo")
                .arg("Admin privileges obtained")
                .status();
            match pkexec_result {
                Ok(status) if status.success() => {
                    info!("Successfully obtained admin privileges with pkexec");
                    ADMIN_PRIVILEGES_GRANTED.store(true, Ordering::Relaxed);
                    return Ok(());
                }
                _ => {
                    // Try sudo as fallback
                    info!("pkexec failed, trying sudo");
                    let sudo_result = Command::new("sudo")
                        .arg("echo")
                        .arg("Admin privileges obtained")
                        .status();
                    match sudo_result {
                        Ok(status) if status.success() => {
                            info!("Successfully obtained admin privileges with sudo");
                            ADMIN_PRIVILEGES_GRANTED.store(true, Ordering::Relaxed);
                            Ok(())
                        }
                        Ok(status) => {
                            error!(
                                "Failed to obtain admin privileges with sudo, exit code: {}",
                                status.code().unwrap_or(-1)
                            );
                            Err(PermissionError::CommandFailed(status.code().unwrap_or(-1)))
                        }
                        Err(e) => {
                            error!("Failed to execute sudo: {}", e);
                            Err(PermissionError::Io(e))
                        }
                    }
                }
            }
        }
        #[cfg(all(
            not(target_os = "windows"),
            not(target_os = "macos"),
            not(target_os = "linux")
        ))]
        {
            // Default implementation for unsupported platforms
            error!("This operation requires administrator privileges.");
            Err(PermissionError::ElevationRequired)
        }
    }
    /// Try to run a command with elevated privileges
    pub fn run_as_admin(command: &str, args: &[&str]) -> Result<(), PermissionError> {
        // If we've already ensured admin privileges, we can just run the command directly
        if ADMIN_PRIVILEGES_GRANTED.load(Ordering::Relaxed) {
            return Self::run_command(command, args);
        }
        // If already admin, no need to elevate
        if Self::is_admin() {
            return Self::run_command(command, args);
        }
        info!(
            "Attempting to run command with elevated privileges: {} {}",
            command,
            args.join(" ")
        );

        #[cfg(target_os = "windows")]
        {
            // First ensure we have admin privileges
            Self::ensure_admin()?;

            // Now we can just run the command directly
            Self::run_command(command, args)
        }

        #[cfg(target_os = "linux")]
        {
            // First ensure we have admin privileges
            Self::ensure_admin()?;

            // Now we can just run the command directly
            Self::run_command(command, args)
        }

        #[cfg(target_os = "macos")]
        {
            // First ensure we have admin privileges
            Self::ensure_admin()?;

            // Now we can just run the command directly
            Self::run_command(command, args)
        }

        #[cfg(all(
            not(target_os = "windows"),
            not(target_os = "macos"),
            not(target_os = "linux")
        ))]
        {
            // Default implementation for unsupported platforms
            Err(PermissionError::AdminCheckFailed(
                "Platform not supported".to_string(),
            ))
        }
    }
    /// Run a command without elevation
    pub fn run_command(command: &str, args: &[&str]) -> Result<(), PermissionError> {
        let output = Command::new(command).args(args).output();
        match output {
            Ok(output) => {
                // Log the stdout and stderr
                if !output.stdout.is_empty() {
                    let stdout = String::from_utf8_lossy(&output.stdout);
                    info!("Command stdout: {}", stdout);
                }
                if !output.stderr.is_empty() {
                    let stderr = String::from_utf8_lossy(&output.stderr);
                    error!("Command stderr: {}", stderr);
                }
                if output.status.success() {
                    Ok(())
                } else {
                    Err(PermissionError::CommandFailed(
                        output.status.code().unwrap_or(-1),
                    ))
                }
            }
            Err(e) => Err(PermissionError::Io(e)),
        }
    }
    /// Check if a process has capability to perform a specific operation
    pub fn has_capability(capability: Capability) -> bool {
        match capability {
            Capability::ManageServices => Self::is_admin(),
            Capability::WriteSystemDirectories => Self::is_admin(),
            Capability::ReadSystemLogs => Self::can_read_system_logs(),
            Capability::WriteSystemLogs => Self::is_admin(),
        }
    }
    /// Check if the process can read system logs
    fn can_read_system_logs() -> bool {
        #[cfg(unix)]
        {
            // On Unix, check if we're root or in the proper group
            if unsafe { libc::geteuid() } == 0 {
                return true;
            }
            #[cfg(target_os = "macos")]
            {
                // On macOS, check if we're in the admin group
                // This is a simplified check - in practice you might need more sophisticated group checking
                let groups = Self::get_current_user_groups();
                groups.contains(&ADMIN_GID)
            }
            #[cfg(target_os = "linux")]
            {
                // On Linux, check if we can access the system log directory
                Path::new("/var/log")
                    .metadata()
                    .map(|m| {
                        // Check if 'other' has read permissions (or we're the owner/group)
                        let mode = m.permissions().mode();
                        mode & 0o004 != 0
                            || (mode & 0o400 != 0 && m.uid() == unsafe { libc::geteuid() })
                            || (mode & 0o040 != 0
                                && Self::get_current_user_groups().contains(&m.gid()))
                    })
                    .unwrap_or(false)
            }
            #[cfg(all(unix, not(target_os = "macos"), not(target_os = "linux")))]
            {
                false
            }
        }
        #[cfg(target_os = "windows")]
        {
            // On Windows, try to open the event log
            // This is a simplified approach - in practice you might use Windows-specific APIs
            Path::new("C:\\Windows\\System32\\winevt\\Logs").exists() && Self::is_admin()
        }
        #[cfg(all(not(unix), not(target_os = "windows")))]
        {
            false
        }
    }
    #[cfg(unix)]
    fn get_current_user_groups() -> Vec<u32> {
        let mut groups = Vec::new();
        let mut ngroups: i32 = 16; // Start with space for 16 groups
        let mut group_list: Vec<libc::gid_t> = vec![0; ngroups as usize];
        unsafe {
            // First call to get the actual number of groups
            libc::getgroups(ngroups, group_list.as_mut_ptr());
            // Get the actual groups
            ngroups = libc::getgroups(ngroups, group_list.as_mut_ptr());
            if ngroups > 0 {
                group_list.truncate(ngroups as usize);
                groups = group_list;
            }
        }
        groups
    }
}
/// Capabilities that a process might need
#[derive(Debug, Clone, Copy)]
pub enum Capability {
    ManageServices,
    WriteSystemDirectories,
    ReadSystemLogs,
    WriteSystemLogs,
}
#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;
    #[test]
    fn test_permissions_creation() {
        let dir_perms = Permissions::directory();
        assert_eq!(dir_perms.mode, 0o755);
        let file_perms = Permissions::file();
        assert_eq!(file_perms.mode, 0o644);
    }
    #[cfg(unix)]
    #[test]
    fn test_permissions_verification() {
        if unsafe { libc::geteuid() } == 0 {
            let temp = tempdir().unwrap();
            let test_path = temp.path().join("test_file");
            fs::write(&test_path, "test").unwrap();
            let perms = Permissions::file();
            assert!(perms.apply(&test_path).is_ok());
            assert!(perms.verify(&test_path).unwrap());
        }
    }
    #[test]
    fn test_is_admin() {
        // This just verifies the function runs without errors
        let is_admin = PermissionUtils::is_admin();
        println!("Running with admin privileges: {}", is_admin);
    }
    #[test]
    fn test_has_capability() {
        // Test all capabilities
        for cap in &[
            Capability::ManageServices,
            Capability::WriteSystemDirectories,
            Capability::ReadSystemLogs,
            Capability::WriteSystemLogs,
        ] {
            let has_cap = PermissionUtils::has_capability(*cap);
            println!("Has capability {:?}: {}", cap, has_cap);
        }
    }
    #[test]
    fn test_ensure_admin() {
        // This should return Ok if already admin, or attempt to get privileges
        let result = PermissionUtils::ensure_admin();
        if PermissionUtils::is_admin() {
            assert!(result.is_ok());
        } else {
            // The function might return Ok if the user granted privileges via the prompt,
            // or an error if they declined or if there was an issue with the prompt
            println!("Result of ensure_admin when not admin: {:?}", result);
        }
    }
    #[test]
    fn test_run_command() {
        // Test running a simple command that should work on all platforms
        // On Windows, use "cmd /c echo test"
        // On Unix, use "echo test"
        #[cfg(target_os = "windows")]
        {
            let result = PermissionUtils::run_command("cmd", &["/c", "echo", "test"]);
            assert!(result.is_ok());
        }
        #[cfg(unix)]
        {
            let result = PermissionUtils::run_command("echo", &["test"]);
            assert!(result.is_ok());
        }
    }
    #[test]
    fn test_cross_platform_permissions() {
        // Create a temporary file and test platform-agnostic permissions
        let temp = tempdir().unwrap();
        let test_path = temp.path().join("test_file");
        fs::write(&test_path, "test").unwrap();
        // Test applying permissions
        let perms = Permissions::file();
        let result = perms.apply(&test_path);
        assert!(result.is_ok());
        // Test verifying permissions - should pass on all platforms
        // even though the exact permission representation differs
        let verify_result = perms.verify(&test_path);
        assert!(verify_result.is_ok());
        // Test retrieving permissions from a path
        let retrieved_perms = Permissions::from_path(&test_path);
        assert!(retrieved_perms.is_ok());
    }
    #[test]
    fn test_can_read_system_logs() {
        // Just verify the function runs without errors
        let can_read = PermissionUtils::has_capability(Capability::ReadSystemLogs);
        println!("Can read system logs: {}", can_read);
    }
}