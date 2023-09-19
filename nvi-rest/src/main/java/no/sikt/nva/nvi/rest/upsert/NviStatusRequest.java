package no.sikt.nva.nvi.rest.upsert;

import java.net.URI;
import java.util.UUID;

public record NviStatusRequest(URI institutionId,
                               NviApprovalStatus status) {

}
