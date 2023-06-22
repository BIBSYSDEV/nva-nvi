package no.sikt.nva.nvi.common;

import java.util.Set;
import no.sikt.nva.nvi.common.model.IndexDocument;

public interface IndexClient {

    Set<IndexDocument> listAllDocuments(String indexName);
}
