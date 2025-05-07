package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.db.DbPublicationChannelFixtures.getExpectedDbPublicationChannel;
import static no.sikt.nva.nvi.common.db.DbPublicationChannelFixtures.randomPublicationChannelBuilder;
import static no.sikt.nva.nvi.common.model.InstanceTypeFixtures.randomInstanceType;
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
import no.sikt.nva.nvi.test.TestUtils;

public class DbPointCalculationFixtures {

  public static DbPointCalculation.Builder randomPointCalculationBuilder(
      URI organizationId, URI creatorId) {
    var channel = randomPublicationChannelBuilder().build();
    return DbPointCalculation.builder()
        .basePoints(TestUtils.randomBigDecimal())
        .collaborationFactor(null)
        .totalPoints(TestUtils.randomBigDecimal())
        .publicationChannel(channel)
        .institutionPoints(List.of(generateInstitutionPoints(organizationId, creatorId)))
        .internationalCollaboration(randomBoolean())
        .creatorShareCount(randomInteger(100))
        .instanceType(randomInstanceType().getValue());
  }

  public static DbPointCalculation getExpectedPointCalculation(UpsertNviCandidateRequest request) {
    var channel = getExpectedDbPublicationChannel(request);
    return DbPointCalculation.builder()
        .basePoints(adjustScaleAndRoundingMode(request.basePoints()))
        .collaborationFactor(adjustScaleAndRoundingMode(request.collaborationFactor()))
        .totalPoints(adjustScaleAndRoundingMode(request.totalPoints()))
        .publicationChannel(channel)
        .institutionPoints(
            request.institutionPoints().stream().map(DbInstitutionPoints::from).toList())
        .internationalCollaboration(request.isInternationalCollaboration())
        .creatorShareCount(request.creatorShareCount())
        .instanceType(request.publicationDetails().publicationType().getValue())
        .build();
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
