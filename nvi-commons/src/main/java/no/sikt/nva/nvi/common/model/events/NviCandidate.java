package no.sikt.nva.nvi.common.model.events;

import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.model.events.Publication.EntityDescription.PublicationDate;
import nva.commons.core.JacocoGenerated;

@JacocoGenerated
public record NviCandidate(CandidateDetails candidateDetails) implements CandidateType {

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