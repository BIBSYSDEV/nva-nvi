package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.db.DbPublicationChannelFixtures.getExpectedDbPublicationChannel;
import static no.sikt.nva.nvi.common.db.DbPublicationChannelFixtures.randomDbPublicationChannelBuilder;
import static no.sikt.nva.nvi.common.model.EnumFixtures.randomValidInstanceType;
import static no.sikt.nva.nvi.common.utils.DecimalUtils.adjustScaleAndRoundingMode;
import static no.unit.nva.testutils.RandomDataGenerator.randomBoolean;
import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints.DbCreatorAffiliationPoints;
import no.sikt.nva.nvi.common.db.model.DbPointCalculation;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.model.Sector;
import no.sikt.nva.nvi.test.TestUtils;

public class DbPointCalculationFixtures {

  public static DbPointCalculation.Builder randomPointCalculationBuilder(
      URI organizationId, URI creatorId) {
    var channel = randomDbPublicationChannelBuilder().build();
    return DbPointCalculation.builder()
        .basePoints(TestUtils.randomBigDecimal())
        .collaborationFactor(null)
        .totalPoints(TestUtils.randomBigDecimal())
        .publicationChannel(channel)
        .institutionPoints(List.of(generateInstitutionPoints(organizationId, creatorId)))
        .internationalCollaboration(randomBoolean())
        .creatorShareCount(randomInteger(100))
        .instanceType(randomValidInstanceType().getValue());
  }

  public static DbPointCalculation getExpectedPointCalculation(UpsertNviCandidateRequest request) {
    var channel = getExpectedDbPublicationChannel(request);
    var pointCalculation = request.pointCalculation();
    return DbPointCalculation.builder()
        .basePoints(adjustScaleAndRoundingMode(pointCalculation.basePoints()))
        .collaborationFactor(adjustScaleAndRoundingMode(pointCalculation.collaborationFactor()))
        .totalPoints(adjustScaleAndRoundingMode(pointCalculation.totalPoints()))
        .publicationChannel(channel)
        .institutionPoints(
            pointCalculation.institutionPoints().stream().map(DbInstitutionPoints::from).toList())
        .internationalCollaboration(pointCalculation.isInternationalCollaboration())
        .creatorShareCount(pointCalculation.creatorShareCount())
        .instanceType(request.pointCalculation().instanceType().getValue())
        .build();
  }

  private static DbInstitutionPoints generateInstitutionPoints(URI institutionId, URI creatorId) {
    var points = BigDecimal.ONE;
    return DbInstitutionPoints.builder()
        .institutionId(randomUri())
        .points(points)
        .institutionId(institutionId)
        .points(points)
        .sector(Sector.OTHER)
        .creatorAffiliationPoints(
            List.of(new DbCreatorAffiliationPoints(creatorId, institutionId, points)))
        .build();
  }
}
