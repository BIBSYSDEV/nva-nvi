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
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.model.PointCalculation;
import no.sikt.nva.nvi.common.model.PublicationChannel;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.publication.PublicationLoaderService;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PublicationChannelMigrationService implements MigrationService {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(PublicationChannelMigrationService.class);

  private static final String EXPANDED_RESOURCES_BUCKET = "EXPANDED_RESOURCES_BUCKET";
  private final CandidateService candidateService;
  private final PublicationLoaderService publicationLoader;

  public PublicationChannelMigrationService(
      CandidateService candidateService, StorageReader<URI> storageReader) {
    this.candidateService = candidateService;
    this.publicationLoader = new PublicationLoaderService(storageReader);
  }

  @JacocoGenerated
  public static PublicationChannelMigrationService defaultService() {
    return new PublicationChannelMigrationService(
        defaultCandidateService(),
        new S3StorageReader(new Environment().readEnv(EXPANDED_RESOURCES_BUCKET)));
  }

  @Override
  public void migrateCandidate(UUID identifier) {
    var candidate = candidateService.getCandidateByIdentifier(identifier);
    if (!hasMissingChannelMetadata(candidate.getPublicationChannel())) {
      LOGGER.info("Candidate {} does not require migration", identifier);
      return;
    }

    LOGGER.info("Migrating candidate with identifier {}", identifier);
    var publicationBucketUri = candidate.publicationDetails().publicationBucketUri();
    var publication = publicationLoader.extractAndTransform(publicationBucketUri);
    var updatedChannel = addMissingChannelMetadata(candidate.getPublicationChannel(), publication);
    if (!updatedChannel.equals(candidate.getPublicationChannel())) {
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

  private static PublicationChannel addMissingChannelMetadata(
      PublicationChannel channel, PublicationDto publication) {
    return findChannelById(publication, channel.id())
        .map(
            current ->
                new PublicationChannel(
                    channel.id(),
                    channel.channelType(),
                    channel.scientificValue(),
                    current.name(),
                    current.printIssn()))
        .orElse(channel);
  }

  private static boolean hasMissingChannelMetadata(PublicationChannel channel) {
    return nonNull(channel) && nonNull(channel.id()) && isBlank(channel.name());
  }

  private static Optional<PublicationChannelDto> findChannelById(
      PublicationDto publication, URI channelId) {
    return publication.publicationChannels().stream()
        .filter(channel -> channelId.equals(channel.id()))
        .findAny();
  }
}
