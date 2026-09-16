package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.model.EnumFixtures.randomValidChannelType;
import static no.unit.nva.testutils.RandomDataGenerator.randomIssn;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import no.sikt.nva.nvi.common.db.model.DbPublicationChannel;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.model.ScientificValue;

public final class DbPublicationChannelFixtures {

  private DbPublicationChannelFixtures() {}

  public static DbPublicationChannel.Builder randomDbPublicationChannelBuilder() {
    return DbPublicationChannel.builder()
        .id(randomUri())
        .channelType(randomValidChannelType().getValue())
        .scientificValue(ScientificValue.LEVEL_ONE.getValue())
        .name(randomString())
        .printIssn(randomIssn());
  }

  public static DbPublicationChannel getExpectedDbPublicationChannel(
      UpsertNviCandidateRequest request) {
    var channelDto = request.pointCalculation().channel();
    return DbPublicationChannel.builder()
        .id(channelDto.id())
        .channelType(channelDto.channelType().getValue())
        .scientificValue(channelDto.scientificValue().getValue())
        .name(channelDto.name())
        .printIssn(channelDto.printIssn())
        .build();
  }
}
