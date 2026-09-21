package no.sikt.nva.nvi.migration;

import static java.util.Objects.nonNull;
import static nva.commons.core.StringUtils.isBlank;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.client.PublicationChannelRetriever;
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.model.PointCalculation;
import no.sikt.nva.nvi.common.model.PublicationChannel;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.publication.PublicationLoaderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Backfills missing publication channel name and print ISSN (NP-51402) from the expanded
 * publication stored in S3, falling back to the publication channel registry.
 */
public final class PublicationChannelMigrationService implements MigrationService {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(PublicationChannelMigrationService.class);

  private static final String MESSAGE_MIGRATION_NOT_NEEDED =
      "Candidate {} does not require channel metadata migration";
  private static final String MESSAGE_MIGRATING_CANDIDATE =
      "Migrating channel metadata for candidate {}";
  private static final String MESSAGE_CHANNEL_NOT_IN_PUBLICATION =
      "Channel {} of candidate {} is not in the current publication, fetching channel metadata."
          + " Channels in the publication {}";
  private static final String MESSAGE_NO_CHANNEL_METADATA_FOUND =
      "Found no metadata for channel {} of candidate {}";
  private final CandidateService candidateService;
  private final PublicationLoaderService publicationLoader;
  private final PublicationChannelRetriever channelRetriever;

  public PublicationChannelMigrationService(
      CandidateService candidateService,
      StorageReader<URI> storageReader,
      PublicationChannelRetriever channelRetriever) {
    this.candidateService = candidateService;
    this.publicationLoader = new PublicationLoaderService(storageReader);
    this.channelRetriever = channelRetriever;
  }

  /** Always writes the candidate back, so untouched candidates are still reindexed. */
  @Override
  public void migrateCandidate(UUID identifier) {
    var candidate = candidateService.getCandidateByIdentifier(identifier);
    var currentChannel = candidate.getPublicationChannel();
    if (!hasMissingChannelMetadata(currentChannel)) {
      LOGGER.info(MESSAGE_MIGRATION_NOT_NEEDED, identifier);
      candidateService.updateCandidate(candidate);
      return;
    }

    LOGGER.info(MESSAGE_MIGRATING_CANDIDATE, identifier);
    var publicationBucketUri = candidate.publicationDetails().publicationBucketUri();
    var publication = publicationLoader.extractAndTransform(publicationBucketUri);
    var updatedChannel = addMissingChannelMetadata(currentChannel, publication, identifier);
    var migratedCandidate =
        updatedChannel.equals(currentChannel) ? candidate : withChannel(candidate, updatedChannel);
    candidateService.updateCandidate(migratedCandidate);
  }

  private static Candidate withChannel(Candidate candidate, PublicationChannel channel) {
    var updatedDetails =
        candidate.publicationDetails().copy().withPublicationChannel(channel).build();
    return candidate
        .copy()
        .withPublicationDetails(updatedDetails)
        .withPointCalculation(withChannel(candidate.pointCalculation(), channel))
        .withModifiedDate(Instant.now())
        .build();
  }

  private static PointCalculation withChannel(
      PointCalculation pointCalculation, PublicationChannel channel) {
    return new PointCalculation(
        pointCalculation.instanceType(),
        channel,
        pointCalculation.isInternationalCollaboration(),
        pointCalculation.collaborationFactor(),
        pointCalculation.basePoints(),
        pointCalculation.creatorShareCount(),
        pointCalculation.institutionPoints(),
        pointCalculation.totalPoints());
  }

  private PublicationChannel addMissingChannelMetadata(
      PublicationChannel channel, PublicationDto publication, UUID identifier) {
    return findChannelInPublication(publication, channel.id())
        .map(
            publicationChannelDto ->
                withMetadata(
                    channel, publicationChannelDto.name(), publicationChannelDto.printIssn()))
        .or(() -> fetchChannelMetadata(channel, identifier, publication))
        .orElseGet(() -> logMissingMetadata(channel, identifier));
  }

  private Optional<PublicationChannel> fetchChannelMetadata(
      PublicationChannel channel, UUID identifier, PublicationDto publication) {
    LOGGER.info(
        MESSAGE_CHANNEL_NOT_IN_PUBLICATION,
        channel.id(),
        identifier,
        publication.publicationChannels().stream()
            .map(
                channelDto ->
                    String.format(
                        "Channel: %s %s %s",
                        channelDto.id(), channelDto.name(), channelDto.printIssn()))
            .toList());
    return channelRetriever
        .fetchChannel(channel.id())
        .filter(fetchedChannel -> !isBlank(fetchedChannel.name()))
        .map(
            fetchedChannel ->
                withMetadata(channel, fetchedChannel.name(), fetchedChannel.printIssn()));
  }

  private static PublicationChannel logMissingMetadata(
      PublicationChannel channel, UUID identifier) {
    LOGGER.info(MESSAGE_NO_CHANNEL_METADATA_FOUND, channel.id(), identifier);
    return channel;
  }

  private static PublicationChannel withMetadata(
      PublicationChannel channel, String name, String printIssn) {
    return new PublicationChannel(
        channel.id(), channel.channelType(), channel.scientificValue(), name, printIssn);
  }

  private static boolean hasMissingChannelMetadata(PublicationChannel channel) {
    return nonNull(channel) && nonNull(channel.id()) && isBlank(channel.name());
  }

  private static Optional<PublicationChannelDto> findChannelInPublication(
      PublicationDto publication, URI channelId) {
    return publication.publicationChannels().stream()
        .filter(channel -> channelId.equals(channel.id()))
        .filter(channel -> !isBlank(channel.name()))
        .findAny();
  }
}
