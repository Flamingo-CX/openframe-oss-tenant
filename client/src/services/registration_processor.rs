use anyhow::{Context, Result};
use tokio::time::{sleep, Duration};
use tracing::{error, info, warn};

use crate::services::AgentRegistrationService;
use crate::services::agent_configuration_service::AgentConfigurationService;
use crate::models::AgentRegistrationResponse;

#[derive(Clone)]
pub struct RegistrationProcessor {
    registration_service: AgentRegistrationService,
    config_service: AgentConfigurationService,
}

impl RegistrationProcessor {
    pub fn new(
        registration_service: AgentRegistrationService,
        config_service: AgentConfigurationService,
    ) -> Self {
        Self {
            registration_service,
            config_service,
        }
    }

    pub async fn process(&self) -> Result<()> {
        let machine_id = self.config_service.get_machine_id().await?;
        if !machine_id.is_empty() {
            info!(
                "Existing machine_id detected ({}). Skipping registration.",
                machine_id
            );
            return Ok(());
        }

        info!("No machine_id found – starting registration loop");
        loop {
            match self.attempt_registration().await {
                Ok(_) => {
                    info!("Registration succeeded");
                    return Ok(());
                }
                Err(e) => {
                    error!("Registration attempt failed. Retrying in 60 seconds…: {:#}", e);
                    // TODO: Add exponential backoff
                    sleep(Duration::from_secs(60)).await;
                }
            }
        }
    }

    async fn attempt_registration(&self) -> Result<AgentRegistrationResponse> {
        self.registration_service
            .register_agent()
            .await
            .context("Registration service returned an error")
    }
} 