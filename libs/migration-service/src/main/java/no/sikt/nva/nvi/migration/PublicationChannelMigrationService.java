package no.sikt.nva.nvi.migration;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.service.CandidateService.defaultCandidateService;
import static nva.commons.core.StringUtils.isBlank;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.client.PublicationChannelRetriever;
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.model.PointCalculation;
import no.sikt.nva.nvi.common.model.PublicationChannel;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.publication.PublicationLoaderService;
import no.unit.nva.auth.uriretriever.UriRetriever;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PublicationChannelMigrationService implements MigrationService {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(PublicationChannelMigrationService.class);

  private static final String EXPANDED_RESOURCES_BUCKET = "EXPANDED_RESOURCES_BUCKET";
  private static final String MESSAGE_MIGRATION_NOT_NEEDED =
      "Candidate {} does not require migration";
  private static final String MESSAGE_MIGRATING_CANDIDATE =
      "Migrating candidate with identifier {}";
  private static final String MESSAGE_CHANNEL_NOT_IN_PUBLICATION =
      "Channel {} of candidate {} is not in the current publication, fetching channel metadata";
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

  @JacocoGenerated
  public static PublicationChannelMigrationService defaultService() {
    return new PublicationChannelMigrationService(
        defaultCandidateService(),
        new S3StorageReader(new Environment().readEnv(EXPANDED_RESOURCES_BUCKET)),
        new PublicationChannelRetriever(new UriRetriever()));
  }

  @Override
  public void migrateCandidate(UUID identifier) {
    var candidate = candidateService.getCandidateByIdentifier(identifier);
    var currentChannel = candidate.getPublicationChannel();
    if (!hasMissingChannelMetadata(currentChannel)) {
      LOGGER.info(MESSAGE_MIGRATION_NOT_NEEDED, identifier);
      return;
    }

    LOGGER.info(MESSAGE_MIGRATING_CANDIDATE, identifier);
    var publicationBucketUri = candidate.publicationDetails().publicationBucketUri();
    var publication = publicationLoader.extractAndTransform(publicationBucketUri);
    var updatedChannel = addMissingChannelMetadata(currentChannel, publication, identifier);
    if (!updatedChannel.equals(currentChannel)) {
      candidateService.updateCandidate(withChannel(candidate, updatedChannel));
    }
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
        .map(current -> withMetadata(channel, current.name(), current.printIssn()))
        .or(() -> fetchChannelMetadata(channel, identifier))
        .orElseGet(() -> logMissingMetadata(channel, identifier));
  }

  private Optional<PublicationChannel> fetchChannelMetadata(
      PublicationChannel channel, UUID identifier) {
    LOGGER.info(MESSAGE_CHANNEL_NOT_IN_PUBLICATION, channel.id(), identifier);
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
