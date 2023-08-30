package no.sikt.nva.nvi.rest;

import java.net.URI;
import java.util.UUID;

public record NviStatusRequest(UUID candidateId,
                               URI institutionId,
                               NviApprovalStatus status) {

}
