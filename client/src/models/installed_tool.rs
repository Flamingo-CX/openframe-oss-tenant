use serde::{Deserialize, Serialize};

/// Installation status of the tool on the endpoint.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum ToolStatus {
    Installed,
    Uninstalled,
    Installing,
}

impl Default for ToolStatus {
    fn default() -> Self {
        ToolStatus::Uninstalled
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct InstalledTool {
    pub tool_id: String,

    pub version: String,

    pub run_command_args: Vec<String>,

    pub status: ToolStatus,
}

impl Default for InstalledTool {
    fn default() -> Self {
        Self {
            tool_id: String::new(),
            version: String::new(),
            run_command_args: Vec::new(),
            status: ToolStatus::default(),
        }
    }
}
