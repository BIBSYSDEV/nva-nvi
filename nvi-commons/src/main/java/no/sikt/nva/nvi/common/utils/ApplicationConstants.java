package no.sikt.nva.nvi.common.utils;

import static no.unit.nva.s3.S3Driver.AWS_REGION_ENV_VARIABLE;

import java.time.Year;
import java.time.ZoneId;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.regions.Region;

@JacocoGenerated
public final class ApplicationConstants {

  public static final Environment ENVIRONMENT = new Environment();
  public static final String NVI_TABLE_NAME = readNviTableName();
  public static final Region REGION = acquireAwsRegion();
  public static final ZoneId DEFAULT_TIME_ZONE = ZoneId.of("Europe/Oslo");

  private ApplicationConstants() {}

  public static Year getCurrentYear() {
    return Year.now(DEFAULT_TIME_ZONE);
  }

  private static String readNviTableName() {
    return ENVIRONMENT.readEnv("NVI_TABLE_NAME");
  }

  private static Region acquireAwsRegion() {
    return ENVIRONMENT.readEnvOpt(AWS_REGION_ENV_VARIABLE).map(Region::of).orElse(Region.EU_WEST_1);
  }
}
