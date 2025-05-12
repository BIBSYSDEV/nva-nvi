package no.sikt.nva.nvi.common.service.model;

import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.function.Predicate.not;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.model.DbPublicationDetails;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.model.NviCreator;
import no.sikt.nva.nvi.common.model.PublicationChannel;
import no.sikt.nva.nvi.common.model.PublicationDate;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.unit.nva.identifiers.SortableIdentifier;

public record PublicationDetails(
    URI publicationId,
    URI publicationBucketUri,
    SortableIdentifier publicationIdentifier,
    String title,
    String status,
    String language,
    String abstractText,
    PageCount pageCount,
    PublicationChannel publicationChannel,
    PublicationDate publicationDate,
    boolean isApplicable,
    List<NviCreator> nviCreators,
    int contributorCount,
    List<Organization> topLevelOrganizations,
    Instant modifiedDate) {

  public static PublicationDetails from(UpsertNviCandidateRequest upsertRequest) {
    var publicationDto = upsertRequest.publicationDetails();
    var publicationChannel = PublicationChannel.from(upsertRequest.pointCalculation().channel());
    var topLevelOrganizations = publicationDto.topLevelOrganizations();
    var nviCreators =
        Stream.concat(
                upsertRequest.unverifiedCreators().stream(),
                upsertRequest.verifiedCreators().stream())
            .map(creator -> NviCreator.from(creator, topLevelOrganizations))
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
        .withPageCount(PageCount.from(publicationDto.pageCount()))
        .withIsApplicable(publicationDto.isApplicable())
        .withPublicationChannel(publicationChannel)
        .withNviCreators(nviCreators)
        .withContributorCount(publicationDto.contributors().size())
        .withTopLevelOrganizations(publicationDto.topLevelOrganizations())
        .withModifiedDate(publicationDto.modifiedDate())
        .build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static PublicationDetails from(CandidateDao candidateDao) {
    var dbCandidate = candidateDao.candidate();
    var dbDetails = dbCandidate.publicationDetails();

    var topLevelOrganizations = getTopLevelOrganizations(dbDetails);
    var nviCreators =
        dbCandidate.creators().stream()
            .map(creator -> NviCreator.from(creator, topLevelOrganizations))
            .toList();

    // TODO: Clean-up this when the database is migrated
    // Null-handling of optional/migrated fields
    var publicationIdentifier = nonNull(dbDetails) ? dbDetails.identifier() : null;
    var title = nonNull(dbDetails) ? dbDetails.title() : null;
    var status = nonNull(dbDetails) ? dbDetails.status() : null;
    var language = nonNull(dbDetails) ? dbDetails.language() : null;
    var abstractText = nonNull(dbDetails) ? dbDetails.abstractText() : null;
    var contributorCount = nonNull(dbDetails) ? dbDetails.contributorCount() : 0;
    var modifiedDate = nonNull(dbDetails) ? dbDetails.modifiedDate() : null;
    var pageCount =
        nonNull(dbDetails)
            ? Optional.ofNullable(dbDetails.pages()).map(PageCount::from).orElse(null)
            : null;
    return builder()
        .withId(dbCandidate.publicationId())
        .withPublicationBucketUri(dbCandidate.publicationBucketUri())
        .withIdentifier(publicationIdentifier)
        .withTitle(title)
        .withStatus(status)
        .withLanguage(language)
        .withAbstract(abstractText)
        .withPublicationDate(PublicationDate.from(dbCandidate.getPublicationDate()))
        .withPageCount(pageCount)
        .withIsApplicable(dbCandidate.applicable())
        .withPublicationChannel(PublicationChannel.from(candidateDao))
        .withNviCreators(nviCreators)
        .withContributorCount(contributorCount)
        .withTopLevelOrganizations(topLevelOrganizations)
        .withModifiedDate(modifiedDate)
        .build();
  }

  public DbPublicationDetails toDbPublication() {
    var dbCreators = nviCreators.stream().map(NviCreator::toDbCreatorType).toList();
    var dbPages = Optional.ofNullable(pageCount).map(PageCount::toDbPages).orElse(null);
    return DbPublicationDetails.builder()
        .id(publicationId)
        .publicationBucketUri(publicationBucketUri)
        .identifier(publicationIdentifier.toString())
        .title(title)
        .status(status)
        .language(language)
        .abstractText(abstractText)
        .pages(dbPages)
        .publicationDate(publicationDate.toDbPublicationDate())
        .creators(dbCreators)
        .modifiedDate(modifiedDate)
        .topLevelOrganizations(
            topLevelOrganizations.stream().map(Organization::toDbOrganization).toList())
        .build();
  }

  public List<URI> getNviCreatorAffiliations() {
    return nviCreators.stream()
        .map(NviCreator::getAffiliationIds)
        .flatMap(List::stream)
        .distinct()
        .toList();
  }

  public Set<URI> getVerifiedNviCreatorIds() {
    return verifiedCreators().stream().map(VerifiedNviCreatorDto::id).collect(Collectors.toSet());
  }

  public List<NviCreatorDto> allCreators() {
    return nviCreators.stream().map(NviCreator::toDto).toList();
  }

  public List<VerifiedNviCreatorDto> verifiedCreators() {
    return nviCreators.stream()
        .filter(NviCreator::isVerified)
        .map(NviCreator::toDto)
        .filter(VerifiedNviCreatorDto.class::isInstance)
        .map(VerifiedNviCreatorDto.class::cast)
        .toList();
  }

  public List<UnverifiedNviCreatorDto> unverifiedCreators() {
    return nviCreators.stream()
        .filter(not(NviCreator::isVerified))
        .map(NviCreator::toDto)
        .filter(UnverifiedNviCreatorDto.class::isInstance)
        .map(UnverifiedNviCreatorDto.class::cast)
        .toList();
  }

  private static List<Organization> getTopLevelOrganizations(DbPublicationDetails dbDetails) {
    if (isNull(dbDetails)) {
      return emptyList();
    }
    return nonNull(dbDetails.topLevelOrganizations())
        ? dbDetails.topLevelOrganizations().stream().map(Organization::from).toList()
        : emptyList();
  }

  public static final class Builder {

    private URI id;
    private URI publicationBucketUri;
    private SortableIdentifier identifier;
    private String title;
    private String status;
    private String language;
    private String abstractText;
    private PageCount pageCount;
    private PublicationDate publicationDate;
    private boolean isApplicable;
    private PublicationChannel publicationChannel;
    private List<NviCreator> nviCreators = emptyList();
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
      this.identifier = new SortableIdentifier(identifier);
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

    public Builder withLanguage(String language) {
      this.language = language;
      return this;
    }

    public Builder withIsApplicable(boolean isApplicable) {
      this.isApplicable = isApplicable;
      return this;
    }

    public Builder withPublicationChannel(PublicationChannel publicationChannel) {
      this.publicationChannel = publicationChannel;
      return this;
    }

    public Builder withNviCreators(Collection<NviCreator> nviCreators) {
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
          isApplicable,
          nviCreators,
          contributorCount,
          topLevelOrganizations,
          modifiedDate);
    }
  }
}
