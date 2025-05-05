package no.sikt.nva.nvi.common.db;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.model.InstanceTypeFixtures.randomInstanceType;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.generatePublicationId;
import static no.unit.nva.testutils.RandomDataGenerator.randomBoolean;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreator;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints.DbCreatorAffiliationPoints;
import no.sikt.nva.nvi.common.db.CandidateDao.DbLevel;
import no.sikt.nva.nvi.common.db.model.DbPublication;
import no.sikt.nva.nvi.common.db.model.DbPublicationChannel;
import no.sikt.nva.nvi.common.db.model.DbPublicationDate;
import no.sikt.nva.nvi.common.model.ChannelType;
import no.sikt.nva.nvi.common.model.ScientificValue;
import no.sikt.nva.nvi.test.TestUtils;

public class DbCandidateFixtures {

  public static DbCandidate randomCandidate() {
    return randomCandidateBuilder(true).build();
  }

  public static DbCandidate.Builder randomCandidateBuilder(boolean applicable) {
    return randomCandidateBuilder(applicable, randomUri());
  }

  public static DbCandidate.Builder randomCandidateBuilder(boolean applicable, URI organizationId) {
    var publicationDetails = randomPublicationBuilder(applicable, organizationId).build();
    return randomCandidateBuilder(organizationId, publicationDetails);
  }

  public static DbCandidate randomCandidateWithYear(String year) {
    var organizationId = randomUri();
    var publicationDetails =
        randomPublicationBuilder(true, organizationId)
            .publicationDate(publicationDate(year))
            .build();
    return randomCandidateBuilder(organizationId, publicationDetails).build();
  }

  public static DbCandidate.Builder randomCandidateBuilder(
      URI organizationId, DbPublication publicationDetails) {
    var creatorId = randomUri();
    return DbCandidate.builder()
        .publicationId(publicationDetails.id())
        .publicationBucketUri(publicationDetails.publicationBucketUri())
        .publicationIdentifier(publicationDetails.identifier())
        .publicationDetails(publicationDetails)
        .applicable(publicationDetails.applicable())
        .instanceType(publicationDetails.publicationType().getValue())
        .points(List.of(generateInstitutionPoints(organizationId, creatorId)))
        .level(DbLevel.LEVEL_ONE)
        .channelType(randomElement(ChannelType.values()))
        .channelId(randomUri())
        .publicationDate(publicationDetails.publicationDate())
        .internationalCollaboration(randomBoolean())
        .creatorCount(randomInteger())
        .createdDate(Instant.now())
        .modifiedDate(Instant.now())
        .totalPoints(TestUtils.randomBigDecimal())
        .creators(
            List.of(
                DbCreator.builder()
                    .creatorId(creatorId)
                    .affiliations(List.of(organizationId))
                    .build()));
  }

  public static DbPublication.Builder randomPublicationBuilder(
      boolean applicable, URI organizationId) {
    var creatorId = randomUri();
    var publicationIdentifier = randomUUID();
    var publicationId = generatePublicationId(publicationIdentifier);
    var channel =
        DbPublicationChannel.builder()
            .id(randomUri())
            .channelType(randomElement(ChannelType.values()))
            .scientificValue(ScientificValue.LEVEL_ONE.getValue())
            .build();
    return DbPublication.builder()
        .id(publicationId)
        .identifier(publicationIdentifier.toString())
        .publicationBucketUri(randomUri())
        .applicable(applicable)
        .publicationType(randomInstanceType())
        .publicationChannel(channel)
        .publicationDate(publicationDate(String.valueOf(CURRENT_YEAR)))
        .internationalCollaboration(randomBoolean())
        .modifiedDate(Instant.now())
        .creators(
            List.of(
                DbCreator.builder()
                    .creatorId(creatorId)
                    .affiliations(List.of(organizationId))
                    .build()));
  }

  private static DbPublicationDate publicationDate(String year) {
    return new DbPublicationDate(year, null, null);
  }

  private static DbInstitutionPoints generateInstitutionPoints(URI institutionId, URI creatorId) {
    var points = BigDecimal.ONE;
    return DbInstitutionPoints.builder()
        .institutionId(randomUri())
        .points(points)
        .institutionId(institutionId)
        .points(points)
        .creatorAffiliationPoints(
            List.of(new DbCreatorAffiliationPoints(creatorId, institutionId, points)))
        .build();
  }
}
