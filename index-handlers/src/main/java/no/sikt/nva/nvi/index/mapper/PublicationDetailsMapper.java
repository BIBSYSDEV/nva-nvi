package no.sikt.nva.nvi.index.mapper;

import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.model.ChannelType;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.PageCount;
import no.sikt.nva.nvi.index.model.document.Contributor;
import no.sikt.nva.nvi.index.model.document.NviContributor;
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
            .withScientificValue(channel.scientificValue())
            .withId(channel.id())
            .withType(channel.channelType());

    findMatchingPublicationChannelDto(channel)
        .ifPresent(
            dto -> {
              builder.withName(dto.name());
              builder.withPrintIssn(dto.printIssn());
            });

    return builder.build();
  }

  /**
   * The by-type fallback covers candidates without a channel ID (Cristin imports) and candidates
   * whose channel ID no longer matches the Publication (channel merged or superseded)
   */
  private Optional<PublicationChannelDto> findMatchingPublicationChannelDto(
      no.sikt.nva.nvi.common.model.PublicationChannel channel) {
    return findChannelDtoById(channel.id()).or(() -> findChannelDtoByType(channel.channelType()));
  }

  private Optional<PublicationChannelDto> findChannelDtoById(URI id) {
    return nonNull(id)
        ? publicationChannels().stream().filter(dto -> id.equals(dto.id())).findFirst()
        : Optional.empty();
  }

  private Optional<PublicationChannelDto> findChannelDtoByType(ChannelType type) {
    return nonNull(type) && type.isValid()
        ? publicationChannels().stream().filter(dto -> type == dto.channelType()).findFirst()
        : Optional.empty();
  }

  private Collection<PublicationChannelDto> publicationChannels() {
    return isNull(publicationDto) ? emptyList() : publicationDto.publicationChannels();
  }
}
