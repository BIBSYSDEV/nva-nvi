package no.sikt.nva.nvi.common.model;

import static no.unit.nva.testutils.RandomDataGenerator.randomElement;

import java.util.List;

public class EnumFixtures {

  public static InstanceType randomValidInstanceType() {
    var values =
        List.of(
            InstanceType.ACADEMIC_COMMENTARY,
            InstanceType.ACADEMIC_MONOGRAPH,
            InstanceType.ACADEMIC_CHAPTER,
            InstanceType.ACADEMIC_ARTICLE,
            InstanceType.ACADEMIC_LITERATURE_REVIEW);
    return randomElement(values);
  }

  public static ScientificValue randomValidScientificValue() {
    var values = List.of(ScientificValue.LEVEL_ONE, ScientificValue.LEVEL_TWO);
    return randomElement(values);
  }

  public static ChannelType randomValidChannelType() {
    var values = List.of(ChannelType.JOURNAL, ChannelType.SERIES, ChannelType.PUBLISHER);
    return randomElement(values);
  }
}
