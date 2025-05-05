package no.sikt.nva.nvi.common.model;

import java.util.Arrays;
import java.util.Random;

public class InstanceTypeFixtures {
  private static final Random RANDOM = new Random();

  public static InstanceType randomInstanceType() {
    var instanceTypes = Arrays.stream(InstanceType.values()).toList();
    return instanceTypes.get(RANDOM.nextInt(instanceTypes.size()));
  }
}
