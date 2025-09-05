use std::net::TcpStream;
use std::panic;
use tracing::{info, warn, debug};

#[derive(Clone)]
pub struct DeviceDataFetcher;

impl DeviceDataFetcher {
    pub fn new() -> Self {
        Self
    }

    pub fn get_hostname(&self) -> Option<String> {
        match hostname::get() {
            Ok(hostname) => {
                let hostname_str = hostname.to_string_lossy().to_string();
                Some(hostname_str)
            }
            Err(e) => {
                warn!("Failed to get hostname: {:#}", e);
                None
            }
        }
    }

    pub fn get_agent_version(&self) -> Option<String> {
        let version = env!("CARGO_PKG_VERSION").to_string();
        info!("Agent version: {}", version);
        Some(version)
    }
} 