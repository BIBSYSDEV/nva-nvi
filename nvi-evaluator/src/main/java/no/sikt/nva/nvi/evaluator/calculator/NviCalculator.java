package no.sikt.nva.nvi.evaluator.calculator;

import static java.util.Objects.isNull;
import static nva.commons.core.attempt.Try.attempt;
import static nva.commons.core.ioutils.IoUtils.stringToStream;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import no.sikt.nva.nvi.evaluator.model.CandidateResponse;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.ioutils.IoUtils;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NviCalculator {

    private static final Logger LOGGER = LoggerFactory.getLogger(NviCalculator.class);
    private static final String AFFILIATION_SPARQL =
        IoUtils.stringFromResources(Path.of("sparql/affiliation.sparql"));
    private static final String ID_SPARQL =
        IoUtils.stringFromResources(Path.of("sparql/id.sparql"));
    //TODO to be configured somehow
    private static final String NVI_YEAR = "2023";
    private static final String NVI_YEAR_REPLACE_STRING = "__NVI_YEAR__";
    private static final String NVI_SPARQL =
        IoUtils.stringFromResources(Path.of("sparql/nvi.sparql"))
            .replace(NVI_YEAR_REPLACE_STRING, NVI_YEAR);
    private static final String ID = "id";
    private static final String AFFILIATION = "affiliation";

    private NviCalculator() {
    }

    public static CandidateType calculateNvi(JsonNode body) {
        var model = createModel(body);
        var affiliationUris = fetchResourceUris(model, AFFILIATION_SPARQL, AFFILIATION);
        if (affiliationUris.isEmpty()) {
            return new NonNviCandidate();
        }
        if (!isNviCandidate(model)) {
            return new NonNviCandidate();
        }
        //TODO ADD Check if affiliations are nviInstitutes
        var nviAffiliationsForApproval = new ArrayList<>(affiliationUris);
        var publicationId = selectPublicationId(model);
        return createCandidateResponse(nviAffiliationsForApproval, publicationId);
    }

    private static CandidateType createCandidateResponse(List<String> affiliationIds,
                                                         URI publicationId) {
        return new NviCandidate(
            new CandidateResponse(
                publicationId,
                affiliationIds.stream().map(URI::create).toList()));
    }

    private static boolean isNviCandidate(Model model) {
        return attempt(() -> QueryExecutionFactory.create(NVI_SPARQL, model))
                   .map(QueryExecution::execAsk)
                   .map(Boolean::booleanValue)
                   .orElseThrow();
    }

    private static URI selectPublicationId(Model model) {
        return URI.create(fetchResourceUris(model, ID_SPARQL, ID).stream()
                              .findFirst()
                              .orElseThrow());
    }

    private static List<String> fetchResourceUris(Model model, String sparqlQuery, String varName) {
        var resourceUris = new ArrayList<String>();
        try (var exec = QueryExecutionFactory.create(sparqlQuery, model)) {
            var resultSet = exec.execSelect();
            while (resultSet.hasNext()) {
                resourceUris.add(
                    resultSet.next().getResource(varName).getURI());
            }
        }
        return resourceUris;
    }

    private static Model createModel(JsonNode body) {
        var model = ModelFactory.createDefaultModel();
        loadDataIntoModel(model, stringToStream(body.toString()));
        return model;
    }

    @JacocoGenerated
    private static void loadDataIntoModel(Model model, InputStream inputStream) {
        if (isNull(inputStream)) {
            return;
        }
        try {
            RDFDataMgr.read(model, inputStream, Lang.JSONLD);
        } catch (RiotException e) {
            logInvalidJsonLdInput(e);
        }
    }

    @JacocoGenerated
    private static void logInvalidJsonLdInput(Exception exception) {
        LOGGER.warn("Invalid JSON LD input encountered: ", exception);
    }
}
