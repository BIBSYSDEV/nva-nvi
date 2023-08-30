package no.sikt.nva.nvi.rest;

import java.net.URI;

public record NviStatusRequest(URI candidateId,
                               URI institutionId,
                               NviApprovalStatus status) {

}
