package no.sikt.nva.nvi.index;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.dto.ContributorDto;
import no.sikt.nva.nvi.common.dto.ContributorRole;
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.Candidate;

/**
 * Source-of-truth fixture for index-document tests. Holds a {@link Candidate} and the views of it
 * that each generator strategy consumes. The legacy mapper reads the {@link #expandedResource()}
 * JSON; the SPARQL mapper reads the {@link #publicationDto()}. Both views are derived from the same
 * scenario so equivalence tests can run against either.
 *
 * <p>Test classes hold a {@code handlerFor(scenario)} method that wires the scenario into an {@code
 * IndexDocumentHandler}: that method is the swap point between mapper implementations.
 */
public final class IndexDocumentScenario {

  private final Candidate candidate;
  private final JsonNode expandedResource;
  private final PublicationDto publicationDto;

  private IndexDocumentScenario(
      Candidate candidate, JsonNode expandedResource, PublicationDto publicationDto) {
    this.candidate = candidate;
    this.expandedResource = expandedResource;
    this.publicationDto = publicationDto;
  }

  public static IndexDocumentScenario forCandidate(Candidate candidate) {
    var expandedResource =
        ExpandedResourceGenerator.builder()
            .withCandidate(candidate)
            .build()
            .createExpandedResource();
    var publicationDto = buildPublicationDto(candidate);
    return new IndexDocumentScenario(candidate, expandedResource, publicationDto);
  }

  public Candidate candidate() {
    return candidate;
  }

  public JsonNode expandedResource() {
    return expandedResource;
  }

  public PublicationDto publicationDto() {
    return publicationDto;
  }

  /**
   * Builds a {@link PublicationDto} mirroring the candidate. Contributors mirror the candidate's
   * NVI creators (verified and unverified) and we add a non-NVI contributor so both mapper branches
   * get exercised. Publication channels mirror the candidate's channel for the same reason.
   */
  private static PublicationDto buildPublicationDto(Candidate candidate) {
    var details = candidate.publicationDetails();
    return PublicationDto.builder()
        .withId(details.publicationId())
        .withIdentifier(details.publicationIdentifier().toString())
        .withTitle(details.title())
        .withStatus("PUBLISHED")
        .withLanguage(details.language())
        .withPublicationType(candidate.getPublicationType())
        .withIsApplicable(candidate.isApplicable())
        .withIsInternationalCollaboration(candidate.getCollaborationFactor() != null)
        .withPublicationDate(details.publicationDate().toDtoPublicationDate())
        .withTopLevelOrganizations(details.topLevelOrganizations())
        .withContributors(buildContributors(candidate))
        .withPublicationChannels(buildChannels(candidate))
        .withHandles(details.handles())
        .withModifiedDate(Instant.now())
        .build();
  }

  private static List<ContributorDto> buildContributors(Candidate candidate) {
    var details = candidate.publicationDetails();
    var contributors = new ArrayList<ContributorDto>();
    details.verifiedCreators().stream()
        .map(IndexDocumentScenario::contributorFor)
        .forEach(contributors::add);
    details.unverifiedCreators().stream()
        .map(IndexDocumentScenario::contributorFor)
        .forEach(contributors::add);
    contributors.add(nonNviContributor(details.topLevelOrganizations()));
    return contributors;
  }

  private static ContributorDto contributorFor(NviCreatorDto creator) {
    var affiliations =
        creator.affiliations().stream()
            .map(uri -> Organization.builder().withId(uri).build())
            .toList();
    var builder =
        ContributorDto.builder()
            .withName(creator.name())
            .withRole(new ContributorRole("Creator"))
            .withAffiliations(affiliations);
    if (creator instanceof VerifiedNviCreatorDto verified) {
      builder.withId(verified.id());
    }
    return builder.build();
  }

  private static ContributorDto nonNviContributor(Collection<Organization> topLevelOrganizations) {
    var someAffiliation =
        topLevelOrganizations.stream()
            .findFirst()
            .orElseGet(() -> Organization.builder().withId(null).build());
    return ContributorDto.builder()
        .withName(randomString())
        .withRole(new ContributorRole("Editor"))
        .withAffiliations(List.of(someAffiliation))
        .build();
  }

  private static List<PublicationChannelDto> buildChannels(Candidate candidate) {
    var channel = candidate.getPublicationChannel();
    var builder = PublicationChannelDto.builder().withScientificValue(channel.scientificValue());
    if (channel.id() != null) {
      builder.withId(channel.id());
    }
    if (channel.channelType() != null) {
      builder.withChannelType(channel.channelType());
    }
    builder.withName(randomString()).withPrintIssn(randomString());
    return List.of(builder.build());
  }
}
