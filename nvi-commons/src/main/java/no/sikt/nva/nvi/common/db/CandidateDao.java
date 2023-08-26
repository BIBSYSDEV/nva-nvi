package no.sikt.nva.nvi.common.db;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.ApplicationConstants.HASH_KEY;
import static no.sikt.nva.nvi.common.ApplicationConstants.SECONDARY_INDEX_1_HASH_KEY;
import static no.sikt.nva.nvi.common.ApplicationConstants.SECONDARY_INDEX_1_RANGE_KEY;
import static no.sikt.nva.nvi.common.ApplicationConstants.SECONDARY_INDEX_PUBLICATION_ID;
import static no.sikt.nva.nvi.common.ApplicationConstants.SORT_KEY;
import java.util.UUID;
import no.sikt.nva.nvi.common.ApplicationConstants;
import no.sikt.nva.nvi.common.model.CandidateWithIdentifier;
import no.sikt.nva.nvi.common.model.business.Candidate;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.enhanced.dynamodb.DefaultAttributeConverterProvider;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@SuppressWarnings({"PMD.GodClass", "PMD.ExcessivePublicCount"})
@DynamoDbBean(converterProviders = { CandidateConverterProvider.class, DefaultAttributeConverterProvider.class})
public class CandidateDao extends Dao implements DynamoEntryWithRangeKey {

    public static final TableSchema<CandidateDao> TABLE_SCHEMA = TableSchema.fromClass(CandidateDao.class);
    private static final String DATA_FIELD = "data";
    public static final String TYPE = "CANDIDATE";
    private UUID identifier;
    private Candidate candidate;

    public CandidateDao() {
        super();
    }

    public CandidateDao(UUID identifier, Candidate candidate) {
        super();
        this.identifier = identifier;
        this.candidate = candidate;
    }

    @Override
    @DynamoDbPartitionKey
    @DynamoDbAttribute(HASH_KEY)
    public String getPrimaryKeyHashKey() {
        return String.join(DynamoEntryWithRangeKey.FIELD_DELIMITER, TYPE, identifier.toString());
    }

    @Override
    public void setPrimaryKeyHashKey(String primaryRangeKey) {
        //DO NOTHING
    }

    @Override
    @DynamoDbSortKey
    @DynamoDbAttribute(SORT_KEY)
    public String getPrimaryKeyRangeKey() {
        return getPrimaryKeyHashKey();
    }

    @Override
    public void setPrimaryKeyRangeKey(String primaryRangeKey) {
        //DO NOTHING
    }

    @Override
    @JacocoGenerated
    @DynamoDbAttribute(TYPE_FIELD)
    public String getType() {
        return TYPE;
    }

    @JacocoGenerated
    @DynamoDbSecondaryPartitionKey(indexNames = {SECONDARY_INDEX_PUBLICATION_ID})
    @DynamoDbAttribute(SECONDARY_INDEX_1_HASH_KEY)
    public String getSearchByPublicationIdHashKey() {
        return nonNull(candidate.publicationId()) ? candidate.publicationId().toString() : null;
    }

    @JacocoGenerated
    public void setSearchByPublicationIdHashKey(String searchByPublicationIdHashKey) {
        //DO NOTHING
    }

    @JacocoGenerated
    @DynamoDbSecondarySortKey(indexNames = {SECONDARY_INDEX_PUBLICATION_ID})
    @DynamoDbAttribute(SECONDARY_INDEX_1_RANGE_KEY)
    public String getSearchByPublicationIdSortKey() {
        return nonNull(candidate.publicationId()) ? candidate.publicationId().toString() : null;
    }

    @JacocoGenerated
    public void setSearchByPublicationIdSortKey(String searchByPublicationIdSortKey) {
        //DO NOTHING
    }

    @DynamoDbAttribute(DATA_FIELD)
    public Candidate getCandidate() {
        return candidate;
    }

    @JacocoGenerated
    public void setIdentifier(UUID identifier) {
        this.identifier = identifier;
    }

    public UUID getIdentifier() {
        return identifier;
    }

    public void setCandidate(Candidate candidate) {
        this.candidate = candidate;
    }

    public CandidateWithIdentifier toCandidateWithIdentifier() {
        return new CandidateWithIdentifier(candidate, identifier);
    }
}
