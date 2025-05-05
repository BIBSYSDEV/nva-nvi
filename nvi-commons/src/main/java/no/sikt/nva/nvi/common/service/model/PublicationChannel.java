package no.sikt.nva.nvi.common.service.model;

import static java.util.Objects.nonNull;

import java.net.URI;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.model.DbPublicationChannel;
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;
import no.sikt.nva.nvi.common.model.ChannelType;
import no.sikt.nva.nvi.common.model.ScientificValue;

// FIXME: Re-order parameters and sync with other versions
public record PublicationChannel(ChannelType channelType, URI id, ScientificValue scientificValue) {

  public static PublicationChannel from(PublicationChannelDto dtoChannel) {
    return new PublicationChannel(
        ChannelType.parse(dtoChannel.channelType()), dtoChannel.id(), dtoChannel.scientificValue());
  }

  /**
   * @deprecated Temporary method while migrating data.
   */
  @Deprecated(since = "2025-05-05", forRemoval = true)
  public static PublicationChannel from(CandidateDao candidateDao) {
    var dbPublicationDetails = candidateDao.candidate().publicationDetails();
    if (nonNull(dbPublicationDetails) && nonNull(dbPublicationDetails.publicationChannel())) {
      return from(candidateDao.candidate().publicationDetails().publicationChannel());
    }

    var scientificValue = ScientificValue.parse(candidateDao.candidate().level().getValue());
    var channelType = ChannelType.parse(candidateDao.candidate().channelType());
    return new PublicationChannel(
        channelType, candidateDao.candidate().channelId(), scientificValue);
  }

  public static PublicationChannel from(DbPublicationChannel dbPublicationChannel) {
    var dbScientificValue = dbPublicationChannel.scientificValue();
    var scientificValue =
        nonNull(dbScientificValue) ? ScientificValue.parse(dbScientificValue) : null;
    var dbChannelType = dbPublicationChannel.channelType();
    var channelType = nonNull(dbChannelType) ? ChannelType.parse(dbChannelType) : null;
    return new PublicationChannel(channelType, dbPublicationChannel.id(), scientificValue);
  }

  public DbPublicationChannel toDbPublicationChannel() {
    return DbPublicationChannel.builder()
        .id(id)
        .channelType(channelType.getValue())
        .scientificValue(scientificValue.getValue())
        .build();
  }
}
