use anyhow::Result;
use serde::Serialize;
use std::sync::Arc;
use tokio::sync::mpsc;
use tokio::time::{sleep, Duration};
use tracing::{error, info};

const BATCH_SIZE: usize = 100;
const BATCH_TIMEOUT: Duration = Duration::from_secs(30);

#[derive(Debug, Clone, Serialize)]
pub struct LogBatch {
    pub logs: Vec<String>,
    pub agent_id: String,
    pub timestamp: chrono::DateTime<chrono::Utc>,
}

pub struct LogShipper {
    sender: mpsc::Sender<String>,
    endpoint: String,
    agent_id: String,
}

impl LogShipper {
    pub fn new(endpoint: String, agent_id: String) -> Self {
        let (sender, receiver) = mpsc::channel(1000);

        // Clone values before moving them
        let endpoint_clone = endpoint.clone();
        let agent_id_clone = agent_id.clone();

        let shipper = LogShipper {
            sender,
            endpoint,
            agent_id,
        };

        // Spawn background shipping task
        tokio::spawn(async move {
            Self::ship_logs(receiver, endpoint_clone, agent_id_clone).await;
        });

        shipper
    }

    pub async fn send(&self, log: String) -> Result<()> {
        self.sender.send(log).await?;
        Ok(())
    }

    async fn ship_logs(mut receiver: mpsc::Receiver<String>, endpoint: String, agent_id: String) {
        let mut batch = Vec::with_capacity(BATCH_SIZE);
        let client = reqwest::Client::new();

        loop {
            tokio::select! {
                // Wait for either a new log message or the batch timeout
                Some(log) = receiver.recv() => {
                    batch.push(log);

                    // Ship batch if it reaches max size
                    if batch.len() >= BATCH_SIZE {
                        if let Err(e) = Self::send_batch(&client, &endpoint, &agent_id, batch.clone()).await {
                            tracing::error!("Failed to ship log batch: {:#}", e);
                        }
                        batch.clear();
                    }
                }
                _ = sleep(BATCH_TIMEOUT) => {
                    // Ship current batch if we have any logs
                    if !batch.is_empty() {
                        if let Err(e) = Self::send_batch(&client, &endpoint, &agent_id, batch.clone()).await {
                            tracing::error!("Failed to ship log batch: {:#}", e);
                        }
                        batch.clear();
                    }
                }
            }
        }
    }

    async fn send_batch(
        client: &reqwest::Client,
        endpoint: &str,
        agent_id: &str,
        logs: Vec<String>,
    ) -> Result<()> {
        let batch = LogBatch {
            logs,
            agent_id: agent_id.to_string(),
            timestamp: chrono::Utc::now(),
        };

        client
            .post(endpoint)
            .json(&batch)
            .send()
            .await?
            .error_for_status()?;

        Ok(())
    }
}
