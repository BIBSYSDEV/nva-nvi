package no.sikt.nva.nvi.events.evaluator;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.dto.ContributorDto;
import no.sikt.nva.nvi.common.dto.PointCalculationDto;
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.model.ChannelType;
import no.sikt.nva.nvi.events.evaluator.calculator.PointCalculator;
import no.sikt.nva.nvi.events.evaluator.model.NviCreator;

public final class PointService {

  private PointService() {}

  public static PointCalculationDto calculatePoints(
      PublicationDto publication, Collection<NviCreator> nviCreators) {
    var instanceType = publication.publicationType();
    var channel = getNviChannel(publication);
    var isInternationalCollaboration = publication.isInternationalCollaboration();
    var totalShares = getTotalShares(publication);

    return new PointCalculator(
            channel, instanceType, nviCreators, isInternationalCollaboration, totalShares)
        .calculatePoints();
  }

  private static PublicationChannelDto getNviChannel(PublicationDto publication) {
    return switch (publication.publicationType()) {
      case ACADEMIC_ARTICLE, ACADEMIC_LITERATURE_REVIEW -> getJournal(publication);
      case ACADEMIC_MONOGRAPH, ACADEMIC_COMMENTARY, ACADEMIC_CHAPTER ->
          getSeriesOrPublisher(publication);
      case NON_CANDIDATE -> throw new IllegalArgumentException("Publication type is invalid");
    };
  }

  private static Optional<PublicationChannelDto> getChannel(
      PublicationDto publication, ChannelType channelType) {
    return publication.publicationChannels().stream()
        .filter(channel -> channelType.equals(channel.channelType()))
        .filter(PublicationChannelDto::isValid)
        .findFirst();
  }

  private static PublicationChannelDto getJournal(PublicationDto publication) {
    return getChannel(publication, ChannelType.JOURNAL).orElseThrow();
  }

  private static PublicationChannelDto getSeriesOrPublisher(PublicationDto publication) {
    return getChannel(publication, ChannelType.SERIES)
        .orElseGet(() -> getChannel(publication, ChannelType.PUBLISHER).orElseThrow());
  }

  /**
   * Calculates the total number of forfatterandeler (author shares) for a publication.
   *
   * <p>A forfatterandel is defined as each unique combination of author (creator) and top-level
   * institution in the publication. This means that an author affiliated with multiple institutions
   * will contribute multiple shares to the total.
   *
   * <p>Special case: If a creator has no valid affiliations (no affiliations or all affiliations
   * lack IDs), they contribute exactly 1 forfatterandel.
   *
   * <p>Example: A publication with two contributors where one has two institutions will have three
   * forfatterandeler in total:
   *
   * <ul>
   *   <li>Contributor 1 + Institution A = 1 share
   *   <li>Contributor 1 + Institution B = 1 share
   *   <li>Contributor 2 + Institution A = 1 share
   * </ul>
   */
  private static int getTotalShares(PublicationDto publication) {
    return (int)
        publication.contributors().stream()
            .filter(ContributorDto::isCreator)
            .mapToLong(PointService::getUniqueContributorShare)
            .sum();
  }

  private static int getUniqueContributorShare(ContributorDto contributor) {
    var uniqueTopLevelOrganizations = getUniqueTopLevelOrganizations(contributor);
    return (int) Math.max(1, uniqueTopLevelOrganizations);
  }

  private static long getUniqueTopLevelOrganizations(ContributorDto contributor) {
    return contributor.affiliations().stream()
        .map(Organization::getTopLevelOrg)
        .map(Organization::id)
        .filter(Objects::nonNull)
        .distinct()
        .count();
  }
}
