package no.sikt.nva.nvi.events;

import java.net.URI;
import java.util.List;

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
