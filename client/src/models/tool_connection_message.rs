use serde::{Serialize, Deserialize};

#[derive(Debug, Serialize, Deserialize)]
pub struct ToolConnectionMessage {
    pub tool_agent_id: String,
}