package no.sikt.nva.nvi.evaluator.calculator;

import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.evaluator.calculator.model.Publication.EntityDescription.PublicationDate;

public record NviCandidate(List<URI> approvalInstitutions,
                           CandidateDetails candidateDetails) implements CandidateType {

    public record CandidateDetails(URI publicationId,
                                   String instanceType,
                                   String level,
                                   PublicationDate publicationDate,
                                   List<Creator> verifiedCreators) {

        public record Creator(URI id,
                              List<URI> institutions) {

        }
    }
}