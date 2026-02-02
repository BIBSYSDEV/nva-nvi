package no.sikt.nva.nvi.common.queue;

import no.unit.nva.commons.json.JsonSerializable;

public record QueueMessage(JsonSerializable body, QueueMessageAttributes attributes) {}
