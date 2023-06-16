package no.sikt.nva.nvi.evaluator.aws;

import nva.commons.core.Environment;
import software.amazon.awssdk.regions.Region;

import static no.unit.nva.s3.S3Driver.AWS_REGION_ENV_VARIABLE;

public class RegionUtil {

    private RegionUtil() { }

    private static final Environment ENVIRONMENT = new Environment();

    public static Region acquireAwsRegion() {
        return ENVIRONMENT.readEnvOpt(AWS_REGION_ENV_VARIABLE)
                .map(Region::of)
                .orElse(Region.EU_WEST_1);
    }
}
