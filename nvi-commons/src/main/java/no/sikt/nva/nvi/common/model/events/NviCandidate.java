package no.sikt.nva.nvi.common.model.events;

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
                              //TODO: Do we need nviInstitutions?
                              List<URI> nviInstitutions) {

        }

        public record PublicationDate(String day,
                                      String month,
                                      String year) {

        }
    }
}