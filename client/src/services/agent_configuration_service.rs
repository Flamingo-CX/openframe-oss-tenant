use anyhow::{Context, Result};
use std::fs;
use std::path::{Path, PathBuf};
use tracing;

use crate::models::AgentConfiguration;
use crate::platform::directories::DirectoryManager;

#[derive(Clone)]
pub struct AgentConfigurationService {
    config_file_path: PathBuf
}

impl AgentConfigurationService {
    pub fn new(directory_manager: DirectoryManager) -> Result<Self> {
        let config_file_path = directory_manager.secured_dir().join("agent_config.json");
        
        directory_manager.ensure_directories()
            .with_context(|| "Failed to ensure secured directory exists")?;

        Ok(Self { 
            config_file_path
        })
    }

    pub async fn save_initial_data(&self, url: String, secret: String) -> Result<()> {
        let mut config = self.get().await?;
        config.server_url = url;
        config.initial_secret = secret;
        
        self.save(&config).await?;
        
        Ok(())
    }

    pub async fn clear_initial_secret(&self) -> Result<()> {
        let mut config = self.get().await?;
        config.initial_secret = String::new();
        
        self.save(&config).await?;
        
        Ok(())
    }

    pub async fn save_registration_data(&self, machine_id: String, client_id: String, client_secret: String) -> Result<()> {
        let mut config = self.get().await?;
        config.machine_id = machine_id;
        config.client_id = client_id;
        config.client_secret = client_secret;
        
        self.save(&config).await?;
        
        Ok(())
    }

    pub async fn update_tokens(&self, access_token: String, refresh_token: String) -> Result<()> {
        let mut config = self.get().await?;
        config.access_token = access_token;
        config.refresh_token = refresh_token;
        
        self.save(&config).await?;
        
        Ok(())
    }

    pub async fn get_server_url(&self) -> Result<String> {
        let config = self.get().await?;
        Ok(config.server_url.clone())
    }

    pub async fn get_machine_id(&self) -> Result<String> {
        let config = self.get().await?;
        Ok(config.machine_id.clone())
    }

    pub async fn get_client_credentials(&self) -> Result<(String, String)> {
        let config = self.get().await?;
        Ok((
            config.client_id.clone(),
            config.client_secret.clone(),
        ))
    }

    pub async fn get_access_token(&self) -> Result<String> {
        let config = self.get().await?;
        return Ok(config.access_token.clone());
    }

    pub async fn get_refresh_token(&self) -> Result<String> {
        let config = self.get().await?;
        return Ok(config.refresh_token.clone());
    }

    async fn get(&self) -> Result<AgentConfiguration> {
        if !self.config_file_path.exists() {
            return Ok(AgentConfiguration::default());
        }

        let json_content = fs::read_to_string(&self.config_file_path)
            .with_context(|| format!("Failed to read config file: {:?}", self.config_file_path))?;

        let config: AgentConfiguration = serde_json::from_str(&json_content)
            .context("Failed to deserialize agent configuration from JSON")?;

        Ok(config)
    }

    async fn save(&self, config: &AgentConfiguration) -> Result<()> {
        let json_content = serde_json::to_string_pretty(config)
            .context("Failed to serialize agent configuration to JSON")?;

        fs::write(&self.config_file_path, json_content)
            .with_context(|| format!("Failed to write config file: {:?}", self.config_file_path))?;
        
        Ok(())
    }
}

 