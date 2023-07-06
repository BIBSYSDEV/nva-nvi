package no.sikt.nva.nvi.evaluator.calculator;

import java.net.URI;
import java.util.List;
import java.util.Set;

public record NviCandidate(Set<URI> approvalAffiliations) implements CandidateType {

}