package no.sikt.nva.nvi.common.etl;

import static no.sikt.nva.nvi.common.examples.ExampleContributors.EXAMPLE_1_CONTRIBUTOR;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ContributorDtoTest {

  @Test
  void shouldFindExpectedValuesInExampleData() {
    var contributor = EXAMPLE_1_CONTRIBUTOR;
    assertTrue(contributor.isCreator());
    assertTrue(contributor.isVerified());
  }
}
