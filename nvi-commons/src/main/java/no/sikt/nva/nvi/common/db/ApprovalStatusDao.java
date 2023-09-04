package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.DatabaseConstants.HASH_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.SORT_KEY;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.converters.ApprovalStatusConverterProvider;
import no.sikt.nva.nvi.common.model.ApprovalStatusWithIdentifier;
import no.sikt.nva.nvi.common.model.business.ApprovalStatus;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.enhanced.dynamodb.DefaultAttributeConverterProvider;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean(converterProviders = {ApprovalStatusConverterProvider.class, DefaultAttributeConverterProvider.class})
public class ApprovalStatusDao extends Dao implements DynamoEntryWithRangeKey {

    public static final TableSchema<ApprovalStatusDao> TABLE_SCHEMA = TableSchema.fromClass(ApprovalStatusDao.class);
    public static final String TYPE = "APPROVAL_STATUS";
    private UUID identifier;
    private ApprovalStatus approvalStatus;

    public ApprovalStatusDao() {
    }

    public ApprovalStatusDao(UUID identifier, ApprovalStatus approvalStatus) {
        super();
        this.identifier = identifier;
        this.approvalStatus = approvalStatus;
    }

    public static String SK(String institutionUri) {
        return String.join(FIELD_DELIMITER, TYPE, institutionUri);
    }

    @Override
    @DynamoDbPartitionKey
    @DynamoDbAttribute(HASH_KEY)
    public String getPrimaryKeyHashKey() {
        return CandidateDao.PK(identifier.toString());
    }

    @Override
    public void setPrimaryKeyHashKey(String primaryHashKey) {
        // DO NOTHING
    }

    @Override
    @DynamoDbSortKey
    @DynamoDbAttribute(SORT_KEY)
    public String getPrimaryKeyRangeKey() {
        return SK(approvalStatus.institutionId().toString());
    }

    @Override
    public void setPrimaryKeyRangeKey(String primaryRangeKey) {
        // DO NOTHING
    }

    @Override
    @JacocoGenerated
    @DynamoDbAttribute(TYPE_FIELD)
    public String getType() {
        return TYPE;
    }

    //    @Override
    //    public void setType(String type) {
    //        DynamoEntryWithRangeKey.super.setType(type);
    //    }

    @DynamoDbAttribute(DATA_FIELD)
    public ApprovalStatus getApprovalStatus() {
        return approvalStatus;
    }

    @JacocoGenerated
    public void setApprovalStatus(ApprovalStatus approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public UUID getIdentifier() {
        return identifier;
    }

    @JacocoGenerated
    public void setIdentifier(UUID identifier) {
        this.identifier = identifier;
    }

    public ApprovalStatusWithIdentifier toApprovalStatusWithIdentifier() {
        return ApprovalStatusWithIdentifier.builder()
                   .withIdentifier(identifier)
                   .withApprovalStatus(approvalStatus)
                   .build();
    }
}
