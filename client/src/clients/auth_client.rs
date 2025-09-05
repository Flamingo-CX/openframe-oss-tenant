use anyhow::{Context, Result};
use reqwest::{Client, header::{HeaderMap, HeaderValue}};
use std::collections::HashMap;

use crate::models::AgentTokenResponse;

#[derive(Clone)]
pub struct AuthClient {
    http_client: Client,
    base_url: String,
}

impl AuthClient {
    pub fn new(base_url: String, http_client: Client) -> Self {
        Self {
            http_client,
            base_url,
        }
    }


    pub async fn authenticate_with_secret(
        &self,
        client_id: String,
        client_secret: String,
    ) -> Result<AgentTokenResponse> {
        let url = format!("{}/clients/oauth/token", self.base_url);
        
        let mut headers = HeaderMap::new();
        headers.insert("Content-Type", HeaderValue::from_static("application/x-www-form-urlencoded"));

        let mut form_data = HashMap::new();
        form_data.insert("grant_type", "client_credentials".to_string());
        form_data.insert("client_id", client_id);
        form_data.insert("client_secret", client_secret);

        let response = self.http_client
            .post(&url)
            .headers(headers)
            .form(&form_data)
            .send()
            .await
            .context("Failed to send token request")?;

        let status = response.status();
        
        if !status.is_success() {
            return Err(anyhow::anyhow!("Failed to obtain access token: with status {} and body {}", status, response.text().await?));
        }

        let token_response: AgentTokenResponse = response
            .json()
            .await
            .context("Failed to parse token response")?;

        Ok(token_response)
    }

    pub async fn authenticate_with_refresh_token(
        &self,
        refresh_token: String,
    ) -> Result<AgentTokenResponse> {
        let url = format!("{}/oauth/token", self.base_url);
        
        let mut headers = HeaderMap::new();
        headers.insert("Content-Type", HeaderValue::from_static("application/x-www-form-urlencoded"));

        let mut form_data = HashMap::new();
        form_data.insert("grant_type", "refresh_token".to_string());
        form_data.insert("refresh_token", refresh_token);

        let response = self.http_client
            .post(&url)
            .headers(headers)
            .form(&form_data)
            .send()
            .await
            .context("Failed to send refresh token request")?;

        let status = response.status();

        if !status.is_success() {
            return Err(anyhow::anyhow!("Failed to refresh access token: HTTP {}", status));
        }

        let token_response: AgentTokenResponse = response
            .json()
            .await
            .context("Failed to parse refresh token response")?;

        Ok(token_response)
    }
} 