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
import no.sikt.nva.nvi.common.db.model.ChannelType;
import no.sikt.nva.nvi.common.db.model.DbContributor;
import no.sikt.nva.nvi.common.db.model.DbPages;
import no.sikt.nva.nvi.common.db.model.DbPublication;
import no.sikt.nva.nvi.common.db.model.DbPublicationDate;
import no.sikt.nva.nvi.common.dto.ContributorDto;
import no.sikt.nva.nvi.common.dto.ContributorRole;
import no.sikt.nva.nvi.common.dto.PageCountDto;
import no.sikt.nva.nvi.common.dto.PublicationDateDto;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.dto.VerificationStatus;
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
    PageCountDto pageCount,
    PublicationChannel publicationChannel,
    List<PublicationChannel> publicationChannels,
    PublicationDateDto publicationDate,
    InstanceType publicationType,
    boolean isApplicable,
    boolean isInternationalCollaboration,
    List<VerifiedNviCreatorDto> verifiedCreators,
    List<UnverifiedNviCreatorDto> unverifiedCreators,
    List<ContributorDto> contributors,
    List<Organization> topLevelOrganizations,
    Instant modifiedDate) {

  public static PublicationDetails from(UpsertNviCandidateRequest upsertRequest) {
    var publicationDto = upsertRequest.publicationDetails();
    var publicationChannel =
        new PublicationChannel(
            ChannelType.parse(upsertRequest.channelType()),
            upsertRequest.publicationChannelId(),
            upsertRequest.level());

    var publicationChannels =
        publicationDto.publicationChannels().stream().map(PublicationChannel::from).toList();

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
        .withPageCount(publicationDto.pageCount())
        .withIsApplicable(publicationDto.isApplicable())
        .withIsInternationalCollaboration(publicationDto.isInternationalCollaboration())
        .withPublicationChannel(publicationChannel)
        .withPublicationChannels(publicationChannels)
        .withVerifiedNviCreators(upsertRequest.verifiedCreators())
        .withUnverifiedNviCreators(upsertRequest.unverifiedCreators())
        .withContributors(publicationDto.contributors())
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

    return builder()
        .withId(dbDetails.id())
        .withPublicationBucketUri(dbDetails.publicationBucketUri())
        .withIdentifier(dbDetails.identifier())
        .withTitle(dbDetails.title())
        .withStatus(dbDetails.status())
        .withLanguage(dbDetails.language())
        .withAbstract(dbDetails.abstractText())
        .withPublicationDate(dateFromDbDate(dbCandidate.publicationDate()))
        .withPublicationType(dbDetails.publicationType())
        .withPageCount(getPages(dbDetails))
        .withIsApplicable(dbDetails.isApplicable())
        .withIsInternationalCollaboration(dbDetails.isInternationalCollaboration())
        .withPublicationChannel(
            new PublicationChannel(
                dbCandidate.channelType(), dbCandidate.channelId(), dbCandidate.level().getValue()))
        .withPublicationChannels(
            dbDetails.publicationChannels().stream().map(PublicationChannel::from).toList())
        .withContributors(
            contributorsFromDbContributors(dbDetails.contributors())) // FIXME: Add creators?
        .withVerifiedNviCreators(verifiedCreators)
        .withUnverifiedNviCreators(unverifiedCreators)
        .withTopLevelOrganizations(
            nonNull(dbDetails.topLevelOrganizations())
                ? dbDetails.topLevelOrganizations().stream().map(Organization::from).toList()
                : emptyList())
        .withModifiedDate(dbDetails.modifiedDate())
        .build();
  }

  private static PageCountDto getPages(DbPublication dbDetails) {
    var dbPages = dbDetails.pages();
    if (nonNull(dbPages)) {
      return new PageCountDto(dbPages.firstPage(), dbPages.lastPage(), dbPages.pageCount());
    }
    return null;
  }

  private DbPages getDbPages() {
    if (nonNull(pageCount)) {
      return new DbPages(pageCount.firstPage(), pageCount.lastPage(), pageCount.numberOfPages());
    }
    return null;
  }

  public DbPublication toDbPublication() {
    var allCreators = mapToDbCreators(verifiedCreators, unverifiedCreators);
    var channels =
        publicationChannels.stream().map(PublicationChannel::toDbPublicationChannel).toList();
    return DbPublication.builder()
        .id(publicationId)
        .identifier(publicationIdentifier)
        .title(title)
        .status(status)
        .language(language)
        .abstractText(abstractText)
        .pages(getDbPages())
        .publicationDate(mapToPublicationDate(publicationDate))
        .publicationType(publicationType)
        .applicable(isApplicable)
        .internationalCollaboration(isInternationalCollaboration)
        .publicationChannels(channels)
        .contributors(
            contributors.stream().map(PublicationDetails::dbContributorFromContributor).toList())
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

  private static PublicationDateDto dateFromDbDate(DbPublicationDate dbPublicationDate) {
    return new PublicationDateDto(
        dbPublicationDate.year(), dbPublicationDate.month(), dbPublicationDate.day());
  }

  private static List<ContributorDto> contributorsFromDbContributors(
      Collection<DbContributor> dbContributors) {
    return dbContributors.stream()
        .map(PublicationDetails::contributorFromDbContributor)
        .collect(Collectors.toList());
  }

  private static ContributorDto contributorFromDbContributor(DbContributor dbContributor) {
    return ContributorDto.builder()
        .withId(dbContributor.id())
        .withName(dbContributor.name())
        .withVerificationStatus(new VerificationStatus(dbContributor.verificationStatus()))
        .withRole(new ContributorRole(dbContributor.role()))
        .withAffiliations(dbContributor.affiliations().stream().map(Organization::from).toList())
        .build();
  }

  private static DbContributor dbContributorFromContributor(ContributorDto contributor) {
    return DbContributor.builder()
        .id(contributor.id())
        .name(contributor.name())
        .verificationStatus(
            nonNull(contributor.verificationStatus())
                ? contributor.verificationStatus().getValue()
                : "Unverified") // TODO: Handle null verification status
        .role(
            nonNull(contributor.role())
                ? contributor.role().getValue()
                : "Unknown") // TODO: Handle null role
        .affiliations(
            contributor.affiliations().stream().map(Organization::toDbOrganization).toList())
        .build();
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
    private PageCountDto pageCount;
    private PublicationDateDto publicationDate;
    private InstanceType publicationType;
    private boolean isApplicable;
    private boolean isInternationalCollaboration;
    private PublicationChannel publicationChannel;
    private List<PublicationChannel> publicationChannels = emptyList();
    private List<VerifiedNviCreatorDto> verifiedCreators = emptyList();
    private List<UnverifiedNviCreatorDto> unverifiedCreators = emptyList();
    private List<ContributorDto> contributors = emptyList();
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

    public Builder withPageCount(PageCountDto pageCount) {
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

    public Builder withContributors(Collection<ContributorDto> contributors) {
      this.contributors = List.copyOf(contributors);
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
          publicationChannels,
          publicationDate,
          publicationType,
          isApplicable,
          isInternationalCollaboration,
          verifiedCreators,
          unverifiedCreators,
          contributors,
          topLevelOrganizations,
          modifiedDate);
    }
  }
}
