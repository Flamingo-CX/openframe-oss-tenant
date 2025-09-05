use anyhow::{Context, Result};
use reqwest::Client;
use tracing::{info, error, debug, warn};

use crate::clients::RegistrationClient;
use crate::services::agent_configuration_service::AgentConfigurationService;
use crate::models::{AgentRegistrationRequest, AgentRegistrationResponse, AgentConfiguration};
use crate::services::device_data_fetcher::DeviceDataFetcher;
use crate::platform::directories::DirectoryManager;

#[derive(Clone)]
pub struct AgentRegistrationService {
    registration_client: RegistrationClient,
    device_data_fetcher: DeviceDataFetcher,
    config_service: AgentConfigurationService,
}

impl AgentRegistrationService {

    // TODO: temporary save to file during installation
    // For development purposes should be manually set based on server key
    const INITIAL_KEY: &str = "CjHQgUkpX7Mnm1a-FyQvNjYC2ngCKrnf";

    pub fn new(
        registration_client: RegistrationClient,
        device_data_fetcher: DeviceDataFetcher,
        config_service: AgentConfigurationService,
    ) -> Self {
        Self {
            registration_client,
            device_data_fetcher,
            config_service,
        }
    }

    pub async fn register_agent(&self) -> Result<AgentRegistrationResponse> {
        let registration_request = self.build_registration_request()?;
        
        let response = self.registration_client
            .register(Self::INITIAL_KEY, registration_request)
            .await
            .context("Failed to register agent")?;

        self.config_service.save_registration_data(
            response.machine_id.clone(),
            response.client_id.clone(),
            response.client_secret.clone()
        ).await
        .context("Failed to save registration data")?;

        Ok(response)
    }

    fn build_registration_request(&self) -> Result<AgentRegistrationRequest> {
        let hostname = self.device_data_fetcher.get_hostname()
            .unwrap_or_else(|| String::new());
        let agent_version = self.device_data_fetcher.get_agent_version()
            .unwrap_or_else(|| String::new());

        let request = AgentRegistrationRequest {
            hostname,
            agent_version,
        };

        Ok(request)
    }
} 