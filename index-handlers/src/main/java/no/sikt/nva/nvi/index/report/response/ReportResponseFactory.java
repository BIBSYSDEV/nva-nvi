package no.sikt.nva.nvi.index.report.response;

import static java.util.Collections.emptyList;

import java.math.BigDecimal;
import java.util.List;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.model.Sector;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.common.service.dto.NviPeriodDto;
import no.sikt.nva.nvi.index.report.request.AllInstitutionsReportRequest;
import no.sikt.nva.nvi.index.report.request.AllPeriodsReportRequest;
import no.sikt.nva.nvi.index.report.request.InstitutionReportRequest;
import no.sikt.nva.nvi.index.report.request.PeriodReportRequest;
import no.sikt.nva.nvi.index.report.request.ReportRequest;

public class ReportResponseFactory {

  private final NviPeriodService nviPeriodService;

  public ReportResponseFactory(NviPeriodService nviPeriodService) {
    this.nviPeriodService = nviPeriodService;
  }

  public ReportResponse getResponse(ReportRequest reportRequest) {
    return switch (reportRequest) {
      case AllPeriodsReportRequest request -> placeholderAllPeriodsReport(request);
      case PeriodReportRequest request -> placeholderPeriodReport(request);
      case AllInstitutionsReportRequest request -> placeholderAllInstitutionsReport(request);
      case InstitutionReportRequest request -> placeholderInstitutionReport(request);
    };
  }

  // FIXME: Temporary placeholder
  private AllPeriodsReport placeholderAllPeriodsReport(AllPeriodsReportRequest request) {
    return new AllPeriodsReport(request.queryId(), emptyList());
  }

  // FIXME: Temporary placeholder
  private PeriodReport placeholderPeriodReport(PeriodReportRequest request) {
    var periodDto = getPeriodDto(request.period());
    return new PeriodReport(
        request.queryId(),
        periodDto,
        new PeriodTotals(BigDecimal.ZERO, 0, 0, 0),
        new CandidatesByGlobalApprovalStatus(0, 0, 0, 0));
  }

  // FIXME: Temporary placeholder
  private AllInstitutionsReport placeholderAllInstitutionsReport(
      AllInstitutionsReportRequest request) {
    var periodDto = getPeriodDto(request.period());
    return new AllInstitutionsReport(request.queryId(), periodDto, emptyList());
  }

  // FIXME: Temporary placeholder
  private InstitutionReport placeholderInstitutionReport(InstitutionReportRequest request) {
    var periodDto = getPeriodDto(request.period());
    var organization = Organization.builder().withId(request.institutionId()).build();
    var institutionSummary =
        new InstitutionSummary(
            new InstitutionTotals(BigDecimal.ZERO, 0, 0, 0),
            new UndisputedCandidatesByLocalApprovalStatus(0, 0, 0, 0));
    var unitSummary =
        new UnitSummary(
            organization,
            new UnitTotals(BigDecimal.ZERO, 0, 0, 0),
            new UndisputedCandidatesByLocalApprovalStatus(0, 0, 0, 0),
            emptyList());
    return new InstitutionReport(
        request.queryId(),
        periodDto,
        Sector.UNKNOWN,
        organization,
        institutionSummary,
        List.of(unitSummary));
  }

  private NviPeriodDto getPeriodDto(String publishingYear) {
    return nviPeriodService.getByPublishingYear(publishingYear).toDto();
  }
}
