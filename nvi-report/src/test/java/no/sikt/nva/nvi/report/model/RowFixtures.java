package no.sikt.nva.nvi.report.model;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;

import java.math.BigDecimal;
import no.sikt.nva.nvi.report.model.institutionreport.ReportRowBuilder;

public final class RowFixtures {

  private RowFixtures() {}

  public static Row completeReportRow(String publicationId) {
    return new ReportRowBuilder()
        .withYear(randomString())
        .withPublicationId(publicationId)
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
        .withDbhInstitutionCode(randomString())
        .withDbhFacultyCode(randomString())
        .withDbhDepartmentCode(randomString())
        .withRboStatus("J")
        .withSector(randomString())
        .build();
  }
}
