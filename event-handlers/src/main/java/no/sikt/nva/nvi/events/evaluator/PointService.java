package no.sikt.nva.nvi.events.evaluator;

import java.util.Collection;
import java.util.Optional;
import no.sikt.nva.nvi.common.dto.ContributorDto;
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.events.evaluator.calculator.PointCalculator;
import no.sikt.nva.nvi.events.evaluator.model.Channel;
import no.sikt.nva.nvi.events.evaluator.model.NviCreator;
import no.sikt.nva.nvi.events.evaluator.model.NviOrganization;
import no.sikt.nva.nvi.events.evaluator.model.PointCalculation;
import no.sikt.nva.nvi.events.evaluator.model.PublicationChannel;
import no.sikt.nva.nvi.events.evaluator.model.UnverifiedNviCreator;
import no.sikt.nva.nvi.events.evaluator.model.VerifiedNviCreator;

public final class PointService {

  private PointService() {}

  public static PointCalculation calculatePoints(
      PublicationDto publication,
      Collection<VerifiedNviCreator> verifiedNviCreators,
      Collection<UnverifiedNviCreator> unverifiedNviCreators) {
    var instanceType = publication.publicationType();
    var channel = getNviChannel(publication);
    var isInternationalCollaboration = publication.isInternationalCollaboration();
    var totalShares = getTotalShares(publication, verifiedNviCreators, unverifiedNviCreators);

    return new PointCalculator(
            channel,
            instanceType,
            verifiedNviCreators,
            unverifiedNviCreators,
            isInternationalCollaboration,
            totalShares)
        .calculatePoints();
  }

  private static Channel getNviChannel(PublicationDto publication) {
    var channelDto =
        switch (publication.publicationType()) {
          case ACADEMIC_ARTICLE, ACADEMIC_LITERATURE_REVIEW -> getJournal(publication);
          case ACADEMIC_MONOGRAPH, ACADEMIC_COMMENTARY, ACADEMIC_CHAPTER ->
              getSeriesOrPublisher(publication);
        };
    var channelType = PublicationChannel.parse(channelDto.channelType());
    return new Channel(channelDto.id(), channelType, channelDto.scientificValue());
  }

  private static Optional<PublicationChannelDto> getChannel(
      PublicationDto publication, PublicationChannel channelType) {
    return publication.publicationChannels().stream()
        .filter(channel -> channelType.getValue().equals(channel.channelType()))
        .filter(channel -> channel.scientificValue().isValid())
        .findFirst();
  }

  private static PublicationChannelDto getJournal(PublicationDto publication) {
    return getChannel(publication, PublicationChannel.JOURNAL).orElseThrow();
  }

  private static PublicationChannelDto getSeriesOrPublisher(PublicationDto publication) {
    return getChannel(publication, PublicationChannel.SERIES)
        .orElseGet(() -> getChannel(publication, PublicationChannel.PUBLISHER).orElseThrow());
  }

  private static int getTotalShares(
      PublicationDto publication,
      Collection<VerifiedNviCreator> verifiedNviCreators,
      Collection<UnverifiedNviCreator> unverifiedNviCreators) {

    var totalCreatorCount =
        (int) publication.contributors().stream().filter(ContributorDto::isCreator).count();

    var verifiedShares =
        verifiedNviCreators.stream().mapToInt(PointService::countOfExtraCreatorShares).sum();

    var unverifiedShares =
        unverifiedNviCreators.stream().mapToInt(PointService::countOfExtraCreatorShares).sum();

    return totalCreatorCount + verifiedShares + unverifiedShares;
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
