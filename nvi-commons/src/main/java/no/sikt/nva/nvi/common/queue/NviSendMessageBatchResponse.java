package no.sikt.nva.nvi.common.queue;

import java.util.List;

public record NviSendMessageBatchResponse(List<String> successful, List<String> failed) {

}
