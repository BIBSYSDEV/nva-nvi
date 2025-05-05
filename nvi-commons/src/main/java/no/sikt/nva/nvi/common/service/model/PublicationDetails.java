package no.sikt.nva.nvi.common.service.model;

import static java.util.Collections.emptyList;
import static java.util.Objects.nonNull;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreatorType;
import no.sikt.nva.nvi.common.db.model.DbPages;
import no.sikt.nva.nvi.common.db.model.DbPublication;
import no.sikt.nva.nvi.common.db.model.DbPublicationDate;
import no.sikt.nva.nvi.common.dto.PublicationDateDto;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.model.InstanceType;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;

// TODO: Can we remove this JsonSerialize annotation?
@JsonSerialize
public record PublicationDetails(
    URI publicationId,
    URI publicationBucketUri,
    String publicationIdentifier,
    String title,
    String status,
    String language,
    String abstractText,
    PageCount pageCount,
    PublicationChannel publicationChannel,
    PublicationDateDto publicationDate,
    InstanceType publicationType,
    boolean isApplicable,
    boolean isInternationalCollaboration,
    List<VerifiedNviCreatorDto> verifiedCreators,
    List<UnverifiedNviCreatorDto> unverifiedCreators,
    int contributorCount,
    List<Organization> topLevelOrganizations,
    Instant modifiedDate) {

  public static PublicationDetails from(UpsertNviCandidateRequest upsertRequest) {
    var publicationDto = upsertRequest.publicationDetails();
    var publicationChannel = PublicationChannel.from(upsertRequest.publicationChannelForLevel());

    return builder()
        .withId(upsertRequest.publicationId())
        .withIdentifier(publicationDto.identifier())
        .withPublicationBucketUri(upsertRequest.publicationBucketUri())
        .withTitle(publicationDto.title())
        .withStatus(publicationDto.status())
        .withLanguage(publicationDto.language())
        .withAbstract(publicationDto.abstractText())
        .withPublicationDate(publicationDto.publicationDate())
        .withPublicationType(publicationDto.publicationType())
        .withPageCount(PageCount.from(publicationDto.pageCount()))
        .withIsApplicable(publicationDto.isApplicable())
        .withIsInternationalCollaboration(publicationDto.isInternationalCollaboration())
        .withPublicationChannel(publicationChannel)
        .withVerifiedNviCreators(upsertRequest.verifiedCreators())
        .withUnverifiedNviCreators(upsertRequest.unverifiedCreators())
        .withContributorCount(publicationDto.contributors().size())
        .withTopLevelOrganizations(publicationDto.topLevelOrganizations())
        .withModifiedDate(publicationDto.modifiedDate())
        .build();
  }

  public static PublicationDetails from(CandidateDao candidateDao) {
    var dbCandidate = candidateDao.candidate();
    var dbDetails = dbCandidate.publicationDetails();

    var creators = dbCandidate.creators().stream().map(DbCreatorType::toNviCreator).toList();
    var verifiedCreators =
        creators.stream()
            .filter(VerifiedNviCreatorDto.class::isInstance)
            .map(VerifiedNviCreatorDto.class::cast)
            .toList();
    var unverifiedCreators =
        creators.stream()
            .filter(UnverifiedNviCreatorDto.class::isInstance)
            .map(UnverifiedNviCreatorDto.class::cast)
            .toList();

    var organizations = getTopLevelOrganizations(dbDetails);

    return builder()
        .withId(dbDetails.id())
        .withPublicationBucketUri(dbDetails.publicationBucketUri())
        .withIdentifier(dbDetails.identifier())
        .withTitle(dbDetails.title())
        .withStatus(dbDetails.status())
        .withLanguage(dbDetails.language())
        .withAbstract(dbDetails.abstractText())
        .withPublicationDate(mapFromDbDate(dbCandidate.getPublicationDate()))
        .withPublicationType(InstanceType.parse(dbDetails.publicationType()))
        .withPageCount(getPages(dbDetails))
        .withIsApplicable(dbDetails.applicable())
        .withIsInternationalCollaboration(dbDetails.internationalCollaboration())
        .withPublicationChannel(PublicationChannel.from(candidateDao))
        .withVerifiedNviCreators(verifiedCreators)
        .withUnverifiedNviCreators(unverifiedCreators)
        .withContributorCount(dbDetails.contributorCount())
        .withTopLevelOrganizations(organizations)
        .withModifiedDate(dbDetails.modifiedDate())
        .build();
  }

  private static List<Organization> getTopLevelOrganizations(DbPublication dbDetails) {
    return nonNull(dbDetails.topLevelOrganizations())
        ? dbDetails.topLevelOrganizations().stream().map(Organization::from).toList()
        : emptyList();
  }

  private static PageCount getPages(DbPublication dbDetails) {
    var dbPages = dbDetails.pages();
    if (nonNull(dbPages)) {
      return PageCount.from(dbPages);
    }
    return null;
  }

  private DbPages getDbPages() {
    if (nonNull(pageCount)) {
      return pageCount.toDbPages();
    }
    return null;
  }

  public DbPublication toDbPublication() {
    var allCreators = mapToDbCreators(verifiedCreators, unverifiedCreators);
    return DbPublication.builder()
        .id(publicationId)
        .publicationBucketUri(publicationBucketUri)
        .identifier(publicationIdentifier)
        .title(title)
        .status(status)
        .language(language)
        .abstractText(abstractText)
        .pages(getDbPages())
        .publicationDate(mapToPublicationDate(publicationDate))
        .publicationType(publicationType.getValue())
        .applicable(isApplicable)
        .internationalCollaboration(isInternationalCollaboration)
        .publicationChannel(publicationChannel.toDbPublicationChannel())
        .creators(allCreators)
        .modifiedDate(modifiedDate)
        .build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public List<NviCreatorDto> getNviCreators() {
    return Stream.concat(verifiedCreators.stream(), unverifiedCreators.stream())
        .map(NviCreatorDto.class::cast)
        .toList();
  }

  public List<NviCreatorDto> creators() {
    return getNviCreators();
  }

  public List<URI> getNviCreatorAffiliations() {
    return Stream.concat(verifiedCreators.stream(), unverifiedCreators.stream())
        .map(NviCreatorDto::affiliations)
        .flatMap(List::stream)
        .toList();
  }

  public List<VerifiedNviCreatorDto> getVerifiedCreators() {
    return verifiedCreators;
  }

  public List<UnverifiedNviCreatorDto> getUnverifiedCreators() {
    return unverifiedCreators;
  }

  public Set<URI> getVerifiedNviCreatorIds() {
    return verifiedCreators.stream().map(VerifiedNviCreatorDto::id).collect(Collectors.toSet());
  }

  private static List<DbCreatorType> mapToDbCreators(
      Collection<VerifiedNviCreatorDto> verifiedNviCreators,
      Collection<UnverifiedNviCreatorDto> unverifiedNviCreators) {
    var verifiedCreators = verifiedNviCreators.stream().map(VerifiedNviCreatorDto::toDao);
    var unverifiedCreators = unverifiedNviCreators.stream().map(UnverifiedNviCreatorDto::toDao);
    return Stream.concat(verifiedCreators, unverifiedCreators)
        .map(DbCreatorType.class::cast)
        .toList();
  }

  private static PublicationDateDto mapFromDbDate(DbPublicationDate dbPublicationDate) {
    return new PublicationDateDto(
        dbPublicationDate.year(), dbPublicationDate.month(), dbPublicationDate.day());
  }

  private static DbPublicationDate mapToPublicationDate(PublicationDateDto publicationDate) {
    return DbPublicationDate.builder()
        .year(publicationDate.year())
        .month(publicationDate.month())
        .day(publicationDate.day())
        .build();
  }

  public static final class Builder {

    private URI id;
    private URI publicationBucketUri;
    private String identifier;
    private String title;
    private String status;
    private String language;
    private String abstractText;
    private PageCount pageCount;
    private PublicationDateDto publicationDate;
    private InstanceType publicationType;
    private boolean isApplicable;
    private boolean isInternationalCollaboration;
    private PublicationChannel publicationChannel;
    private List<PublicationChannel> publicationChannels = emptyList();
    private List<VerifiedNviCreatorDto> verifiedCreators = emptyList();
    private List<UnverifiedNviCreatorDto> unverifiedCreators = emptyList();
    private int contributorCount;
    private List<Organization> topLevelOrganizations = emptyList();
    private Instant modifiedDate;

    private Builder() {}

    public Builder withId(URI id) {
      this.id = id;
      return this;
    }

    public Builder withPublicationBucketUri(URI publicationBucketUri) {
      this.publicationBucketUri = publicationBucketUri;
      return this;
    }

    public Builder withIdentifier(String identifier) {
      this.identifier = identifier;
      return this;
    }

    public Builder withTitle(String title) {
      this.title = title;
      return this;
    }

    public Builder withAbstract(String abstractText) {
      this.abstractText = abstractText;
      return this;
    }

    public Builder withPageCount(PageCount pageCount) {
      this.pageCount = pageCount;
      return this;
    }

    public Builder withPublicationDate(PublicationDateDto publicationDate) {
      this.publicationDate = publicationDate;
      return this;
    }

    public Builder withStatus(String status) {
      this.status = status;
      return this;
    }

    public Builder withPublicationType(InstanceType publicationType) {
      this.publicationType = publicationType;
      return this;
    }

    public Builder withLanguage(String language) {
      this.language = language;
      return this;
    }

    public Builder withIsApplicable(boolean isApplicable) {
      this.isApplicable = isApplicable;
      return this;
    }

    public Builder withIsInternationalCollaboration(boolean isInternationalCollaboration) {
      this.isInternationalCollaboration = isInternationalCollaboration;
      return this;
    }

    public Builder withPublicationChannel(PublicationChannel publicationChannel) {
      this.publicationChannel = publicationChannel;
      return this;
    }

    public Builder withPublicationChannels(Collection<PublicationChannel> publicationChannels) {
      this.publicationChannels = List.copyOf(publicationChannels);
      return this;
    }

    public Builder withVerifiedNviCreators(Collection<VerifiedNviCreatorDto> verifiedCreators) {
      this.verifiedCreators = List.copyOf(verifiedCreators);
      return this;
    }

    public Builder withUnverifiedNviCreators(
        Collection<UnverifiedNviCreatorDto> unverifiedCreators) {
      this.unverifiedCreators = List.copyOf(unverifiedCreators);
      return this;
    }

    public Builder withContributorCount(int contributorCount) {
      this.contributorCount = contributorCount;
      return this;
    }

    public Builder withTopLevelOrganizations(Collection<Organization> topLevelOrganizations) {
      this.topLevelOrganizations = List.copyOf(topLevelOrganizations);
      return this;
    }

    public Builder withModifiedDate(Instant modifiedDate) {
      this.modifiedDate = modifiedDate;
      return this;
    }

    public PublicationDetails build() {
      return new PublicationDetails(
          id,
          publicationBucketUri,
          identifier,
          title,
          status,
          language,
          abstractText,
          pageCount,
          publicationChannel,
          publicationDate,
          publicationType,
          isApplicable,
          isInternationalCollaboration,
          verifiedCreators,
          unverifiedCreators,
          contributorCount,
          topLevelOrganizations,
          modifiedDate);
    }
  }
}
