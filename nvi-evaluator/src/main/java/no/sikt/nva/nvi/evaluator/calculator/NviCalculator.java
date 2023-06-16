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
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NviCalculator {

    private static final Logger LOGGER = LoggerFactory.getLogger(NviCalculator.class);
    private static final Pair<Boolean, CandidateResponse> NON_CANDIDATE = Pair.of(false, null);
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

    public static Pair<Boolean, CandidateResponse> calculateCandidate(JsonNode body) {
        var model = createModel(body);
        var affiliationUris = fetchResourceUris(model, AFFILIATION_SPARQL, AFFILIATION);
        if (affiliationUris.isEmpty()) {
            return NON_CANDIDATE;
        }
        if (!isNviCandidate(model)) {
            return NON_CANDIDATE;
        }
        //TODO ADD Check if affiliations are nviInstitutes
        var nviAffiliationsForApproval = new ArrayList<>(affiliationUris);
        var publicationId = selectPublicationId(model);
        return createCandidateResponse(nviAffiliationsForApproval, publicationId);
    }

    private static Pair<Boolean, CandidateResponse> createCandidateResponse(List<String> affiliationIds,
                                                                            URI publicationId) {
        return Pair.of(true, CandidateResponse.builder()
                                         .withPublicationId(publicationId)
                                         .withApprovalAffiliations(
                                             affiliationIds.stream().map(URI::create).toList())
                                         .build());
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

    @JacocoGenerated
    private static void logInvalidJsonLdInput(Exception exception) {
        LOGGER.warn("Invalid JSON LD input encountered: ", exception);
    }

    private static Model createModel(JsonNode body) {
        var model = ModelFactory.createDefaultModel();
        loadDataIntoModel(model, stringToStream(body.toString()));
        return model;
    }

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
}
