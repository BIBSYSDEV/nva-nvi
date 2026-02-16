package no.sikt.nva.nvi.events.evaluator;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.dto.ContributorDto;
import no.sikt.nva.nvi.common.dto.PointCalculationDto;
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.model.ChannelType;
import no.sikt.nva.nvi.common.model.Customer;
import no.sikt.nva.nvi.events.evaluator.calculator.PointCalculator;
import no.sikt.nva.nvi.events.evaluator.model.NviCreator;

public final class PointService {

  private PointService() {}

  public static PointCalculationDto calculatePoints(
      PublicationDto publication,
      Collection<NviCreator> nviCreators,
      Map<URI, Customer> customers) {
    var instanceType = publication.publicationType();
    var channel = getNviChannel(publication);
    var isInternationalCollaboration = publication.isInternationalCollaboration();
    var totalShares = getTotalShares(publication);

    return new PointCalculator(
            channel,
            instanceType,
            nviCreators,
            isInternationalCollaboration,
            totalShares,
            customers)
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
        .filter(channel -> channel.channelType() == channelType)
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
   * Calculates the total number of shares (forfatterandeler) for a publication.
   *
   * <p>A share is defined as a unique combination of contributor with role Creator and top-level
   * affiliation. This means that a contributor affiliated with multiple unique top-level
   * affiliations will contribute multiple shares to the total.
   *
   * <p>Special case: If a contributor has no valid affiliations (no affiliations or all
   * affiliations lack IDs), they contribute exactly one share.
   *
   * <p>Example: A publication with one contributor with a single affiliation and a second
   * contributor with two unique affiliations will have three shares in total:
   *
   * <ul>
   *   <li>Contributor 1 + affiliation A = 1 share
   *   <li>Contributor 1 + affiliation B = 1 share
   *   <li>Contributor 2 + affiliation A = 1 share
   * </ul>
   */
  private static int getTotalShares(PublicationDto publication) {
    return publication.contributors().stream()
        .filter(ContributorDto::isCreator)
        .mapToInt(PointService::getUniqueContributorShare)
        .sum();
  }

  private static int getUniqueContributorShare(ContributorDto contributor) {
    var uniqueTopLevelOrganizations = getUniqueTopLevelOrganizations(contributor);
    return Math.max(1, uniqueTopLevelOrganizations);
  }

  private static int getUniqueTopLevelOrganizations(ContributorDto contributor) {
    return Math.toIntExact(
        contributor.affiliations().stream()
            .map(Organization::getTopLevelOrg)
            .map(Organization::id)
            .filter(Objects::nonNull)
            .distinct()
            .count());
  }
}
