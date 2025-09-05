use anyhow::{Context, Result};
use reqwest::{Client, header::{HeaderMap, HeaderValue}};
use tracing::{info, error, debug};

use crate::models::{AgentRegistrationRequest, AgentRegistrationResponse};

#[derive(Clone)]
pub struct RegistrationClient {
    http_client: Client,
    base_url: String,
}

impl RegistrationClient {
    pub fn new(base_url: String, http_client: Client) -> Result<Self> {
        Ok(Self { http_client, base_url })
    }

    pub async fn register(
        &self,
        initial_key: &str,
        request: AgentRegistrationRequest,
    ) -> Result<AgentRegistrationResponse> {
        let url = format!("{}/clients/api/agents/register", self.base_url);
        
        let mut headers = HeaderMap::new();
        headers.insert("X-Initial-Key", initial_key.parse()
            .context("Failed to parse initial key header")?);
        headers.insert("Content-Type", HeaderValue::from_static("application/json"));

        let response = self.http_client
            .post(&url)
            .headers(headers)
            .json(&request)
            .send()
            .await
            .context("Failed to send registration request")?;

        let status = response.status();
        
        if !status.is_success() {
            return Err(anyhow::anyhow!("Failed to register agent with status {} and body {}", status, response.text().await?));
        }

        let registration_response: AgentRegistrationResponse = response
            .json()
            .await
            .context("Failed to parse registration response")?;

        Ok(registration_response)
    }


}

 