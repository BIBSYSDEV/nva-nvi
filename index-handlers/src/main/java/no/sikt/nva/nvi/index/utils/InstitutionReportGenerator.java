package no.sikt.nva.nvi.index.utils;

import static java.util.Objects.nonNull;
import java.io.IOException;
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
import nva.commons.core.paths.UriWrapper;
import org.opensearch.client.ResponseException;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InstitutionReportGenerator {

    private static final Logger logger = LoggerFactory.getLogger(InstitutionReportGenerator.class);
    private static final int INITIAL_OFFSET = 0;
    private static final String EXCLUDE_CONTRIBUTORS_FIELD = "publicationDetails.contributors";
    private static final int HTTP_REQUEST_ENTITY_TOO_LARGE = 413;
    private static final int MIN_PAGE_SIZE = 1;
    private static final int EXPONENTIAL_PAGE_SIZE_DIVISOR = 2;
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

    private static boolean isRequestEntityTooLarge(ResponseException responseException) {
        return responseException.getResponse().getStatusLine().getStatusCode() == HTTP_REQUEST_ENTITY_TOO_LARGE;
    }

    private static boolean hasNotReachedMinimumPageSize(int currentPageSize) {
        return currentPageSize > MIN_PAGE_SIZE;
    }

    private static boolean thereAreMoreHitsToFetch(Long totalHits, int numberOfFetchedCandidates) {
        return totalHits > numberOfFetchedCandidates;
    }

    private Stream<List<String>> orderByHeaderOrder(
        List<Map<InstitutionReportHeader, String>> reportRows) {
        return reportRows.stream().map(InstitutionReportGenerator::sortValuesByHeaderOrder);
    }

    private List<NviCandidateIndexDocument> fetchNviCandidates() {
        var fetchedCandidates = new ArrayList<NviCandidateIndexDocument>();
        var offset = INITIAL_OFFSET;
        var currentPageSize = searchPageSize;
        Long totalHits = null;

        do {
            try {
                var hits = search(offset, currentPageSize);
                addHitsToListOfCandidates(hits, fetchedCandidates);
                totalHits = hits.total().value();
                offset += currentPageSize;
                currentPageSize = searchPageSize;
            } catch (ResponseException responseException) {
                if (isRequestEntityTooLarge(responseException) && hasNotReachedMinimumPageSize(currentPageSize)) {
                    currentPageSize /= EXPONENTIAL_PAGE_SIZE_DIVISOR;
                    offset = fetchedCandidates.size();
                } else {
                    throw new RuntimeException(responseException);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } while (thereAreMoreHitsToFetch(totalHits, fetchedCandidates.size()));
        logNumberOfCandidatesFound(fetchedCandidates);
        return fetchedCandidates;
    }

    private void logNumberOfCandidatesFound(List<NviCandidateIndexDocument> candidates) {
        logger.info("Found {} candidates for institution {} for year {}", candidates.size(), topLevelOrganization,
                    year);
    }

    private HitsMetadata<NviCandidateIndexDocument> search(int offset, int pageSize) throws IOException {
        logger.info("Searching for candidates with page size {} and with offset {}", pageSize, offset);
        return searchClient.search(buildSearchRequest(offset, pageSize)).hits();
    }

    private CandidateSearchParameters buildSearchRequest(int offset, int pageSize) {
        var topLevelOrganizationIdentifier = UriWrapper.fromUri(topLevelOrganization).getLastPathElement();
        return CandidateSearchParameters.builder()
                   .withYear(year)
                   .withTopLevelCristinOrg(topLevelOrganization)
                   .withAffiliations(List.of(topLevelOrganizationIdentifier))
                   .withSearchResultParameters(getSearchRequestParameters(offset, pageSize))
                   .withExcludeFields(List.of(EXCLUDE_CONTRIBUTORS_FIELD))
                   .build();
    }

    private SearchResultParameters getSearchRequestParameters(int offset, int pageSize) {
        return SearchResultParameters.builder()
                   .withSize(pageSize)
                   .withOffset(offset)
                   .build();
    }
}
