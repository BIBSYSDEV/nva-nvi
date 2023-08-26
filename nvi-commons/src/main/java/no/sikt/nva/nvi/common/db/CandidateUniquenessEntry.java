package no.sikt.nva.nvi.common.db;

import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@DynamoDbBean()
public class CandidateUniquenessEntry extends UniquenessEntry {

    private static final String TYPE = "CandidateUniquenessEntry";
    public CandidateUniquenessEntry(String identifier) {
        super(identifier);
    }

    @JacocoGenerated
    public CandidateUniquenessEntry() {
        super();
    }

    @Override
    public String getType() {
        return TYPE;
    }

}
