package no.sikt.nva.nvi.common.model;

import java.util.UUID;
import no.sikt.nva.nvi.common.model.business.Candidate;

public record CandidateWithIdentifier(Candidate candidate, UUID identifier) {

}