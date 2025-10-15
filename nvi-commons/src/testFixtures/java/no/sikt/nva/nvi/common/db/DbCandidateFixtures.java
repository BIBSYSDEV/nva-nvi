package no.sikt.nva.nvi.common.db;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.db.DbPointCalculationFixtures.getExpectedPointCalculation;
import static no.sikt.nva.nvi.common.db.DbPointCalculationFixtures.randomPointCalculationBuilder;
import static no.sikt.nva.nvi.common.db.DbPublicationDetailsFixtures.getExpectedPublicationDetails;
import static no.sikt.nva.nvi.common.db.DbPublicationDetailsFixtures.randomPublicationBuilder;
import static no.sikt.nva.nvi.common.model.NviCreatorFixtures.mapToDbCreators;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreator;
import no.sikt.nva.nvi.common.db.CandidateDao.DbLevel;
import no.sikt.nva.nvi.common.db.model.DbPointCalculation;
import no.sikt.nva.nvi.common.db.model.DbPublicationDate;
import no.sikt.nva.nvi.common.db.model.DbPublicationDetails;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.service.model.Candidate;

public class DbCandidateFixtures {

  public static DbCandidate randomCandidate() {
    return randomCandidateBuilder(true).build();
  }

  public static DbCandidate.Builder randomCandidateBuilder(boolean applicable) {
    return randomCandidateBuilder(applicable, randomUri());
  }

  public static DbCandidate.Builder randomCandidateBuilder(boolean applicable, URI organizationId) {
    var publicationDetails = randomPublicationBuilder(organizationId).build();
    return randomCandidateBuilder(organizationId, publicationDetails).applicable(applicable);
  }

  public static DbCandidate randomCandidateWithYear(String year) {
    var organizationId = randomUri();
    var publicationDetails =
        randomPublicationBuilder(organizationId)
            .publicationDate(new DbPublicationDate(year, null, null))
            .build();
    return randomCandidateBuilder(organizationId, publicationDetails).applicable(true).build();
  }

  public static DbCandidate.Builder randomCandidateBuilder(
      URI organizationId, DbPublicationDetails publicationDetails) {
    var creatorId = randomUri();
    var pointCalculation = randomPointCalculationBuilder(creatorId, organizationId).build();
    return randomCandidateBuilder(organizationId, publicationDetails, pointCalculation);
  }

  public static DbCandidate.Builder randomCandidateBuilder(
      URI organizationId,
      DbPublicationDetails publicationDetails,
      DbPointCalculation pointCalculation) {
    var creatorId = randomUri();
    return DbCandidate.builder()
        .publicationId(publicationDetails.id())
        .publicationBucketUri(publicationDetails.publicationBucketUri())
        .publicationIdentifier(publicationDetails.identifier())
        .pointCalculation(pointCalculation)
        .publicationDetails(publicationDetails)
        .applicable(true)
        .instanceType(pointCalculation.instanceType())
        .points(pointCalculation.institutionPoints())
        .level(DbLevel.LEVEL_ONE)
        .channelType(pointCalculation.publicationChannel().channelType())
        .channelId(pointCalculation.publicationChannel().id())
        .publicationDate(publicationDetails.publicationDate())
        .internationalCollaboration(pointCalculation.internationalCollaboration())
        .creatorCount(pointCalculation.creatorShareCount())
        .createdDate(Instant.now())
        .modifiedDate(Instant.now())
        .totalPoints(pointCalculation.totalPoints())
        .creators(
            List.of(
                DbCreator.builder()
                    .creatorId(creatorId)
                    .affiliations(List.of(organizationId))
                    .build()));
  }

  public static DbCandidate getExpectedUpdatedDbCandidate(
      Candidate candidate, UpsertNviCandidateRequest request) {
    return getExpectedDbCandidate(candidate.identifier(), candidate.createdDate(), request);
  }

  public static DbCandidate getExpectedDbCandidate(
      UUID candidateIdentifier, Instant createdDate, UpsertNviCandidateRequest request) {
    return getExpectedCandidateDao(candidateIdentifier, createdDate, request).candidate();
  }

  public static CandidateDao getExpectedCandidateDao(
      UUID candidateIdentifier, Instant createdDate, UpsertNviCandidateRequest request) {
    var dtoPublicationDetails = request.publicationDetails();
    var dbCreators = mapToDbCreators(request.verifiedCreators(), request.unverifiedCreators());
    var dbPointCalculation = getExpectedPointCalculation(request);
    var dbPublicationDetails = getExpectedPublicationDetails(request);
    var dbChannel = dbPointCalculation.publicationChannel();
    var dbCandidate =
        DbCandidate.builder()
            .publicationId(request.publicationId())
            .publicationIdentifier(dbPublicationDetails.identifier())
            .publicationBucketUri(request.publicationBucketUri())
            .pointCalculation(dbPointCalculation)
            .publicationDetails(dbPublicationDetails)
            .publicationDate(dbPublicationDetails.publicationDate())
            .applicable(dtoPublicationDetails.isApplicable())
            .instanceType(dbPointCalculation.instanceType())
            .channelType(dbChannel.channelType())
            .channelId(dbChannel.id())
            .level(DbLevel.parse(dbChannel.scientificValue()))
            .basePoints(dbPointCalculation.basePoints())
            .internationalCollaboration(dbPointCalculation.internationalCollaboration())
            .collaborationFactor(dbPointCalculation.collaborationFactor())
            .creators(dbCreators)
            .creatorShareCount(dbPointCalculation.creatorShareCount())
            .points(dbPointCalculation.institutionPoints())
            .totalPoints(dbPointCalculation.totalPoints())
            .createdDate(createdDate)
            .build();
    return CandidateDao.builder()
        .identifier(candidateIdentifier)
        .candidate(dbCandidate)
        .version(randomUUID().toString())
        .periodYear(randomYear())
        .build();
  }
}
