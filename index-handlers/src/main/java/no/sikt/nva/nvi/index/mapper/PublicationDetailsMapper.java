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
        .withPages(Pages.from(candidateDetails.pageCount()))
        .withLanguage(candidateDetails.language())
        .withHandles(candidateDetails.handles())
        .build();
  }

  private PublicationChannel buildPublicationChannel() {
    var persistedChannel = candidate.getPublicationChannel();
    return findMatchingPublicationChannelDto(persistedChannel)
        .map(currentChannel -> PublicationChannel.from(persistedChannel, currentChannel))
        .orElseGet(() -> PublicationChannel.from(persistedChannel));
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
        ? publicationChannels().stream().filter(dto -> id.equals(dto.id())).findAny()
        : Optional.empty();
  }

  private Optional<PublicationChannelDto> findChannelDtoByType(ChannelType type) {
    return nonNull(type) && type.isValid()
        ? publicationChannels().stream().filter(dto -> type == dto.channelType()).findAny()
        : Optional.empty();
  }

  private Collection<PublicationChannelDto> publicationChannels() {
    return isNull(publicationDto) ? emptyList() : publicationDto.publicationChannels();
  }
}
