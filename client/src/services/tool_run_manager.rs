use anyhow::{Context, Result};
use tracing::{info, warn, error};
use std::process::Command;
use tokio::time::sleep;
use std::time::Duration;

use crate::models::installed_tool::{InstalledTool, ToolStatus};
use crate::services::installed_tools_service::InstalledToolsService;
use crate::services::tool_command_params_resolver::ToolCommandParamsResolver;

const RETRY_DELAY_SECONDS: u64 = 5;

#[derive(Clone)]
pub struct ToolRunManager {
    installed_tools_service: InstalledToolsService,
    params_processor: ToolCommandParamsResolver,
}

impl ToolRunManager {
    pub fn new(
        installed_tools_service: InstalledToolsService,
        params_processor: ToolCommandParamsResolver
    ) -> Self {
        Self {
            installed_tools_service,
            params_processor,
        }
    }

    pub async fn run(&self) -> Result<()> {
        let tools = self
            .installed_tools_service
            .get_all()
            .await
            .context("Failed to retrieve installed tools list")?;

        if tools.is_empty() {
            info!("No installed tools found – nothing to run");
            return Ok(());
        }

        for tool in tools {
            self.run_tool(tool).await?;
        }
 
        Ok(())
    }

    pub async fn run_new_tool(&self, installed_tool: InstalledTool) -> Result<()> {
        info!(tool_id = %installed_tool.tool_id, "Running single tool");
        self.run_tool(installed_tool).await
    }

    async fn run_tool(&self, tool: InstalledTool) -> Result<()> {
        let params_processor = self.params_processor.clone();
        
        tokio::spawn(async move {
            loop {
                // exchange args placeholders to real values
                let processed_args = match params_processor.process(&tool.tool_id, tool.run_command_args.clone()) {
                    Ok(args) => args,
                    Err(e) => {
                        error!(tool_id = %tool.tool_id, error = %e, 
                               "Failed to process run command params - giving up");
                        break;
                    }
                };

                // Build executable path directly using directory manager
                let command_path = params_processor.directory_manager.app_support_dir()
                    .join(&tool.tool_id)
                    .join("agent")
                    .to_string_lossy()
                    .to_string();

                info!("TOOL_LOG: Executing tool command - tool_id: {}, command: {}, args: {:?}", 
                      tool.tool_id, command_path, processed_args);

                // spawn tool run process and wait async till the end
                let mut child = match Command::new(&command_path)
                    .args(&processed_args)
                    .spawn()
                {
                    Ok(child) => child,
                    Err(e) => {
                        error!(tool_id = %tool.tool_id, error = %e, 
                               "Failed to start tool process - retrying in {} seconds", RETRY_DELAY_SECONDS);
                        sleep(Duration::from_secs(RETRY_DELAY_SECONDS)).await;
                        continue;
                    }
                };

                match child.wait() {
                    Ok(status) => {
                        if status.success() {
                            warn!(tool_id = %tool.tool_id, 
                                  "Tool completed successfully but should keep running - restarting in {} seconds", 
                                  RETRY_DELAY_SECONDS);
                            sleep(Duration::from_secs(RETRY_DELAY_SECONDS)).await;
                        } else {
                            error!(tool_id = %tool.tool_id, exit_status = %status, 
                                   "Tool failed with exit status - restarting in {} seconds", RETRY_DELAY_SECONDS);
                            sleep(Duration::from_secs(RETRY_DELAY_SECONDS)).await;
                        }
                    }
                    Err(e) => {
                        error!(tool_id = %tool.tool_id, error = %e, 
                               "Failed to wait for tool process - restarting in {} seconds", RETRY_DELAY_SECONDS);
                        sleep(Duration::from_secs(RETRY_DELAY_SECONDS)).await;
                    }
                }
            }
        });

        Ok(())
    }
}
