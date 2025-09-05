use crate::models::ToolConnectionMessage;
use crate::services::nats_message_publisher::NatsMessagePublisher;

#[derive(Clone)]
pub struct ToolConnectionMessagePublisher {
    nats_message_publisher: NatsMessagePublisher,
}

impl ToolConnectionMessagePublisher {

    pub fn new(nats_message_publisher: NatsMessagePublisher) -> Self {
        Self { nats_message_publisher }
    }

    pub async fn publish(&self, machine_id: String, tool_agent_id: String) -> anyhow::Result<()> {
        let topic = Self::build_topic_name(machine_id);
        let message = Self::build_message(tool_agent_id);
        self.nats_message_publisher.publish(&topic, message).await
        // TODO: wait for ack and publish again if failed
    }

    fn build_topic_name(machine_id: String) -> String {
        format!("machine.{}.toolconnection", machine_id)
    }

    fn build_message(tool_agent_id: String) -> ToolConnectionMessage {
        ToolConnectionMessage {
            tool_agent_id,
        }
    }
}