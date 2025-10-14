package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.DatabaseConstants.DATA_FIELD;
import static no.sikt.nva.nvi.common.DatabaseConstants.HASH_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.IDENTIFIER_FIELD;
import static no.sikt.nva.nvi.common.DatabaseConstants.SORT_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.VERSION_FIELD;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.time.Instant;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.NoteDao.Builder;
import no.sikt.nva.nvi.common.db.model.Username;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbImmutable(builder = Builder.class)
@JsonSerialize
public final class NoteDao extends Dao {

  public static final String TYPE = "NOTE";

  @JsonProperty(IDENTIFIER_FIELD)
  private final UUID identifier;

  @JsonProperty(DATA_FIELD)
  private final DbNote note;

  @JsonProperty(VERSION_FIELD)
  private final String version;

  public NoteDao(
      @JsonProperty(IDENTIFIER_FIELD) UUID identifier,
      @JsonProperty(DATA_FIELD) DbNote note,
      @JsonProperty(VERSION_FIELD) String version) {
    super();
    this.identifier = identifier;
    this.note = note;
    this.version = version;
  }

  public static String createSortKey(String skId) {
    return String.join(FIELD_DELIMITER, TYPE, skId);
  }

  public static Builder builder() {
    return new Builder();
  }

  @DynamoDbIgnore
  public Builder copy() {
    return new Builder().identifier(identifier).note(note).version(version);
  }

  @Override
  @DynamoDbPartitionKey
  @DynamoDbAttribute(HASH_KEY)
  @JsonProperty(HASH_KEY)
  public String primaryKeyHashKey() {
    return CandidateDao.createPartitionKey(identifier.toString());
  }

  @Override
  @DynamoDbSortKey
  @DynamoDbAttribute(SORT_KEY)
  @JsonProperty(SORT_KEY)
  public String primaryKeyRangeKey() {
    return createSortKey(note.noteId().toString());
  }

  @Override
  public String version() {
    return version;
  }

  @Override
  @JacocoGenerated
  @DynamoDbAttribute(TYPE_FIELD)
  public String type() {
    return TYPE;
  }

  public UUID identifier() {
    return identifier;
  }

  @DynamoDbAttribute(DATA_FIELD)
  public DbNote note() {
    return note;
  }

  public static final class Builder {

    // Becasue of codacy the variable name has to be different than the setMethod
    // And because of @DynamoDbImmutable the methods must have the same name as the getters
    // And in records that obj.identifer() not obj.getIdentifier()
    private UUID builderIdentifier;
    private DbNote builderNote;
    private String builderVersion;

    private Builder() {}

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
      this.builderIdentifier = identifier;
      return this;
    }

    public Builder note(DbNote note) {
      this.builderNote = note;
      return this;
    }

    public Builder version(String version) {
      this.builderVersion = version;
      return this;
    }

    public NoteDao build() {
      return new NoteDao(builderIdentifier, builderNote, builderVersion);
    }
  }

  @DynamoDbImmutable(builder = DbNote.Builder.class)
  public record DbNote(UUID noteId, Username user, String text, Instant createdDate) {

    public static Builder builder() {
      return new Builder();
    }

    @JacocoGenerated // TODO use later :)
    public static final class Builder {

      private UUID builderNoteId;
      private Username builderUser;
      private String builderText;
      private Instant builderCreatedDate;

      public Builder() {}

      public Builder noteId(UUID noteId) {
        this.builderNoteId = noteId;
        return this;
      }

      public Builder user(Username user) {
        this.builderUser = user;
        return this;
      }

      public Builder text(String text) {
        this.builderText = text;
        return this;
      }

      public Builder createdDate(Instant createdDate) {
        this.builderCreatedDate = createdDate;
        return this;
      }

      public DbNote build() {
        return new DbNote(builderNoteId, builderUser, builderText, builderCreatedDate);
      }
    }
  }
}
