package no.sikt.nva.nvi.index.utils;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.model.ChannelType;
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

    findMatchingPublicationChannelDto(channel)
        .ifPresent(
            dto -> {
              builder.withName(dto.name());
              builder.withPrintIssn(dto.printIssn());
            });

    return builder.build();
  }

  private Optional<PublicationChannelDto> findMatchingPublicationChannelDto(
      no.sikt.nva.nvi.common.model.PublicationChannel channel) {
    if (nonNull(channel.id())) {
      return findChannelDtoById(channel.id());
    }
    if (nonNull(channel.channelType())) {
      return findChannelDtoByType(channel.channelType());
    }
    return Optional.empty();
  }

  private Optional<PublicationChannelDto> findChannelDtoById(URI id) {
    return publicationDto.publicationChannels().stream()
        .filter(dto -> id.equals(dto.id()))
        .findFirst();
  }

  // Defensive fallback for Cristin-imported candidates that have a channel type but no channel id.
  private Optional<PublicationChannelDto> findChannelDtoByType(ChannelType type) {
    return publicationDto.publicationChannels().stream()
        .filter(dto -> type == dto.channelType())
        .findFirst();
  }
}
