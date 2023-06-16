package no.sikt.nva.nvi.evaluator.aws;

import static no.unit.nva.s3.S3Driver.AWS_REGION_ENV_VARIABLE;
import nva.commons.core.Environment;
import software.amazon.awssdk.regions.Region;

public final class RegionUtil {

    private static final Environment ENVIRONMENT = new Environment();

    private RegionUtil() {
    }

    public static Region acquireAwsRegion() {
        return ENVIRONMENT.readEnvOpt(AWS_REGION_ENV_VARIABLE)
                   .map(Region::of)
                   .orElse(Region.EU_WEST_1);
    }
}
