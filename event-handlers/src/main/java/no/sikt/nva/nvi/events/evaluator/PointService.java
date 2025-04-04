package no.sikt.nva.nvi.events.evaluator;

import static java.util.Objects.nonNull;

import java.util.Collection;
import java.util.List;
import no.sikt.nva.nvi.common.client.model.Organization;
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

  public PointService() {}

  public PointCalculation calculatePoints(
      PublicationDto publication,
      Collection<VerifiedNviCreator> verifiedNviCreators,
      Collection<UnverifiedNviCreator> unverifiedNviCreators) {
    var channel = getNviChannel(publication);
    var creatorShareCount1 = countCreatorShares(verifiedNviCreators);
    var creatorShareCount2 = countCreatorShares(unverifiedNviCreators);
    return new PointCalculator(
            channel,
            publication.publicationType(),
            verifiedNviCreators,
            unverifiedNviCreators,
            publication.isInternationalCollaboration(),
            creatorShareCount1)
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

  // FIXME: Placeholder method, move and clean up
  private static PublicationChannelDto getJournal(PublicationDto publication) {
    return publication.publicationChannels().stream()
        .filter(channel -> "Journal".equals(channel.channelType()))
        .findFirst()
        .orElseThrow();
  }

  // FIXME: Placeholder method, move and clean up
  private static PublicationChannelDto getSeriesOrPublisher(PublicationDto publication) {
    return publication.publicationChannels().stream()
        .filter(channel -> "Series".equals(channel.channelType()))
        .filter(channel -> channel.scientificValue().isValid())
        .findFirst()
        .orElseGet(() -> getPublisher(publication));
  }

  private static PublicationChannelDto getPublisher(PublicationDto publication) {
    return publication.publicationChannels().stream()
        .filter(channel -> "Publisher".equals(channel.channelType()))
        .findFirst()
        .orElseThrow();
  }

  private static int countCreatorsWithoutAffiliations(Collection<NviCreator> creators) {
    return (int)
               creators.stream()
            .map(NviCreator::nviAffiliations)
            .filter(List::isEmpty)
            .count();
  }

  private static boolean isOnlyAffiliatedWithUnidentifiedOrganizations(NviCreator creator) {
    return creator.affiliations().stream().allMatch(PointService::doesNotHaveId);
  }

  private static boolean hasId(NviOrganization organization) {
    return nonNull(organization.id());
  }

  private static boolean doesNotHaveId(NviOrganization organization) {
    return !hasId(organization);
  }

  private static int countCreatorsWithOnlyUnverifiedAffiliations(
      Collection<NviCreator> creators) {
    return (int)
               creators.stream()
            .filter(PointService::isOnlyAffiliatedWithUnidentifiedOrganizations)
            .count();
  }

  private static int countVerifiedTopLevelAffiliations(NviCreator creator) {
    return (int)
               creator.nviAffiliations().stream()
            .map(NviOrganization::topLevelOrganization)
            .filter(PointService::hasId)
            .count();
  }

  private int countVerifiedTopLevelAffiliationsPerCreator(Collection<NviCreator> creators) {
    return creators.stream()
        .mapToInt(PointService::countVerifiedTopLevelAffiliations)
        .sum();
  }

  private int countCreatorShares(Collection<NviCreator> creators) {
    return Integer.sum(
        Integer.sum(
            countVerifiedTopLevelAffiliationsPerCreator(creators),
            countCreatorsWithoutAffiliations(creators)),
        countCreatorsWithOnlyUnverifiedAffiliations(creators));
  }

  private int countCreatorShares(PublicationDto publication) {
    return Integer.sum(
        Integer.sum(
            countVerifiedTopLevelAffiliationsPerCreator(publication.contributors()),
            countCreatorsWithoutAffiliations(publication.contributors())),
        countCreatorsWithOnlyUnverifiedAffiliations(publication.contributors()));
  }
}
