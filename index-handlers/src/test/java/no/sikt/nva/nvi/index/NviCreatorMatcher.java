package no.sikt.nva.nvi.index;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.Candidate;

/**
 * Matches a contributor node from an expanded resource to the NVI creator persisted on the
 * Candidate: verified creators by id, unverified creators by name. Used by the test-side expected
 * document builder in {@link IndexDocumentTestUtils}.
 */
final class NviCreatorMatcher {

  private NviCreatorMatcher() {}

  static Optional<NviCreatorDto> getAnyNviCreatorIfPresent(
      JsonNode contributorNode, Candidate candidate) {
    return getVerifiedNviCreatorIfPresent(contributorNode, candidate)
        .map(NviCreatorDto.class::cast)
        .or(
            () ->
                getUnverifiedNviCreatorIfPresent(contributorNode, candidate)
                    .map(NviCreatorDto.class::cast));
  }

  private static Optional<VerifiedNviCreatorDto> getVerifiedNviCreatorIfPresent(
      JsonNode contributorNode, Candidate candidate) {
    var contributorId = ExpandedResourceGenerator.extractId(contributorNode);
    return candidate.publicationDetails().verifiedCreators().stream()
        .filter(creator -> creator.id().toString().equals(contributorId))
        .findFirst();
  }

  private static Optional<UnverifiedNviCreatorDto> getUnverifiedNviCreatorIfPresent(
      JsonNode contributorNode, Candidate candidate) {
    var contributorName = ExpandedResourceGenerator.extractName(contributorNode);
    return candidate.publicationDetails().unverifiedCreators().stream()
        .filter(creator -> creator.name().equals(contributorName))
        .findFirst();
  }
}
