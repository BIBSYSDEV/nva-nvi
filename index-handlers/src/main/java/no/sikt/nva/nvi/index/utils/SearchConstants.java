package no.sikt.nva.nvi.index.utils;

import java.util.Map;
import nva.commons.core.Environment;
import org.opensearch.client.opensearch._types.mapping.KeywordProperty;
import org.opensearch.client.opensearch._types.mapping.NestedProperty;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;

public final class SearchConstants {

    public static final String ID = "id";
    public static final String ASSIGNEE = "assignee";
    public static final String NUMBER_OF_APPROVALS = "numberOfApprovals";
    public static final String APPROVALS = "approvals";
    public static final String APPROVAL_STATUS = "approvalStatus";
    public static final String NVI_CANDIDATES_INDEX = "nvi-candidates";
    public static final String SEARCH_INFRASTRUCTURE_CREDENTIALS = "SearchInfrastructureCredentials";
    public static final Environment ENVIRONMENT = new Environment();
    public static final String SEARCH_INFRASTRUCTURE_API_HOST = readSearchInfrastructureApiHost();
    public static final String SEARCH_INFRASTRUCTURE_AUTH_URI = readSearchInfrastructureAuthUri();

    private SearchConstants() {

    }

    public static TypeMapping mappings() {
        return new TypeMapping.Builder()
                   .properties(mappingProperties())
                   .build();
    }

    private static Map<String, Property> mappingProperties() {
        return Map.of(APPROVALS, new Property.Builder()
                                    .nested(new NestedProperty.Builder()
                                                .includeInParent(true)
                                                .properties(affiliationsProperties()).build())
                                    .build());
    }

    private static String readSearchInfrastructureApiHost() {
        return ENVIRONMENT.readEnv("SEARCH_INFRASTRUCTURE_API_HOST");
    }

    private static String readSearchInfrastructureAuthUri() {
        return ENVIRONMENT.readEnv("SEARCH_INFRASTRUCTURE_AUTH_URI");
    }

    private static Map<String, Property> affiliationsProperties() {
        return Map.of(ID, keywordProperty(),
                      ASSIGNEE, keywordProperty(),
                      APPROVAL_STATUS, keywordProperty());
    }

    private static Property keywordProperty() {
        return new Property.Builder().keyword(new KeywordProperty.Builder().build()).build();
    }
}
