package no.sikt.nva.nvi.index.utils;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import java.net.URI;
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

final class PublicationDetailsMapper {

  private final Candidate candidate;
  private final PublicationDto publicationDto;

  PublicationDetailsMapper(Candidate candidate, PublicationDto publicationDto) {
    this.candidate = candidate;
    this.publicationDto = publicationDto;
  }

  PublicationDetails mapPublicationDetails(List<ContributorType> contributors) {
    var candidateDetails = candidate.publicationDetails();
    return PublicationDetails.builder()
        .withId(candidateDetails.publicationId().toString())
        .withType(candidate.getPublicationType().getValue())
        .withTitle(candidateDetails.title())
        .withAbstract(candidateDetails.abstractText())
        .withPublicationDate(candidateDetails.publicationDate().toDtoPublicationDate())
        .withContributors(contributors)
        .withContributorsCount(candidateDetails.creatorCount())
        .withPublicationChannel(buildPublicationChannel())
        .withPages(buildPages(candidateDetails.pageCount()))
        .withLanguage(candidateDetails.language())
        .withHandles(candidateDetails.handles())
        .build();
  }

  private static Pages buildPages(PageCount pageCount) {
    if (isNull(pageCount)) {
      return null;
    }
    return Pages.builder()
        .withBegin(pageCount.first())
        .withEnd(pageCount.last())
        .withNumberOfPages(pageCount.total())
        .build();
  }

  private PublicationChannel buildPublicationChannel() {
    var channel = candidate.getPublicationChannel();
    var builder =
        PublicationChannel.builder()
            .withScientificValue(ScientificValue.parse(channel.scientificValue().getValue()));

    if (nonNull(channel.id())) {
      builder.withId(channel.id());
    }
    if (nonNull(channel.channelType())) {
      builder.withType(channel.channelType().getValue());
    }

    findMatchingPublicationChannelDto(channel.id(), channel.channelType())
        .ifPresent(
            dto -> {
              builder.withName(dto.name());
              builder.withPrintIssn(dto.printIssn());
            });

    return builder.build();
  }

  private Optional<PublicationChannelDto> findMatchingPublicationChannelDto(
      URI channelId, no.sikt.nva.nvi.common.model.ChannelType channelType) {
    if (nonNull(channelId)) {
      return publicationDto.publicationChannels().stream()
          .filter(dto -> channelId.equals(dto.id()))
          .findFirst();
    }
    if (nonNull(channelType)) {
      return publicationDto.publicationChannels().stream()
          .filter(dto -> channelType == dto.channelType())
          .findFirst();
    }
    return Optional.empty();
  }
}
