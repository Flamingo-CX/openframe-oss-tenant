use serde::{Serialize, Deserialize};

#[derive(Debug, Serialize, Deserialize)]
pub struct ToolInstallationResult {
    pub tool_agent_id: String,
}