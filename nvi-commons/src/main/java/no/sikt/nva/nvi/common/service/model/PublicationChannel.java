package no.sikt.nva.nvi.common.service.model;

import static java.util.Objects.nonNull;

import java.net.URI;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.model.DbPublicationChannel;
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;
import no.sikt.nva.nvi.common.model.ChannelType;
import no.sikt.nva.nvi.common.model.ScientificValue;

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
    var dbPublicationDetails = candidateDao.candidate().publicationDetails();
    if (nonNull(dbPublicationDetails) && nonNull(dbPublicationDetails.publicationChannel())) {
      return from(candidateDao.candidate().publicationDetails().publicationChannel());
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
