package no.sikt.nva.nvi.index.utils;

import static nva.commons.core.attempt.Try.attempt;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.report.InstitutionReportHeader;
import no.sikt.nva.nvi.index.model.search.CandidateSearchParameters;
import no.sikt.nva.nvi.index.model.search.SearchResultParameters;
import no.sikt.nva.nvi.index.xlsx.ExcelWorkbookGenerator;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InstitutionReportGenerator {

    private static final Logger logger = LoggerFactory.getLogger(InstitutionReportGenerator.class);
    private static final int INITIAL_OFFSET = 0;
    private final SearchClient<NviCandidateIndexDocument> searchClient;
    private final int searchPageSize;
    private final String year;
    private final URI topLevelOrganization;

    public InstitutionReportGenerator(SearchClient<NviCandidateIndexDocument> searchClient,
                                      int searchPageSize,
                                      String year,
                                      URI topLevelOrganization) {
        this.searchClient = searchClient;
        this.searchPageSize = searchPageSize;
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

    private static void addHitsToListOfCandidates(HitsMetadata<NviCandidateIndexDocument> hits,
                                                  List<NviCandidateIndexDocument> fetchedCandidates) {
        hits.hits().stream().map(Hit::source).forEach(fetchedCandidates::add);
    }

    private Stream<List<String>> orderByHeaderOrder(
        List<Map<InstitutionReportHeader, String>> reportRows) {
        return reportRows.stream().map(InstitutionReportGenerator::sortValuesByHeaderOrder);
    }

    private List<NviCandidateIndexDocument> fetchNviCandidates() {
        var fetchedCandidates = new ArrayList<NviCandidateIndexDocument>();
        var offset = INITIAL_OFFSET;
        var hits = search(offset);
        addHitsToListOfCandidates(hits, fetchedCandidates);
        while (thereAreMoreHitsToFetch(hits, fetchedCandidates.size())) {
            offset += searchPageSize;
            hits = search(offset);
            addHitsToListOfCandidates(hits, fetchedCandidates);
        }
        logNumberOfCandidatesFound(fetchedCandidates);
        return fetchedCandidates;
    }

    private void logNumberOfCandidatesFound(List<NviCandidateIndexDocument> candidates) {
        logger.info("Found {} candidates for institution {} for year {}", candidates.size(), topLevelOrganization,
                    year);
    }

    private HitsMetadata<NviCandidateIndexDocument> search(int offset) {
        logger.info("Searching for candidates with page size {} and with offset {}", searchPageSize, offset);
        return attempt(() -> searchClient.search(buildSearchRequest(offset))).orElseThrow().hits();
    }

    private boolean thereAreMoreHitsToFetch(HitsMetadata<NviCandidateIndexDocument> hitsMetadata, int size) {
        return hitsMetadata.total().value() > size;
    }

    private CandidateSearchParameters buildSearchRequest(int offset) {
        return CandidateSearchParameters.builder()
                   .withYear(year)
                   .withTopLevelCristinOrg(topLevelOrganization)
                   .withSearchResultParameters(getSearchRequestParameters(offset))
                   .build();
    }

    private SearchResultParameters getSearchRequestParameters(int offset) {
        return SearchResultParameters.builder()
                   .withSize(searchPageSize)
                   .withOffset(offset)
                   .build();
    }
}
