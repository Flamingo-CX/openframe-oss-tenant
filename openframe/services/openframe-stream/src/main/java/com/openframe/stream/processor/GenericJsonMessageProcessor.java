package com.openframe.stream.processor;

import com.openframe.data.model.debezium.DeserializedDebeziumMessage;
import com.openframe.data.model.debezium.IntegratedToolEnrichedData;
import com.openframe.stream.deserializer.KafkaMessageDeserializer;
import com.openframe.data.model.enums.DataEnrichmentServiceType;
import com.openframe.data.model.enums.Destination;
import com.openframe.data.model.enums.MessageType;
import com.openframe.stream.handler.MessageHandler;
import com.openframe.stream.service.DataEnrichmentService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GenericJsonMessageProcessor {

    private final Map<MessageType, Map<Destination, MessageHandler>> handlers;
    private final Map<DataEnrichmentServiceType, DataEnrichmentService> dataEnrichmentServices;
    private final Map<MessageType, KafkaMessageDeserializer> deserializers;

    public GenericJsonMessageProcessor(List<MessageHandler> handlers, List<DataEnrichmentService> dataEnrichmentServices, List<KafkaMessageDeserializer> deserializers) {
        this.handlers = handlers.stream()
                .collect(Collectors.groupingBy(
                        MessageHandler::getType,
                        Collectors.toMap(
                                MessageHandler::getDestination,
                                Function.identity()
                        )
                ));
        this.dataEnrichmentServices = dataEnrichmentServices.stream()
                .collect(Collectors.toMap(DataEnrichmentService::getType, Function.identity()));
        this.deserializers = deserializers.stream()
                .collect(Collectors.toMap(KafkaMessageDeserializer::getType, Function.identity()));
    }

    public void process(DeserializedDebeziumMessage message, MessageType type) {
        DeserializedDebeziumMessage deserializedKafkaMessage = deserialize(message, type);
        IntegratedToolEnrichedData enrichedData = getExtraParams(deserializedKafkaMessage, type);
        type.getDestinationList().forEach(destination -> {
            MessageHandler handler = handlers.get(type).get(destination);
            if (handler == null) {
                throw new IllegalArgumentException("No handler found for type: " + type);
            }
            handler.handle(deserializedKafkaMessage, enrichedData);
        });
    }

    private DeserializedDebeziumMessage deserialize(DeserializedDebeziumMessage message, MessageType type) {
        KafkaMessageDeserializer deserializer = deserializers.get(type);
        if (deserializer == null) {
            throw new IllegalArgumentException("The message type '%s' is not supported".formatted(type));
        }
        return deserializer.deserialize(message, type);
    }

    private IntegratedToolEnrichedData getExtraParams(DeserializedDebeziumMessage message, MessageType messageType) {
        DataEnrichmentService dataEnrichmentService = dataEnrichmentServices.get(messageType.getDataEnrichmentServiceType());
        return dataEnrichmentService.getExtraParams(message);
    }

}
