use anyhow::{Context, Result};
use async_nats::Client;
use tokio::sync::RwLock;
use tracing::{debug, info};
use crate::services::agent_configuration_service::AgentConfigurationService;
use std::sync::Arc;

#[derive(Clone)]
pub struct NatsConnectionManager {
    client: Arc<RwLock<Option<Arc<Client>>>>,
    nats_server_url: String,
    config_service: AgentConfigurationService,
}

impl NatsConnectionManager {

    // TODO: no password or password from parameters.
    const NATS_DEVICE_USER: &'static str = "device";
    const NATS_DEVICE_PASSWORD: &'static str = "devicepassword";
    
    pub fn new(nats_server_url: &str, config_service: AgentConfigurationService) -> Self {
        Self {
            client: Arc::new(RwLock::new(None)),
            nats_server_url: nats_server_url.to_string(),
            config_service,
        }
    }

    pub async fn connect(&self) -> Result<()> {
        info!("Connecting to NATS server");

        let connection_url = self.build_nats_connection_url().await?;
        let machine_id = self.config_service.get_machine_id().await?;

        // TODO: token fallback and connection retry
        let client = async_nats::ConnectOptions::new()
            .name(machine_id)
            .user_and_password(Self::NATS_DEVICE_USER.to_string(), Self::NATS_DEVICE_PASSWORD.to_string())
            .max_reconnects(10000)
            .retry_on_initial_connect()
            .reconnect_delay_callback(|attempt| {
                println!("\n\nFallback: reconnecting to NATS server, attempt: {}\n\n", attempt);
                std::time::Duration::from_secs(2)
            })
            .connect(&connection_url)

            .await
            .context("Failed to connect to NATS server")?;

        info!("Connected to NATS server");

        *self.client.write().await = Some(Arc::new(client));

        Ok(())
    }

    async fn build_nats_connection_url(&self) -> Result<String> {
        let token = self.config_service.get_access_token().await?;
        let host = &self.nats_server_url;
        Ok(format!("{}/ws/nats?authorization={}", host, token))
    }

    pub async fn get_client(&self) -> Result<Arc<Client>> {
        let guard = self.client.read().await;
        guard
            .clone()
            .context("NATS client is not initialized. Call connect() first.")
    }
}