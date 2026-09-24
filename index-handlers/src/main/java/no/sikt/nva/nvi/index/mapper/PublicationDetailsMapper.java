package no.sikt.nva.nvi.index.mapper;

import java.util.List;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.index.model.document.Contributor;
import no.sikt.nva.nvi.index.model.document.NviContributor;
import no.sikt.nva.nvi.index.model.document.Pages;
import no.sikt.nva.nvi.index.model.document.PublicationChannel;
import no.sikt.nva.nvi.index.model.document.PublicationDetails;

final class PublicationDetailsMapper {

  private final Candidate candidate;

  PublicationDetailsMapper(Candidate candidate) {
    this.candidate = candidate;
  }

  PublicationDetails mapPublicationDetails(
      List<Contributor> contributors, List<NviContributor> nviContributors) {
    var candidateDetails = candidate.publicationDetails();
    return PublicationDetails.builder()
        .withId(candidateDetails.publicationId().toString())
        .withType(candidate.getPublicationType().getValue())
        .withTitle(candidateDetails.title())
        .withAbstract(candidateDetails.abstractText())
        .withPublicationDate(candidateDetails.publicationDate().toDtoPublicationDate())
        .withContributors(contributors)
        .withNviContributors(nviContributors)
        .withPublicationChannel(buildPublicationChannel())
        .withPages(Pages.from(candidateDetails.pageCount()))
        .withLanguage(candidateDetails.language())
        .withHandles(candidateDetails.handles())
        .build();
  }

  private PublicationChannel buildPublicationChannel() {
    return PublicationChannel.from(candidate.getPublicationChannel());
  }
}
