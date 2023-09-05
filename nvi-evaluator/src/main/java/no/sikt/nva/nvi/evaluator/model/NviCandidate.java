package no.sikt.nva.nvi.evaluator.model;

import java.net.URI;
import java.util.List;
import nva.commons.core.JacocoGenerated;

@JacocoGenerated
public record NviCandidate(CandidateDetails candidateDetails) implements CandidateType {

    public record CandidateDetails(URI publicationId,
                                   String instanceType,
                                   String level,
                                   PublicationDate publicationDate,
                                   List<Creator> verifiedCreators) {

        public record Creator(URI id,
                              List<URI> nviInstitutions) {

        }

        public record PublicationDate(String day,
                                      String month,
                                      String year) {

        }
    }
}