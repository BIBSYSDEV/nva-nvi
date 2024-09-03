package no.sikt.nva.nvi.index.utils;

import static nva.commons.core.attempt.Try.attempt;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.report.InstitutionReportHeader;
import no.sikt.nva.nvi.index.model.search.CandidateSearchParameters;
import no.sikt.nva.nvi.index.xlsx.ExcelWorkbookGenerator;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InstitutionReportGenerator {

    private static final Logger logger = LoggerFactory.getLogger(InstitutionReportGenerator.class);
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
        var data = nviCandidates.stream()
                       .map(candidate -> candidate.toReportRowsForInstitution(topLevelOrganization))
                       .flatMap(this::orderByHeaderOrder)
                       .toList();
        return new ExcelWorkbookGenerator(InstitutionReportHeader.getOrderedValues(), data);
    }

    private static List<String> sortValuesByHeaderOrder(Map<InstitutionReportHeader, String> keyValueMap) {
        return keyValueMap.entrySet()
                   .stream()
                   .sorted(Comparator.comparing(entry -> entry.getKey().getOrder()))
                   .map(Entry::getValue)
                   .toList();
    }

    private Stream<List<String>> orderByHeaderOrder(
        List<Map<InstitutionReportHeader, String>> reportRows) {
        return reportRows.stream().map(InstitutionReportGenerator::sortValuesByHeaderOrder);
    }

    private List<NviCandidateIndexDocument> fetchNviCandidates() {
        var searchHits = attempt(() -> searchClient.search(buildSearchRequest())).orElseThrow()
                               .hits()
                               .hits()
                               .stream()
                               .map(Hit::source)
                               .toList();
        logger.info("Found {} candidates for institution {} for year {}", searchHits.size(), topLevelOrganization,
                    year);
        return searchHits;
    }

    private CandidateSearchParameters buildSearchRequest() {
        return CandidateSearchParameters.builder()
                   .withYear(year)
                   .withTopLevelCristinOrg(topLevelOrganization)
                   .build();
    }
}
