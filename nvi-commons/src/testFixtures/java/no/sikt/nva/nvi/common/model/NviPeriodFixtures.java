package no.sikt.nva.nvi.common.model;

import static no.sikt.nva.nvi.common.model.UsernameFixtures.randomUsername;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.time.Instant;
import java.time.ZonedDateTime;
import no.sikt.nva.nvi.common.service.model.NviPeriod;

public final class NviPeriodFixtures {
  private static final Instant PREVIOUS_MONTH = ZonedDateTime.now().minusMonths(1).toInstant();
  private static final Instant PREVIOUS_YEAR = ZonedDateTime.now().minusMonths(12).toInstant();
  private static final Instant NEXT_MONTH = ZonedDateTime.now().plusMonths(1).toInstant();
  private static final Instant NEXT_YEAR = ZonedDateTime.now().plusMonths(12).toInstant();

  private NviPeriodFixtures() {}

  public static NviPeriod.Builder createNviPeriodBuilder() {
    var user = randomUsername();
    return NviPeriod.builder()
        .withId(randomUri())
        .withPublishingYear(CURRENT_YEAR)
        .withCreatedBy(user)
        .withModifiedBy(user);
  }

  public static NviPeriod openPeriod() {
    return createNviPeriodBuilder()
        .withStartDate(PREVIOUS_MONTH)
        .withReportingDate(NEXT_MONTH)
        .build();
  }

  public static NviPeriod closedPeriod() {
    return createNviPeriodBuilder()
        .withStartDate(PREVIOUS_YEAR)
        .withReportingDate(PREVIOUS_MONTH)
        .build();
  }

  public static NviPeriod futurePeriod() {
    return createNviPeriodBuilder().withStartDate(NEXT_MONTH).withReportingDate(NEXT_YEAR).build();
  }
}
