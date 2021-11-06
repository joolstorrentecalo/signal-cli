package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.asamk.Signal;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.UntrustedIdentityException;
import org.asamk.signal.manager.api.InvalidNumberException;
import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.manager.api.RecipientIdentifier;

import java.util.List;
import java.util.UUID;

public record JsonMessageEnvelope(
        @Deprecated String source,
        String sourceNumber,
        String sourceUuid,
        String sourceName,
        Integer sourceDevice,
        long timestamp,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonDataMessage dataMessage,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonSyncMessage syncMessage,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonCallMessage callMessage,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonReceiptMessage receiptMessage,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonTypingMessage typingMessage
) {

    public static JsonMessageEnvelope from(
            MessageEnvelope envelope, Throwable exception, Manager m
    ) {
        final String source;
        final String sourceNumber;
        final String sourceUuid;
        final Integer sourceDevice;
        if (envelope.sourceAddress().isPresent()) {
            final var sourceAddress = envelope.sourceAddress().get();
            source = sourceAddress.getLegacyIdentifier();
            sourceNumber = sourceAddress.getNumber().orElse(null);
            sourceUuid = sourceAddress.getUuid().map(UUID::toString).orElse(null);
            sourceDevice = envelope.sourceDevice();
        } else if (exception instanceof UntrustedIdentityException e) {
            final var sender = e.getSender();
            source = sender.getLegacyIdentifier();
            sourceNumber = sender.getNumber().orElse(null);
            sourceUuid = sender.getUuid().map(UUID::toString).orElse(null);
            sourceDevice = e.getSenderDevice();
        } else {
            source = null;
            sourceNumber = null;
            sourceUuid = null;
            sourceDevice = null;
        }
        String name;
        try {
            name = m.getContactOrProfileName(RecipientIdentifier.Single.fromString(source, m.getSelfNumber()));
        } catch (InvalidNumberException | NullPointerException e) {
            name = null;
        }
        final var sourceName = name;
        final var timestamp = envelope.timestamp();
        final var receiptMessage = envelope.receipt().map(JsonReceiptMessage::from).orElse(null);
        final var typingMessage = envelope.typing().map(JsonTypingMessage::from).orElse(null);

        final var dataMessage = envelope.data().map(JsonDataMessage::from).orElse(null);
        final var syncMessage = envelope.sync().map(JsonSyncMessage::from).orElse(null);
        final var callMessage = envelope.call().map(JsonCallMessage::from).orElse(null);

        return new JsonMessageEnvelope(source,
                sourceNumber,
                sourceUuid,
                sourceName,
                sourceDevice,
                timestamp,
                dataMessage,
                syncMessage,
                callMessage,
                receiptMessage,
                typingMessage);
    }

    public static JsonMessageEnvelope from(Signal.MessageReceived messageReceived) {
        return new JsonMessageEnvelope(messageReceived.getSource(),
                null,
                null,
                null,
                null,
                messageReceived.getTimestamp(),
                JsonDataMessage.from(messageReceived),
                null,
                null,
                null,
                null);
    }

    public static JsonMessageEnvelope from(Signal.ReceiptReceived receiptReceived) {
        return new JsonMessageEnvelope(receiptReceived.getSender(),
                null,
                null,
                null,
                null,
                receiptReceived.getTimestamp(),
                null,
                null,
                null,
                JsonReceiptMessage.deliveryReceipt(receiptReceived.getTimestamp(),
                        List.of(receiptReceived.getTimestamp())),
                null);
    }

    public static JsonMessageEnvelope from(Signal.SyncMessageReceived messageReceived) {
        return new JsonMessageEnvelope(messageReceived.getSource(),
                null,
                null,
                null,
                null,
                messageReceived.getTimestamp(),
                null,
                JsonSyncMessage.from(messageReceived),
                null,
                null,
                null);
    }
}
