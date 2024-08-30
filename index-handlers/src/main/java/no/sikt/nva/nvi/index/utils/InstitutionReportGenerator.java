package no.sikt.nva.nvi.index.utils;

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
import no.sikt.nva.nvi.index.model.search.CandidateSearchParameters;
import no.sikt.nva.nvi.index.xlsx.ExcelWorkbookGenerator;
import org.opensearch.client.opensearch.core.search.Hit;

public class InstitutionReportGenerator {

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
        var data = nviCandidates.stream().flatMap(this::generateReportRowForEachContributorAffiliation).toList();
        return new ExcelWorkbookGenerator(INSTITUTION_REPORT_HEADERS, data);
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

    private Stream<List<String>> generateReportRowForEachContributorAffiliation(NviCandidateIndexDocument candidate) {
        return candidate.publicationDetails().contributors().stream()
                   .filter(NviContributor.class::isInstance)
                   .map(NviContributor.class::cast)
                   .flatMap(nviContributor -> generateRowsForContributorAffiliations(candidate, nviContributor));
    }

    private Stream<List<String>> generateRowsForContributorAffiliations(NviCandidateIndexDocument candidate,
                                                                        NviContributor nviContributor) {
        return nviContributor.affiliations()
                   .stream()
                   .filter(NviOrganization.class::isInstance)
                   .map(NviOrganization.class::cast)
                   .map(affiliation -> getExpectedRow(candidate, nviContributor, affiliation));
    }

    private List<String> getExpectedRow(NviCandidateIndexDocument candidate, NviContributor nviContributor,
                                        NviOrganization affiliation) {
        var expectedRow = new ArrayList<String>();
        expectedRow.add(candidate.getReportingPeriodYear());
        expectedRow.add(candidate.getPublicationIdentifier());
        expectedRow.add(candidate.getPublicationDateYear());
        expectedRow.add(getApprovalStatus(candidate));
        expectedRow.add(candidate.getPublicationInstanceType());
        expectedRow.add(nviContributor.id());
        expectedRow.add(affiliation.getInstitutionIdentifier());
        expectedRow.add(affiliation.getFacultyIdentifier());
        expectedRow.add(affiliation.getDepartmentIdentifier());
        expectedRow.add(affiliation.getGroupIdentifier());
        expectedRow.add(nviContributor.name());
        expectedRow.add(nviContributor.name());
        expectedRow.add(candidate.getPublicationTitle());
        expectedRow.add(getGlobalApprovalStatus(candidate));
        expectedRow.add(candidate.publicationTypeChannelLevelPoints().toString());
        expectedRow.add(candidate.internationalCollaborationFactor().toString());
        expectedRow.add(String.valueOf(candidate.creatorShareCount()));
        expectedRow.add(
            candidate.getPointsForContributorAffiliation(topLevelOrganization, nviContributor, affiliation).toString());
        return expectedRow;
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
