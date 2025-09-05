use reqwest::Client;
use anyhow::{Context, Result};

#[derive(Clone)]
pub struct ToolAgentFileClient {
    http_client: Client,
    base_url: String,
}

impl ToolAgentFileClient {
    pub fn new(http_client: Client, base_url: String) -> Self {
        Self { http_client, base_url }
    }

    pub async fn get_tool_agent_file(&self, assetId: String) -> Result<bytes::Bytes> {
        let url = format!("{}/clients/tool-agent/{}", self.base_url, assetId);
        let response = self.http_client.get(url).send()
            .await
            .context("Failed to get tool agent file")?;

        let status = response.status();

        if !response.status().is_success() {
            let error_text = response.text().await.context("Failed to read response text")?;
            return Err(anyhow::anyhow!("Failed to get tool agent file with status {} and body {}", status, error_text));
        }

        let body = response.bytes().await?; 
        Ok(body)
    }

}