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
import no.sikt.nva.nvi.common.db.model.DbPublicationDetails;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.model.InstanceType;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;

// FIXME: Revert change to creators and unsplit lists
// TODO: Can we remove this JsonSerialize annotation?
@SuppressWarnings({"PMD.TooManyFields", "PMD.CouplingBetweenObjects"})
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
    PublicationDate publicationDate,
    InstanceType publicationType,
    boolean isApplicable,
    boolean isInternationalCollaboration,
    List<NviCreatorDto> nviCreators,
    int contributorCount,
    List<Organization> topLevelOrganizations,
    Instant modifiedDate) {

  public static PublicationDetails from(UpsertNviCandidateRequest upsertRequest) {
    var publicationDto = upsertRequest.publicationDetails();
    var publicationChannel = PublicationChannel.from(upsertRequest.publicationChannelForLevel());
    var nviCreators =
        Stream.concat(
                upsertRequest.unverifiedCreators().stream(),
                upsertRequest.verifiedCreators().stream())
            .map(NviCreatorDto.class::cast)
            .toList();

    return builder()
        .withId(upsertRequest.publicationId())
        .withIdentifier(publicationDto.identifier())
        .withPublicationBucketUri(upsertRequest.publicationBucketUri())
        .withTitle(publicationDto.title())
        .withStatus(publicationDto.status())
        .withLanguage(publicationDto.language())
        .withAbstract(publicationDto.abstractText())
        .withPublicationDate(PublicationDate.from(publicationDto.publicationDate()))
        .withPublicationType(publicationDto.publicationType())
        .withPageCount(PageCount.from(publicationDto.pageCount()))
        .withIsApplicable(publicationDto.isApplicable())
        .withIsInternationalCollaboration(publicationDto.isInternationalCollaboration())
        .withPublicationChannel(publicationChannel)
        .withNviCreators(nviCreators)
        .withContributorCount(publicationDto.contributors().size())
        .withTopLevelOrganizations(publicationDto.topLevelOrganizations())
        .withModifiedDate(publicationDto.modifiedDate())
        .build();
  }

  public static PublicationDetails from(CandidateDao candidateDao) {
    var dbCandidate = candidateDao.candidate();
    var dbDetails = dbCandidate.publicationDetails();

    var nviCreators = dbCandidate.creators().stream().map(DbCreatorType::toNviCreator).toList();
    var organizations = getTopLevelOrganizations(dbDetails);

    return builder()
        .withId(dbDetails.id())
        .withPublicationBucketUri(dbDetails.publicationBucketUri())
        .withIdentifier(dbDetails.identifier())
        .withTitle(dbDetails.title())
        .withStatus(dbDetails.status())
        .withLanguage(dbDetails.language())
        .withAbstract(dbDetails.abstractText())
        .withPublicationDate(PublicationDate.from(dbCandidate.getPublicationDate()))
        .withPublicationType(InstanceType.parse(dbDetails.publicationType()))
        .withPageCount(getPages(dbDetails))
        .withIsApplicable(dbCandidate.applicable())
        .withIsInternationalCollaboration(dbCandidate.internationalCollaboration())
        .withPublicationChannel(PublicationChannel.from(candidateDao))
        .withNviCreators(nviCreators)
        .withContributorCount(dbDetails.contributorCount())
        .withTopLevelOrganizations(organizations)
        .withModifiedDate(dbDetails.modifiedDate())
        .build();
  }

  public List<VerifiedNviCreatorDto> verifiedCreators() {
    return nviCreators.stream()
        .filter(VerifiedNviCreatorDto.class::isInstance)
        .map(VerifiedNviCreatorDto.class::cast)
        .toList();
  }

  public List<UnverifiedNviCreatorDto> unverifiedCreators() {
    return nviCreators.stream()
        .filter(UnverifiedNviCreatorDto.class::isInstance)
        .map(UnverifiedNviCreatorDto.class::cast)
        .toList();
  }

  private static List<Organization> getTopLevelOrganizations(DbPublicationDetails dbDetails) {
    return nonNull(dbDetails.topLevelOrganizations())
        ? dbDetails.topLevelOrganizations().stream().map(Organization::from).toList()
        : emptyList();
  }

  // FIXME: Move these?
  private static PageCount getPages(DbPublicationDetails dbDetails) {
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

  public DbPublicationDetails toDbPublication() {
    var dbCreators = nviCreators.stream().map(NviCreatorDto::toDao).toList();
    return DbPublicationDetails.builder()
        .id(publicationId)
        .publicationBucketUri(publicationBucketUri)
        .identifier(publicationIdentifier)
        .title(title)
        .status(status)
        .language(language)
        .abstractText(abstractText)
        .pages(getDbPages())
        .publicationDate(publicationDate.toDbPublicationDate())
        .publicationType(publicationType.getValue())
        .publicationChannel(publicationChannel.toDbPublicationChannel())
        .creators(dbCreators)
        .modifiedDate(modifiedDate)
        .topLevelOrganizations(
            topLevelOrganizations.stream().map(Organization::toDbOrganization).toList())
        .build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public List<URI> getNviCreatorAffiliations() {
    return nviCreators.stream().map(NviCreatorDto::affiliations).flatMap(List::stream).toList();
  }

  public Set<URI> getVerifiedNviCreatorIds() {
    return verifiedCreators().stream().map(VerifiedNviCreatorDto::id).collect(Collectors.toSet());
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
    private PublicationDate publicationDate;
    private InstanceType publicationType;
    private boolean isApplicable;
    private boolean isInternationalCollaboration;
    private PublicationChannel publicationChannel;
    private List<NviCreatorDto> nviCreators = emptyList();
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

    public Builder withPublicationDate(PublicationDate publicationDate) {
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

    public Builder withNviCreators(Collection<NviCreatorDto> nviCreators) {
      this.nviCreators = List.copyOf(nviCreators);
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
          nviCreators,
          contributorCount,
          topLevelOrganizations,
          modifiedDate);
    }
  }
}
