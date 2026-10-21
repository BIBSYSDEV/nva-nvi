package no.sikt.nva.nvi.test;

import static no.sikt.nva.nvi.test.TestConstants.JOURNAL_TYPE;
import static no.sikt.nva.nvi.test.TestConstants.PUBLISHER_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;

class SampleExpandedPublicationChannelTest {

  @Test
  void shouldDeriveIdFromTypeIdentifierAndYear() {
    var channel =
        SampleExpandedPublicationChannel.builder()
            .withType(JOURNAL_TYPE)
            .withIdentifier("ABC-123")
            .withYear("2024")
            .build();

    assertThat(channel.id().toString()).endsWith("/serial-publication/ABC-123/2024");
  }

  @Test
  void shouldKeepExplicitId() {
    var explicitId = URI.create("https://example.org/custom-channel");
    var channel =
        SampleExpandedPublicationChannel.builder()
            .withType(JOURNAL_TYPE)
            .withId(explicitId)
            .build();

    assertThat(channel.id()).isEqualTo(explicitId);
  }

  @Test
  void shouldBuildChannelWithoutIdWhenAskedTo() {
    var channel =
        SampleExpandedPublicationChannel.builder().withType(JOURNAL_TYPE).withoutId().build();

    assertThat(channel.id()).isNull();
  }

  @Test
  void shouldFailToDeriveIdWithoutType() {
    var builder = SampleExpandedPublicationChannel.builder();

    assertThatThrownBy(builder::build)
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Channel type is required");
  }

  @Test
  void shouldNotAssignPrintIssnToPublishers() {
    var publisher = SampleExpandedPublicationChannel.builder().withType(PUBLISHER_TYPE).build();
    var journal = SampleExpandedPublicationChannel.builder().withType(JOURNAL_TYPE).build();

    assertThat(publisher.printIssn()).isNull();
    assertThat(journal.printIssn()).isNotBlank();
  }

  @Test
  void shouldCopyEveryFieldWhenBuildingFromExistingChannel() {
    var original = SampleExpandedPublicationChannel.builder().withType(JOURNAL_TYPE).build();

    var copy = SampleExpandedPublicationChannel.from(original).build();

    assertThat(copy).isEqualTo(original);
  }

  @Test
  void shouldDeriveNewIdWhenYearChangesOnCopy() {
    var original = SampleExpandedPublicationChannel.builder().withType(JOURNAL_TYPE).build();

    var moved = SampleExpandedPublicationChannel.from(original).withYear("1999").build();

    assertThat(moved.id().toString()).endsWith("/" + original.identifier() + "/1999");
    assertThat(moved.id()).isNotEqualTo(original.id());
  }

  @Test
  void shouldKeepExplicitIdWhenYearChangesOnCopy() {
    var explicitId = URI.create("https://example.org/custom-channel");
    var original =
        SampleExpandedPublicationChannel.builder()
            .withType(JOURNAL_TYPE)
            .withId(explicitId)
            .build();

    var moved = SampleExpandedPublicationChannel.from(original).withYear("1999").build();

    assertThat(moved.id()).isEqualTo(explicitId);
  }

  @Test
  void shouldStayWithoutIdOnCopy() {
    var original =
        SampleExpandedPublicationChannel.builder().withType(JOURNAL_TYPE).withoutId().build();

    var copy = SampleExpandedPublicationChannel.from(original).withYear("1999").build();

    assertThat(copy.id()).isNull();
  }
}
