package no.sikt.nva.nvi.common;

import nva.commons.core.Environment;

public class ApplicationConstants {

    public static final Environment ENVIRONMENT = new Environment();

    public static final String OPENSEARCH_ENDPOINT = readSearchInfrastructureApiUri();

    private static String readSearchInfrastructureApiUri() {
        return ENVIRONMENT.readEnv("OPENSEARCH_ENDPOINT");
    }

}
