package no.sikt.nva.nvi.common.service.model;

import java.net.URI;
import no.sikt.nva.nvi.common.db.model.ChannelType;
import no.sikt.nva.nvi.common.db.model.DbPublicationChannel;
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;
import no.sikt.nva.nvi.common.model.ScientificValue;

// FIXME: Use ScientificValue enum
// FIXME: Move ChannelType enum to commons and update it
public record PublicationChannel(ChannelType channelType, URI id, String level) {

  public static PublicationChannel from(PublicationChannelDto dto) {
    return new PublicationChannel(
        ChannelType.parse(dto.channelType()), dto.id(), dto.scientificValue().getValue());
  }

  public static PublicationChannel from(DbPublicationChannel dbPublicationChannel) {
    return new PublicationChannel(
        dbPublicationChannel.channelType(),
        dbPublicationChannel.id(),
        dbPublicationChannel.scientificValue().getValue());
  }

  public DbPublicationChannel toDbPublicationChannel() {
    return DbPublicationChannel.builder()
        .id(id)
        .channelType(channelType)
        .scientificValue(ScientificValue.parse(level))
        .build();
  }
}
