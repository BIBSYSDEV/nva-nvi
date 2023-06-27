package no.sikt.nva.nvi.common;

import static no.unit.nva.s3.S3Driver.AWS_REGION_ENV_VARIABLE;
import nva.commons.core.Environment;
import software.amazon.awssdk.regions.Region;

public class ApplicationConstants {

    public static final Environment ENVIRONMENT = new Environment();

    public static final String SEARCH_INFRASTRUCTURE_API_URI = readSearchInfrastructureApiUri();

    public static final String SEARCH_INFRASTRUCTURE_AUTH_URI = readSearchInfrastructureAuthUri();

    public static final Region REGION = acquireAwsRegion();

    private static String readSearchInfrastructureApiUri() {
        return ENVIRONMENT.readEnv("SEARCH_INFRASTRUCTURE_API_URI");
    }

    private static String readSearchInfrastructureAuthUri() {
        return ENVIRONMENT.readEnv("SEARCH_INFRASTRUCTURE_AUTH_URI");
    }

    private static Region acquireAwsRegion() {
        return ENVIRONMENT.readEnvOpt(AWS_REGION_ENV_VARIABLE)
                   .map(Region::of)
                   .orElse(Region.EU_WEST_1);
    }
}
