package no.sikt.nva.nvi.index.apigateway;

import static no.sikt.nva.nvi.index.apigateway.NviInstitutionStatusHeader.AUTHOR_SHARE_COUNT;
import static no.sikt.nva.nvi.index.apigateway.NviInstitutionStatusHeader.CONTRIBUTOR_IDENTIFIER;
import static no.sikt.nva.nvi.index.apigateway.NviInstitutionStatusHeader.DEPARTMENT_ID;
import static no.sikt.nva.nvi.index.apigateway.NviInstitutionStatusHeader.FACULTY_ID;
import static no.sikt.nva.nvi.index.apigateway.NviInstitutionStatusHeader.FIRST_NAME;
import static no.sikt.nva.nvi.index.apigateway.NviInstitutionStatusHeader.GLOBAL_STATUS;
import static no.sikt.nva.nvi.index.apigateway.NviInstitutionStatusHeader.GROUP_ID;
import static no.sikt.nva.nvi.index.apigateway.NviInstitutionStatusHeader.INSTITUTION_APPROVAL_STATUS;
import static no.sikt.nva.nvi.index.apigateway.NviInstitutionStatusHeader.INSTITUTION_ID;
import static no.sikt.nva.nvi.index.apigateway.NviInstitutionStatusHeader.INTERNATIONAL_COLLABORATION_FACTOR;
import static no.sikt.nva.nvi.index.apigateway.NviInstitutionStatusHeader.LAST_NAME;
import static no.sikt.nva.nvi.index.apigateway.NviInstitutionStatusHeader.POINTS_FOR_AFFILIATION;
import static no.sikt.nva.nvi.index.apigateway.NviInstitutionStatusHeader.PUBLICATION_CHANNEL_LEVEL_POINTS;
import static no.sikt.nva.nvi.index.apigateway.NviInstitutionStatusHeader.PUBLICATION_IDENTIFIER;
import static no.sikt.nva.nvi.index.apigateway.NviInstitutionStatusHeader.PUBLICATION_INSTANCE;
import static no.sikt.nva.nvi.index.apigateway.NviInstitutionStatusHeader.PUBLICATION_TITLE;
import static no.sikt.nva.nvi.index.apigateway.NviInstitutionStatusHeader.PUBLISHED_YEAR;
import static no.sikt.nva.nvi.index.apigateway.NviInstitutionStatusHeader.REPORTING_YEAR;
import static nva.commons.core.attempt.Try.attempt;
import java.net.URI;
import java.util.List;
import java.util.stream.Stream;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.search.CandidateSearchParameters;
import no.sikt.nva.nvi.index.xlsx.ExcelWorkbookGenerator;
import org.opensearch.client.opensearch.core.search.Hit;

public class InstitutionReportGenerator {

    private static final List<String> HEADER = List.of(REPORTING_YEAR.getValue(),
                                                       PUBLICATION_IDENTIFIER.getValue(),
                                                       PUBLISHED_YEAR.getValue(),
                                                       INSTITUTION_APPROVAL_STATUS.getValue(),
                                                       PUBLICATION_INSTANCE.getValue(),
                                                       CONTRIBUTOR_IDENTIFIER.getValue(),
                                                       INSTITUTION_ID.getValue(),
                                                       FACULTY_ID.getValue(),
                                                       DEPARTMENT_ID.getValue(),
                                                       GROUP_ID.getValue(),
                                                       LAST_NAME.getValue(),
                                                       FIRST_NAME.getValue(),
                                                       PUBLICATION_TITLE.getValue(),
                                                       GLOBAL_STATUS.getValue(),
                                                       PUBLICATION_CHANNEL_LEVEL_POINTS.getValue(),
                                                       INTERNATIONAL_COLLABORATION_FACTOR.getValue(),
                                                       AUTHOR_SHARE_COUNT.getValue(),
                                                       POINTS_FOR_AFFILIATION.getValue());
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
        var searchParameters = CandidateSearchParameters.builder()
                                   .withYear(year)
                                   .withTopLevelCristinOrg(topLevelOrganization)
                                   .build();
        var nviCandidates = attempt(() -> searchClient.search(searchParameters)).orElseThrow().hits().hits();
        var data = nviCandidates.stream().flatMap(this::toReportRows).toList();
        return new ExcelWorkbookGenerator(HEADER, data);
    }

    private Stream<List<String>> toReportRows(Hit<NviCandidateIndexDocument> candidate) {
        return null;
    }
}
