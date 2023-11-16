package no.sikt.nva.nvi.events.evaluator.aws;

import static no.unit.nva.s3.S3Driver.AWS_REGION_ENV_VARIABLE;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.regions.Region;

public final class RegionUtil {

    private RegionUtil() {
    }

    @JacocoGenerated
    public static Region acquireAwsRegion() {
        return new Environment().readEnvOpt(AWS_REGION_ENV_VARIABLE)
                   .map(Region::of)
                   .orElse(Region.EU_WEST_1);
    }
}
