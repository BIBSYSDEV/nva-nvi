package no.sikt.nva.nvi.events.batch;

import java.util.Optional;
import no.sikt.nva.nvi.common.queue.NviReceiveMessage;

public record NviProcessMessageResult(
    NviReceiveMessage message, boolean success, Optional<String> error) {}
