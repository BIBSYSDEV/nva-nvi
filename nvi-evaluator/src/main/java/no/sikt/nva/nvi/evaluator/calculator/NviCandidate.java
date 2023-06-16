package no.sikt.nva.nvi.evaluator.calculator;

import java.util.Objects;
import no.sikt.nva.nvi.evaluator.model.CandidateResponse;

public record NviCandidate(CandidateResponse response) implements CandidateType {

}