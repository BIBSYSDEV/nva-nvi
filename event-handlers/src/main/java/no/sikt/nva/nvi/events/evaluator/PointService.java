package no.sikt.nva.nvi.events.evaluator;

import java.util.Collection;
import java.util.Optional;
import no.sikt.nva.nvi.common.dto.ContributorDto;
import no.sikt.nva.nvi.common.dto.PointCalculationDto;
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.model.ChannelType;
import no.sikt.nva.nvi.events.evaluator.calculator.PointCalculator;
import no.sikt.nva.nvi.events.evaluator.model.NviCreator;
import no.sikt.nva.nvi.events.evaluator.model.NviOrganization;

public final class PointService {

  private PointService() {}

  public static PointCalculationDto calculatePoints(
      PublicationDto publication, Collection<NviCreator> nviCreators) {
    var instanceType = publication.publicationType();
    var channel = getNviChannel(publication);
    var isInternationalCollaboration = publication.isInternationalCollaboration();
    var totalShares = getTotalShares(publication, nviCreators);

    return new PointCalculator(
            channel, instanceType, nviCreators, isInternationalCollaboration, totalShares)
        .calculatePoints();
  }

  private static PublicationChannelDto getNviChannel(PublicationDto publication) {
    var channelDto =
        switch (publication.publicationType()) {
          case ACADEMIC_ARTICLE, ACADEMIC_LITERATURE_REVIEW -> getJournal(publication);
          case ACADEMIC_MONOGRAPH, ACADEMIC_COMMENTARY, ACADEMIC_CHAPTER ->
              getSeriesOrPublisher(publication);
          case NON_CANDIDATE -> throw new IllegalArgumentException("Publication type is invalid");
        };
    return channelDto;
  }

  private static Optional<PublicationChannelDto> getChannel(
      PublicationDto publication, ChannelType channelType) {
    return publication.publicationChannels().stream()
        .filter(channel -> channelType.equals(channel.channelType()))
        .filter(channel -> channel.scientificValue().isValid())
        .findFirst();
  }

  private static PublicationChannelDto getJournal(PublicationDto publication) {
    return getChannel(publication, ChannelType.JOURNAL).orElseThrow();
  }

  private static PublicationChannelDto getSeriesOrPublisher(PublicationDto publication) {
    return getChannel(publication, ChannelType.SERIES)
        .orElseGet(() -> getChannel(publication, ChannelType.PUBLISHER).orElseThrow());
  }

  private static int getTotalShares(
      PublicationDto publication, Collection<NviCreator> nviCreators) {

    var totalCreatorCount =
        (int) publication.contributors().stream().filter(ContributorDto::isCreator).count();

    var extraCreatorShares =
        nviCreators.stream().mapToInt(PointService::countOfExtraCreatorShares).sum();

    return totalCreatorCount + extraCreatorShares;
  }

  /**
   * Counts the number of extra shares for a creator based on their affiliations. Creators get one
   * extra share for each verified top-level affiliation beyond the first one.
   */
  private static int countOfExtraCreatorShares(NviCreator creator) {
    var affiliationCount =
        creator.nviAffiliations().stream()
            .map(NviOrganization::topLevelOrganization)
            .map(NviOrganization::id)
            .distinct()
            .count();
    return (int) Math.max(0, affiliationCount - 1);
  }
}
