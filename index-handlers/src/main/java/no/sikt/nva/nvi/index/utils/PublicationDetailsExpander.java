package no.sikt.nva.nvi.index.utils;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.model.ScientificValue;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.PageCount;
import no.sikt.nva.nvi.index.model.document.ContributorType;
import no.sikt.nva.nvi.index.model.document.Pages;
import no.sikt.nva.nvi.index.model.document.PublicationChannel;
import no.sikt.nva.nvi.index.model.document.PublicationDetails;

final class PublicationDetailsExpander {

  private final Candidate candidate;
  private final PublicationDto publicationDto;

  PublicationDetailsExpander(Candidate candidate, PublicationDto publicationDto) {
    this.candidate = candidate;
    this.publicationDto = publicationDto;
  }

  PublicationDetails expandPublicationDetails(List<ContributorType> contributors) {
    var publicationDetails = candidate.publicationDetails();
    return PublicationDetails.builder()
        .withId(publicationDetails.publicationId().toString())
        .withContributors(contributors)
        .withType(candidate.getPublicationType().getValue())
        .withPublicationDate(publicationDetails.publicationDate().toDtoPublicationDate())
        .withTitle(publicationDetails.title())
        .withAbstract(publicationDetails.abstractText())
        .withPublicationChannel(buildPublicationChannel())
        .withPages(buildPages(publicationDetails.pageCount()))
        .withLanguage(publicationDetails.language())
        .withHandles(new HashSet<>(publicationDetails.handles()))
        .build();
  }

  private static Pages buildPages(PageCount pageCount) {
    if (isNull(pageCount)) {
      return Pages.builder().build();
    }
    return Pages.builder()
        .withBegin(pageCount.first())
        .withEnd(pageCount.last())
        .withNumberOfPages(pageCount.total())
        .build();
  }

  private PublicationChannel buildPublicationChannel() {
    var publicationChannel = candidate.getPublicationChannel();
    var channelBuilder =
        PublicationChannel.builder()
            .withScientificValue(
                ScientificValue.parse(publicationChannel.scientificValue().getValue()));

    if (nonNull(publicationChannel.id())) {
      channelBuilder.withId(publicationChannel.id());
    }
    if (nonNull(publicationChannel.channelType())) {
      channelBuilder.withType(publicationChannel.channelType().getValue());
      var matchedChannel = findMatchingPublicationChannel(publicationChannel.id());
      channelBuilder.withName(matchedChannel.map(PublicationChannelDto::name).orElse(null));
      channelBuilder.withPrintIssn(
          matchedChannel.map(PublicationChannelDto::printIssn).orElse(null));
    }
    return channelBuilder.build();
  }

  private Optional<PublicationChannelDto> findMatchingPublicationChannel(URI channelId) {
    return publicationDto.publicationChannels().stream()
        .filter(channel -> channel.id().equals(channelId))
        .findFirst();
  }
}
