use anyhow::{Context, Result};
use std::fs;
use std::path::{Path, PathBuf};
use crate::models::InstalledTool;
use crate::platform::directories::DirectoryManager;

#[derive(Clone)]
pub struct InstalledToolsService {
    file_path: PathBuf,
}

impl InstalledToolsService {
    pub fn new(directory_manager: DirectoryManager) -> Result<Self> {
        let path = directory_manager.secured_dir().join("installed_tools.json");
        directory_manager
            .ensure_directories()
            .with_context(|| "Failed to ensure secured directory exists")?;
        Ok(Self { file_path: path })
    }

    pub async fn save(&self, tool: InstalledTool) -> Result<()> {
        let mut tools = self.get_all().await?;

        if let Some(existing) = tools.iter_mut().find(|t| t.tool_id == tool.tool_id) {
            *existing = tool;
        } else {
            tools.push(tool);
        }

        self.persist(&tools).await
    }

    pub async fn get_by_tool_id(&self, tool_id: &str) -> Result<Option<InstalledTool>> {
        let tools = self.get_all().await?;
        Ok(tools.into_iter().find(|t| t.tool_id == tool_id))
    }

    pub async fn get_all(&self) -> Result<Vec<InstalledTool>> {
        if !self.file_path.exists() {
            return Ok(Vec::new());
        }

        let json = fs::read_to_string(&self.file_path)
            .with_context(|| format!("Failed to read installed tools file: {:?}", self.file_path))?;
        let tools: Vec<InstalledTool> = serde_json::from_str(&json)
            .context("Failed to deserialize installed tools from JSON")?;
        Ok(tools)
    }

    async fn persist(&self, tools: &[InstalledTool]) -> Result<()> {
        let json = serde_json::to_string_pretty(tools)
            .context("Failed to serialize installed tools to JSON")?;
        fs::write(&self.file_path, json)
            .with_context(|| format!("Failed to write installed tools file: {:?}", self.file_path))?;
        Ok(())
    }
}
