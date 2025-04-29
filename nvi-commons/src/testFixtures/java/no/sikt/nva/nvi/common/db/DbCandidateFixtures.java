package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.model.InstanceTypeFixtures.randomInstanceType;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.testutils.RandomDataGenerator.randomBoolean;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreator;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints.DbCreatorAffiliationPoints;
import no.sikt.nva.nvi.common.db.CandidateDao.DbLevel;
import no.sikt.nva.nvi.common.db.model.ChannelType;
import no.sikt.nva.nvi.common.db.model.DbPublicationDate;
import no.sikt.nva.nvi.test.TestUtils;

public class DbCandidateFixtures {

  public static DbCandidate randomCandidate() {
    return randomCandidateBuilder(true).build();
  }

  public static DbCandidate.Builder randomCandidateBuilder(boolean applicable) {
    return randomCandidateBuilder(applicable, randomUri());
  }

  public static DbCandidate.Builder randomCandidateBuilder(boolean applicable, URI institutionId) {
    var creatorId = randomUri();
    var institutionPoints = TestUtils.randomBigDecimal();
    var instanceType = randomInstanceType().getValue();
    var publicationDate = new DbPublicationDate(String.valueOf(CURRENT_YEAR), "10", "10");
    return DbCandidate.builder()
        .publicationId(randomUri())
        .publicationBucketUri(randomUri())
        .applicable(applicable)
        .instanceType(instanceType)
        .points(
            List.of(
                new DbInstitutionPoints(
                    institutionId,
                    institutionPoints,
                    List.of(
                        new DbCreatorAffiliationPoints(
                            creatorId, institutionId, institutionPoints)))))
        .level(DbLevel.LEVEL_ONE)
        .channelType(randomElement(ChannelType.values()))
        .channelId(randomUri())
        .publicationDate(publicationDate)
        .internationalCollaboration(randomBoolean())
        .creatorCount(randomInteger())
        .createdDate(Instant.now())
        .modifiedDate(Instant.now())
        .totalPoints(TestUtils.randomBigDecimal())
        .creators(
            List.of(
                DbCreator.builder()
                    .creatorId(creatorId)
                    .affiliations(List.of(institutionId))
                    .build()));
  }

  public static DbCandidate randomCandidateWithYear(String year) {
    return randomCandidateBuilder(true).publicationDate(publicationDate(year)).build();
  }

  private static DbPublicationDate publicationDate(String year) {
    return new DbPublicationDate(year, null, null);
  }
}
