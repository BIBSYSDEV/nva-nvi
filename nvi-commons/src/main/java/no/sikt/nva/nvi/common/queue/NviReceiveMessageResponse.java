package no.sikt.nva.nvi.common.queue;

import java.util.List;

public record NviReceiveMessageResponse(List<NviReceiveMessage> messages) {}
