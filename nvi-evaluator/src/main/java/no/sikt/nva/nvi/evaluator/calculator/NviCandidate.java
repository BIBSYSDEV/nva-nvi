package no.sikt.nva.nvi.evaluator.calculator;

import java.net.URI;
import java.util.List;

public record NviCandidate(List<URI> approvalAffiliations) implements CandidateType {

}