package no.sikt.nva.nvi.index.report.model;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;

import java.math.BigDecimal;

public final class RowFixtures {

  private RowFixtures() {}

  public static Row completeReportRow() {
    return ReportRow.builder()
        .withYear(randomString())
        .withPublicationId(randomString())
        .withPublicationType(randomString())
        .withPublicationChannel(randomString())
        .withPublicationChannelType(randomString())
        .withPrintIssn(randomString())
        .withPublicationChannelName(randomString())
        .withScientificValue(randomString())
        .withContributorId(randomString())
        .withAffiliationIdentifier(randomString())
        .withAffiliationId(randomString())
        .withInstitutionNumber(randomString())
        .withFacultyNumber(randomString())
        .withDepartmentNumber(randomString())
        .withGroupNumber(randomString())
        .withLastName(randomString())
        .withFirstName(randomString())
        .withTitle(randomString())
        .withApprovalStatus(randomString())
        .withGlobalStatus(randomString())
        .withPublicationTypeChannelLevelPoints(BigDecimal.ONE)
        .withInternationalCollaborationFactor(randomString())
        .withCreatorShareCount(BigDecimal.ONE)
        .withTentativePublishingPoints(BigDecimal.ONE)
        .withPublishingPoints(BigDecimal.ONE)
        .withHkdirInstitutionCode(randomString())
        .build();
  }
}
