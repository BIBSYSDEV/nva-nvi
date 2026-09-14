package no.sikt.nva.nvi.common.model;

import static java.util.Objects.nonNull;

import java.net.URI;
import java.util.Optional;
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
 * @param name The name of the channel as it was when the candidate was evaluated. This is reported
 *     data and is therefore frozen on the candidate.
 * @param printIssn The print ISSN of the channel as it was when the candidate was evaluated. This
 *     is reported data and is therefore frozen on the candidate.
 */
public record PublicationChannel(
    URI id,
    ChannelType channelType,
    ScientificValue scientificValue,
    String name,
    String printIssn) {

  /**
   * Channel without descriptive metadata. This is not a valid state for channels evaluated in
   * nva-nvi, but occurs for candidates imported from Cristin.
   */
  public PublicationChannel(URI id, ChannelType channelType, ScientificValue scientificValue) {
    this(id, channelType, scientificValue, null, null);
  }

  public static PublicationChannel from(PublicationChannelDto dtoChannel) {
    return new PublicationChannel(
        dtoChannel.id(),
        dtoChannel.channelType(),
        dtoChannel.scientificValue(),
        dtoChannel.name(),
        dtoChannel.printIssn());
  }

  public static PublicationChannel from(DbPublicationChannel dbPublicationChannel) {
    var dbScientificValue = dbPublicationChannel.scientificValue();
    var scientificValue =
        nonNull(dbScientificValue) ? ScientificValue.parse(dbScientificValue) : null;
    var dbChannelType = dbPublicationChannel.channelType();
    var channelType = nonNull(dbChannelType) ? ChannelType.parse(dbChannelType) : null;
    return new PublicationChannel(
        dbPublicationChannel.id(),
        channelType,
        scientificValue,
        dbPublicationChannel.name(),
        dbPublicationChannel.printIssn());
  }

  public DbPublicationChannel toDbPublicationChannel() {
    return DbPublicationChannel.builder()
        .id(id)
        .channelType(Optional.ofNullable(channelType).map(ChannelType::getValue).orElse(null))
        .scientificValue(
            Optional.ofNullable(scientificValue).map(ScientificValue::getValue).orElse(null))
        .name(name)
        .printIssn(printIssn)
        .build();
  }
}
