package no.sikt.nva.nvi.common.model;

import java.util.Arrays;
import java.util.Random;
import no.sikt.nva.nvi.common.service.model.InstanceType;

public class InstanceTypeFixtures {
  private static final Random RANDOM = new Random();

  public static InstanceType randomInstanceType() {
    var instanceTypes = Arrays.stream(InstanceType.values()).toList();
    return instanceTypes.get(RANDOM.nextInt(instanceTypes.size()));
  }
}
