package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.ApplicationConstants.PRIMARY_KEY;
import static no.sikt.nva.nvi.common.ApplicationConstants.SORT_KEY;
import java.net.URI;
import no.sikt.nva.nvi.common.model.business.Candidate;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.enhanced.dynamodb.DefaultAttributeConverterProvider;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@SuppressWarnings({"PMD.GodClass", "PMD.ExcessivePublicCount"})
@DynamoDbBean(converterProviders = { CandidateConverterProvider.class, DefaultAttributeConverterProvider.class})
public class CandidateDao implements DynamoEntryWithRangeKey {

    public static final TableSchema<CandidateDao> TABLE_SCHEMA = TableSchema.fromClass(CandidateDao.class);
    private static final String DATA_FIELD = "data";
    public static final String TYPE = "CANDIDATE";
    private String documentIdentifier;
    private Candidate candidate;

    public CandidateDao() {
        super();
    }

    public CandidateDao(String documentIdentifier, Candidate candidate) {
        super();
        this.documentIdentifier = documentIdentifier;
        this.candidate = candidate;
    }

    public static CandidateDao fromCandidateDto(Candidate candidate) {
        var documentIdentifier = getDocIdFromUri(candidate.publicationId());
        return new CandidateDao(documentIdentifier, candidate);
    }

    @Override
    @DynamoDbPartitionKey
    @DynamoDbAttribute(PRIMARY_KEY)
    public String getPrimaryKeyHashKey() {
        return String.join(DynamoEntryWithRangeKey.FIELD_DELIMITER, TYPE, documentIdentifier);
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

    public static String getDocIdFromUri(URI uri) {
        return uri.getPath().substring(uri.getPath().lastIndexOf('/') + 1);
    }

    @DynamoDbIgnore
    @JacocoGenerated
    public String getDocumentIdentifier() {
        return documentIdentifier;
    }

    @DynamoDbAttribute(DATA_FIELD)
    public Candidate getCandidateDb() {
        return candidate;
    }

    @JacocoGenerated
    public void setDocumentIdentifier(String documentIdentifier) {
        this.documentIdentifier = documentIdentifier;
    }

    public void setCandidateDb(Candidate candidate) {
        this.candidate = candidate;
    }
}
