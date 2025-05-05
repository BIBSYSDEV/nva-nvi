package no.sikt.nva.nvi.common.service.model;

import static java.util.Objects.nonNull;

import java.net.URI;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.model.ChannelType;
import no.sikt.nva.nvi.common.db.model.DbPublicationChannel;
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;
import no.sikt.nva.nvi.common.model.ScientificValue;

// FIXME: Move ChannelType enum to commons and update it
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
    return new PublicationChannel(
        candidateDao.candidate().channelType(),
        candidateDao.candidate().channelId(),
        scientificValue);
  }

  public static PublicationChannel from(DbPublicationChannel dbPublicationChannel) {
    var scientificValue =
        nonNull(dbPublicationChannel.scientificValue())
            ? ScientificValue.parse(dbPublicationChannel.scientificValue())
            : null;
    return new PublicationChannel(
        dbPublicationChannel.channelType(), dbPublicationChannel.id(), scientificValue);
  }

  public DbPublicationChannel toDbPublicationChannel() {
    return DbPublicationChannel.builder()
        .id(id)
        .channelType(channelType)
        .scientificValue(scientificValue.getValue())
        .build();
  }
}
