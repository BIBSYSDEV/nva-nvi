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
import no.sikt.nva.nvi.common.db.model.DbPublicationChannel;
import no.sikt.nva.nvi.common.db.model.DbPublicationDate;
import no.sikt.nva.nvi.common.db.model.DbPublicationDetails;
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
    var publicationDetails = randomPublicationBuilder(organizationId).build();
    return randomCandidateBuilder(organizationId, publicationDetails).applicable(applicable);
  }

  public static DbCandidate randomCandidateWithYear(String year) {
    var organizationId = randomUri();
    var publicationDetails =
        randomPublicationBuilder(organizationId).publicationDate(publicationDate(year)).build();
    return randomCandidateBuilder(organizationId, publicationDetails).applicable(true).build();
  }

  public static DbCandidate.Builder randomCandidateBuilder(
      URI organizationId, DbPublicationDetails publicationDetails) {
    var creatorId = randomUri();
    return DbCandidate.builder()
        .publicationId(publicationDetails.id())
        .publicationBucketUri(publicationDetails.publicationBucketUri())
        .publicationIdentifier(publicationDetails.identifier())
        .publicationDetails(publicationDetails)
        .applicable(true)
        .instanceType(publicationDetails.publicationType())
        .points(List.of(generateInstitutionPoints(organizationId, creatorId)))
        .level(DbLevel.LEVEL_ONE)
        .channelType(randomElement(ChannelType.values()).getValue())
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

  public static DbPublicationDetails.Builder randomPublicationBuilder(URI organizationId) {
    var creatorId = randomUri();
    var publicationIdentifier = randomUUID();
    var publicationId = generatePublicationId(publicationIdentifier);
    var channel =
        DbPublicationChannel.builder()
            .id(randomUri())
            .channelType(randomElement(ChannelType.values()).getValue())
            .scientificValue(ScientificValue.LEVEL_ONE.getValue())
            .build();
    return DbPublicationDetails.builder()
        .id(publicationId)
        .identifier(publicationIdentifier.toString())
        .publicationBucketUri(randomUri())
        .publicationType(randomInstanceType().getValue())
        .publicationChannel(channel)
        .publicationDate(publicationDate(String.valueOf(CURRENT_YEAR)))
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
