package no.sikt.nva.nvi.common.db.model;

import static java.util.Collections.emptyList;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = DbPointCalculation.Builder.class)
public record DbPointCalculation(
    BigDecimal basePoints,
    BigDecimal collaborationFactor,
    BigDecimal totalPoints,
    DbPublicationChannel publicationChannel,
    List<DbInstitutionPoints> institutionPoints,
    boolean internationalCollaboration,
    int creatorShareCount,
    String instanceType) {

  public DbPointCalculation {
    institutionPoints =
        Optional.ofNullable(institutionPoints).map(List::copyOf).orElse(emptyList());
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private BigDecimal basePoints;
    private BigDecimal collaborationFactor;
    private BigDecimal totalPoints;
    private DbPublicationChannel publicationChannel;
    private List<DbInstitutionPoints> institutionPoints;
    private boolean internationalCollaboration;
    private int creatorShareCount;
    private String instanceType;

    private Builder() {}

    public Builder basePoints(BigDecimal basePoints) {
      this.basePoints = basePoints;
      return this;
    }

    public Builder collaborationFactor(BigDecimal collaborationFactor) {
      this.collaborationFactor = collaborationFactor;
      return this;
    }

    public Builder totalPoints(BigDecimal totalPoints) {
      this.totalPoints = totalPoints;
      return this;
    }

    public Builder publicationChannel(DbPublicationChannel publicationChannel) {
      this.publicationChannel = publicationChannel;
      return this;
    }

    public Builder institutionPoints(List<DbInstitutionPoints> institutionPoints) {
      this.institutionPoints = institutionPoints;
      return this;
    }

    public Builder internationalCollaboration(boolean internationalCollaboration) {
      this.internationalCollaboration = internationalCollaboration;
      return this;
    }

    public Builder creatorShareCount(int creatorShareCount) {
      this.creatorShareCount = creatorShareCount;
      return this;
    }

    public Builder instanceType(String instanceType) {
      this.instanceType = instanceType;
      return this;
    }

    public DbPointCalculation build() {
      return new DbPointCalculation(
          basePoints,
          collaborationFactor,
          totalPoints,
          publicationChannel,
          institutionPoints,
          internationalCollaboration,
          creatorShareCount,
          instanceType);
    }
  }
}
