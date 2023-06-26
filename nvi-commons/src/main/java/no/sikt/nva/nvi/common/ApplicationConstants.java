package no.sikt.nva.nvi.common;

import nva.commons.core.Environment;
import static no.unit.nva.s3.S3Driver.AWS_REGION_ENV_VARIABLE;
import software.amazon.awssdk.regions.Region;

public class ApplicationConstants {

    public static final Environment ENVIRONMENT = new Environment();

    public static final String OPENSEARCH_ENDPOINT = readSearchInfrastructureApiUri();

    public static final Region REGION = acquireAwsRegion();

    private static String readSearchInfrastructureApiUri() {
        return ENVIRONMENT.readEnv("OPENSEARCH_ENDPOINT");
    }

    private static Region acquireAwsRegion() {
        return ENVIRONMENT.readEnvOpt(AWS_REGION_ENV_VARIABLE)
                   .map(Region::of)
                   .orElse(Region.EU_WEST_1);
    }

}
