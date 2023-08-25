package no.sikt.nva.nvi.common.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.model.business.Candidate;
import nva.commons.core.JacocoGenerated;

public record CandidateWithIdentifier(Candidate candidate, UUID identifier) {

}