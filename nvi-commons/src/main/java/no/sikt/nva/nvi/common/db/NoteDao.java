package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.DatabaseConstants.DATA_FIELD;
import static no.sikt.nva.nvi.common.DatabaseConstants.HASH_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.SORT_KEY;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.model.DbNote;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbImmutable(builder = NoteDao.Builder.class)
public record NoteDao (
    UUID identifier,
    @DynamoDbAttribute(DATA_FIELD)
    DbNote note) implements DynamoEntryWithRangeKey{
    public static final String TYPE = "NOTE";

    public static String sk0(String skId) {
        return String.join(FIELD_DELIMITER, TYPE, skId);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    @DynamoDbPartitionKey
    @DynamoDbAttribute(HASH_KEY)
    public String primaryKeyHashKey() {
        return CandidateDao.pk0(identifier.toString());
    }

    @Override
    @DynamoDbSortKey
    @DynamoDbAttribute(SORT_KEY)
    public String primaryKeyRangeKey() {
        return sk0(note.noteId().toString());
    }

    @Override
    @JacocoGenerated
    @DynamoDbAttribute(TYPE_FIELD)
    public String type() {
        return TYPE;
    }

    public static final class Builder {

        private UUID identifier;
        private DbNote note;

        private Builder() {
        }

        public Builder type(String noop) {
            // Used by @DynamoDbImmutable for building the object
            return this;
        }

        public Builder primaryKeyHashKey(String noop) {
            // Used by @DynamoDbImmutable for building the object
            return this;
        }

        public Builder primaryKeyRangeKey(String noop) {
            // Used by @DynamoDbImmutable for building the object
            return this;
        }

        public Builder identifier(UUID identifier) {
            this.identifier = identifier;
            return this;
        }

        public Builder note(DbNote note) {
            this.note = note;
            return this;
        }

        public NoteDao build() {
            return new NoteDao(identifier, note);
        }
    }
}
