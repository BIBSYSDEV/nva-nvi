package no.sikt.nva.nvi.test;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.Collections.emptyMap;
import static java.util.Objects.isNull;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static no.unit.nva.testutils.RandomDataGenerator.randomInstant;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import no.sikt.nva.nvi.common.model.ChannelType;
import no.sikt.nva.nvi.common.model.InstanceType;
import no.sikt.nva.nvi.common.model.PointCalculation;
import no.sikt.nva.nvi.common.model.PublicationChannel;
import no.sikt.nva.nvi.common.model.PublicationDate;
import no.sikt.nva.nvi.common.model.ScientificValue;
import no.sikt.nva.nvi.common.model.Sector;
import no.sikt.nva.nvi.common.service.model.Approval;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.common.service.model.PublicationDetails;
import no.sikt.nva.nvi.common.service.model.Username;
import nva.commons.core.Environment;

public final class SampleCandidateGenerator {

  private final Map<URI, Approval> approvals = new HashMap<>();
  private final List<InstitutionPoints> institutionPoints = new ArrayList<>();
  private final Instant createdDate = randomInstant();
  private final Instant modifiedDate = randomInstant();
  private final UUID candidateIdentifier = UUID.randomUUID();
  private static final boolean applicable = true;
  private final NviPeriod period = randomPeriod();
  private PointCalculation pointCalculation;
  private PublicationDetails publicationDetails;

  public SampleCandidateGenerator() {}

  public SampleCandidateGenerator withInstitutionPoints(
      URI institutionId, Sector sector, BigDecimal points) {
    var institutionPoint = new InstitutionPoints(institutionId, points, sector, List.of());
    this.institutionPoints.add(institutionPoint);
    this.approvals.put(
        institutionId, Approval.createNewApproval(candidateIdentifier, institutionId));
    return this;
  }

  public Candidate build() {
    applyRandomPublicationDetailsAndCalculationPoints();
    return new Candidate(
        candidateIdentifier,
        applicable,
        approvals,
        emptyMap(),
        period,
        pointCalculation,
        publicationDetails,
        createdDate,
        modifiedDate,
        null,
        null,
        null,
        new Environment());
  }

  private static ChannelType randomChannelType() {
    var values = ChannelType.values();
    return values[(int) (Math.random() * values.length)];
  }

  private static ScientificValue randomScientificValue() {
    var values = ScientificValue.values();
    return values[(int) (Math.random() * values.length)];
  }

  private static InstanceType randomInstanceType() {
    var values = InstanceType.values();
    return values[(int) (Math.random() * values.length)];
  }

  private static NviPeriod randomPeriod() {
    return NviPeriod.builder()
        .withId(randomUri())
        .withPublishingYear(Integer.parseInt(randomYear()))
        .withStartDate(Instant.now().plus(1, DAYS))
        .withReportingDate(Instant.now().plus(2, DAYS))
        .withCreatedBy(new Username(randomString()))
        .build();
  }

  private void applyRandomPublicationDetailsAndCalculationPoints() {
    if (isNull(pointCalculation)) {
      pointCalculation = randomPointCalculation();
    }
    if (isNull(publicationDetails)) {
      publicationDetails = randomPublicationDetails();
    }
  }

  private PointCalculation randomPointCalculation() {
    return new PointCalculation(
        randomInstanceType(),
        randomPublicationChannel(),
        false,
        BigDecimal.ONE,
        BigDecimal.ONE,
        1,
        institutionPoints,
        calculateTotalPoints());
  }

  private PublicationChannel randomPublicationChannel() {
    return new PublicationChannel(randomUri(), randomChannelType(), randomScientificValue());
  }

  private PublicationDetails randomPublicationDetails() {
    return PublicationDetails.builder()
        .withId(randomUri())
        .withPublicationBucketUri(randomUri())
        .withTitle(TestUtils.randomTitle())
        .withPublicationDate(new PublicationDate(randomYear(), null, null))
        .withNviCreators(List.of())
        .build();
  }

  private BigDecimal calculateTotalPoints() {
    return institutionPoints.stream()
        .map(InstitutionPoints::institutionPoints)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
