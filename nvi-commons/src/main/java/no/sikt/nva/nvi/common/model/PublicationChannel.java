package no.sikt.nva.nvi.common.model;

import static java.util.Objects.nonNull;

import java.net.URI;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.model.DbPublicationChannel;
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;

/**
 * This represents the publication channel of a candidate, i.e. the channel used for calculating the
 * NVI status and points for the candidate. While a publication may have multiple channels, only one
 * channel is used for NVI evaluation.
 *
 * @param id Unique ID as a URI, which can be dereferenced for more information.
 * @param channelType The type of channel, i.e. Journal, Series or Publisher.
 * @param scientificValue The scientific value of the channel, which is used for calculating NVI
 *     points. For a valid NVI candidate, this should be LevelOne or LevelTwo.
 */
public record PublicationChannel(URI id, ChannelType channelType, ScientificValue scientificValue) {

  public static PublicationChannel from(PublicationChannelDto dtoChannel) {
    return new PublicationChannel(
        dtoChannel.id(), dtoChannel.channelType(), dtoChannel.scientificValue());
  }

  /**
   * @deprecated Temporary method while migrating data.
   */
  @Deprecated(since = "2025-05-05", forRemoval = true)
  public static PublicationChannel from(CandidateDao candidateDao) {
    var dbPointCalculation = candidateDao.candidate().pointCalculation();
    if (nonNull(dbPointCalculation) && nonNull(dbPointCalculation.publicationChannel())) {
      return from(dbPointCalculation.publicationChannel());
    }

    var scientificValue = ScientificValue.parse(candidateDao.candidate().level().getValue());
    var channelType = ChannelType.parse(candidateDao.candidate().channelType());
    return new PublicationChannel(
        candidateDao.candidate().channelId(), channelType, scientificValue);
  }

  public static PublicationChannel from(DbPublicationChannel dbPublicationChannel) {
    var dbScientificValue = dbPublicationChannel.scientificValue();
    var scientificValue =
        nonNull(dbScientificValue) ? ScientificValue.parse(dbScientificValue) : null;
    var dbChannelType = dbPublicationChannel.channelType();
    var channelType = nonNull(dbChannelType) ? ChannelType.parse(dbChannelType) : null;
    return new PublicationChannel(dbPublicationChannel.id(), channelType, scientificValue);
  }

  public DbPublicationChannel toDbPublicationChannel() {
    return DbPublicationChannel.builder()
        .id(id)
        .channelType(channelType.getValue())
        .scientificValue(scientificValue.getValue())
        .build();
  }
}
