package no.sikt.nva.nvi.index.utils;

import static no.sikt.nva.nvi.common.utils.ExceptionUtils.getStackTrace;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeaders.INSTITUTION_REPORT_HEADERS;
import static nva.commons.core.attempt.Try.attempt;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.document.NviContributor;
import no.sikt.nva.nvi.index.model.document.NviOrganization;
import no.sikt.nva.nvi.index.model.report.InstitutionReportHeader;
import no.sikt.nva.nvi.index.model.search.CandidateSearchParameters;
import no.sikt.nva.nvi.index.xlsx.ExcelWorkbookGenerator;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;

public class InstitutionReportGenerator {

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(InstitutionReportGenerator.class);
    private static final String REPORT_REJECTED_VALUE = "N";
    private static final String REPORT_PENDING_VALUE = "?";
    private static final String REPORT_APPROVED_VALUE = "J";
    private static final String REPORT_DISPUTED_VALUE = "T";
    private final SearchClient<NviCandidateIndexDocument> searchClient;
    private final String year;
    private final URI topLevelOrganization;

    public InstitutionReportGenerator(SearchClient<NviCandidateIndexDocument> searchClient,
                                      String year,
                                      URI topLevelOrganization) {
        this.searchClient = searchClient;
        this.year = year;
        this.topLevelOrganization = topLevelOrganization;
    }

    public ExcelWorkbookGenerator generateReport() {
        var nviCandidates = fetchNviCandidates();
        var data = nviCandidates.stream().flatMap(this::generateDataRows).toList();
        return new ExcelWorkbookGenerator(InstitutionReportHeader.getOrderedValues(), data);
    }

    private Stream<List<String>> generateDataRows(NviCandidateIndexDocument candidate) {
        return generateReportRowsForCandidate(candidate).stream();
    }

    private List<NviCandidateIndexDocument> fetchNviCandidates() {
        return attempt(() -> searchClient.search(buildSearchRequest())).orElseThrow()
                   .hits()
                   .hits()
                   .stream()
                   .map(Hit::source)
                   .toList();
    }

    private CandidateSearchParameters buildSearchRequest() {
        return CandidateSearchParameters.builder()
                   .withYear(year)
                   .withTopLevelCristinOrg(topLevelOrganization)
                   .build();
    }

    private List<List<String>> generateReportRowsForCandidate(NviCandidateIndexDocument candidate) {
        return attempt(() -> generateReportRowsForContributorAffiliations(candidate)).map(Stream::toList)
                   .orElseThrow(failure -> {
                       logFailure(failure.getException(), candidate);
                       return (RuntimeException) failure.getException();
                   });
    }

    private Stream<List<String>> generateReportRowsForContributorAffiliations(NviCandidateIndexDocument candidate) {
        return candidate.getNviContributors().stream()
                   .flatMap(nviContributor -> generateRowsForContributorAffiliations(candidate, nviContributor));
    }

    private void logFailure(Exception exception, NviCandidateIndexDocument candidate) {
        logger.error("Failed to generate report lines for candidate: {}. Error {}", candidate.id(),
                     getStackTrace(exception));
    }

    private Stream<List<String>> generateRowsForContributorAffiliations(NviCandidateIndexDocument candidate,
                                                                        NviContributor nviContributor) {
        return nviContributor.getAffiliationsPartOfOrEqualTo(topLevelOrganization)
                   .map(affiliation -> generateRow(candidate, nviContributor, affiliation));
    }

    private List<String> generateRow(NviCandidateIndexDocument candidate, NviContributor nviContributor,
                                     NviOrganization affiliation) {
        var row = new ArrayList<String>();
        row.add(candidate.getReportingPeriodYear());
        row.add(candidate.getPublicationIdentifier());
        row.add(candidate.getPublicationDateYear());
        row.add(getApprovalStatus(candidate));
        row.add(candidate.getPublicationInstanceType());
        row.add(nviContributor.id());
        row.add(affiliation.getInstitutionIdentifier());
        row.add(affiliation.getFacultyIdentifier());
        row.add(affiliation.getDepartmentIdentifier());
        row.add(affiliation.getGroupIdentifier());
        row.add(nviContributor.name());
        row.add(nviContributor.name());
        row.add(candidate.getPublicationTitle());
        row.add(getGlobalApprovalStatus(candidate));
        row.add(candidate.publicationTypeChannelLevelPoints().toString());
        row.add(candidate.internationalCollaborationFactor().toString());
        row.add(String.valueOf(candidate.creatorShareCount()));
        row.add(
            candidate.getPointsForContributorAffiliation(topLevelOrganization, nviContributor, affiliation).toString());
        return row;
    }

    private String getGlobalApprovalStatus(NviCandidateIndexDocument candidate) {
        return switch (candidate.globalApprovalStatus()) {
            case PENDING -> REPORT_PENDING_VALUE;
            case APPROVED -> REPORT_APPROVED_VALUE;
            case REJECTED -> REPORT_REJECTED_VALUE;
            case DISPUTE -> REPORT_DISPUTED_VALUE;
        };
    }

    private String getApprovalStatus(NviCandidateIndexDocument document) {
        return switch (document.getApprovalStatusForInstitution(topLevelOrganization)) {
            case APPROVED -> REPORT_APPROVED_VALUE;
            case REJECTED -> REPORT_REJECTED_VALUE;
            case NEW, PENDING -> REPORT_PENDING_VALUE;
        };
    }
}
