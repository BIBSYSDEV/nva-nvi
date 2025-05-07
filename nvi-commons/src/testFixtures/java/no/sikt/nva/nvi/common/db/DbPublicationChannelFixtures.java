package no.sikt.nva.nvi.common.db;

import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import no.sikt.nva.nvi.common.db.model.DbPublicationChannel;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.model.ChannelType;
import no.sikt.nva.nvi.common.model.ScientificValue;

public class DbPublicationChannelFixtures {

  public static DbPublicationChannel.Builder randomPublicationChannelBuilder() {
    return DbPublicationChannel.builder()
        .id(randomUri())
        .channelType(randomElement(ChannelType.values()).getValue())
        .scientificValue(ScientificValue.LEVEL_ONE.getValue());
  }

  public static DbPublicationChannel getExpectedDbPublicationChannel(
      UpsertNviCandidateRequest request) {
    var channelDto = request.publicationChannelForLevel();
    return DbPublicationChannel.builder()
        .id(channelDto.id())
        .channelType(channelDto.channelType().getValue())
        .scientificValue(channelDto.scientificValue().getValue())
        .build();
  }
}
