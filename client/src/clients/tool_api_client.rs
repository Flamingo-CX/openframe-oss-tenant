use reqwest::Client;
use anyhow::{Context, Result};

use crate::services::agent_configuration_service::AgentConfigurationService;

#[derive(Clone)]
pub struct ToolApiClient {
    http_client: Client,
    base_url: String,
    config_service: AgentConfigurationService,
}

impl ToolApiClient {
    pub fn new(http_client: Client, base_url: String, config_service: AgentConfigurationService) -> Self {
        Self { 
            http_client, 
            base_url,
            config_service,
        }
    }

    pub async fn get_tool_asset(&self, tool_id: String, asset_path: String) -> Result<bytes::Bytes> {
        let url = format!("{}/tools/agent/{}/{}", self.base_url, tool_id, asset_path);
        
        // Get access token from configuration service
        let access_token = self.config_service.get_access_token()
            .await
            .context("Failed to get access token from configuration service")?;
        
        let response = self.http_client
            .get(url)
            .header("Authorization", format!("Bearer {}", access_token))
            .send()
            .await
            .context("Failed to get tool asset from tool API")?;

        let status = response.status();

        if !response.status().is_success() {
            let error_text = response.text().await.context("Failed to read response text")?;
            return Err(anyhow::anyhow!("Failed to get tool asset with status {} and body {}", status, error_text));
        }

        let body = response.bytes().await?; 
        Ok(body)
    }
}
