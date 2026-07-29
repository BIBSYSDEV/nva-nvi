package no.sikt.nva.nvi.common.utils;

import static no.unit.nva.s3.S3Driver.AWS_REGION_ENV_VARIABLE;

import java.net.URI;
import java.time.Year;
import java.time.ZoneId;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;
import software.amazon.awssdk.regions.Region;

public final class ApplicationConstants {

  public static final ZoneId DEFAULT_TIME_ZONE = ZoneId.of("Europe/Oslo");

  private ApplicationConstants() {}

  public static Year getCurrentYear() {
    return Year.now(DEFAULT_TIME_ZONE);
  }

  public static String getTableName(Environment environment) {
    return environment.readEnv("NVI_TABLE_NAME");
  }

  @JacocoGenerated
  public static Region getRegion(Environment environment) {
    return environment.readEnvOpt(AWS_REGION_ENV_VARIABLE).map(Region::of).orElse(Region.EU_WEST_1);
  }

  public static URI getBaseUri(Environment environment) {
    return UriWrapper.fromHost(environment.readEnv("API_HOST"))
        .addChild(environment.readEnv("CUSTOM_DOMAIN_BASE_PATH"))
        .getUri();
  }
}
