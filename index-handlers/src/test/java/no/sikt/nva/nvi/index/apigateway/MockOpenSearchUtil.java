package no.sikt.nva.nvi.index.apigateway;

import static no.sikt.nva.nvi.index.utils.SearchConstants.NVI_CANDIDATES_INDEX;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import java.util.List;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import org.opensearch.client.opensearch._types.ShardStatistics;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.SearchResponse.Builder;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.opensearch.client.opensearch.core.search.TotalHits;
import org.opensearch.client.opensearch.core.search.TotalHitsRelation;

public class MockOpenSearchUtil {

    public static SearchResponse<NviCandidateIndexDocument> createSearchResponse(NviCandidateIndexDocument document) {
        return defaultBuilder()
                   .hits(constructHitsMetadata(List.of(document)))
                   .build();
    }

    public static SearchResponse<NviCandidateIndexDocument> createSearchResponse(
        List<NviCandidateIndexDocument> documents, String aggregateName, Aggregate aggregate) {
        return defaultBuilder()
                   .hits(constructHitsMetadata(documents))
                   .aggregations(aggregateName, aggregate)
                   .build();
    }

    public static SearchResponse<NviCandidateIndexDocument> createSearchResponse(String aggregateName,
                                                                                 Aggregate aggregate) {
        return defaultBuilder()
                   .hits(constructHitsMetadata(List.of()))
                   .aggregations(aggregateName, aggregate)
                   .build();
    }

    private static Builder<NviCandidateIndexDocument> defaultBuilder() {
        return new Builder<NviCandidateIndexDocument>()
                   .took(10)
                   .timedOut(false)
                   .shards(new ShardStatistics.Builder().failed(0).successful(1).total(10).build());
    }

    private static HitsMetadata<NviCandidateIndexDocument> constructHitsMetadata(
        List<NviCandidateIndexDocument> hits) {
        return new HitsMetadata.Builder<NviCandidateIndexDocument>()
                   .total(new TotalHits.Builder().value(hits.size()).relation(TotalHitsRelation.Eq).build())
                   .hits(hits.stream().map(MockOpenSearchUtil::toHit).collect(Collectors.toList()))
                   .total(new TotalHits.Builder().relation(TotalHitsRelation.Eq).value(hits.size()).build())
                   .build();
    }

    private static Hit<NviCandidateIndexDocument> toHit(NviCandidateIndexDocument document) {
        return new Hit.Builder<NviCandidateIndexDocument>()
                   .id(randomString())
                   .index(NVI_CANDIDATES_INDEX)
                   .source(document)
                   .build();
    }
}
