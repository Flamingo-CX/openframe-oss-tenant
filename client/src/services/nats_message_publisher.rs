use crate::services::nats_connection_manager::NatsConnectionManager;
use serde::Serialize;
use anyhow::{Result, Context};

#[derive(Clone)]
pub struct NatsMessagePublisher {
    nats_connection_manager: NatsConnectionManager,
}

impl NatsMessagePublisher {
    pub fn new(nats_connection_manager: NatsConnectionManager) -> Self {
        Self { nats_connection_manager }
    }

    pub async fn publish<T: Serialize>(&self, subject: &str, payload: T) -> Result<()> {
        let payload_json = serde_json::to_string(&payload).context("Failed to serialize payload")?;

        let client = self.nats_connection_manager
            .get_client()
            .await?;

        client.publish(subject.to_string(), payload_json.into()).await
            .context("Failed to publish message to NATS")?;
        Ok(())
    }
}