package no.sikt.nva.nvi.evaluator.calculator;

import static java.util.Objects.isNull;
import static nva.commons.core.attempt.Try.attempt;
import static nva.commons.core.ioutils.IoUtils.stringToStream;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import no.sikt.nva.nvi.evaluator.EvaluateNviCandidateHandler;
import no.sikt.nva.nvi.evaluator.exceptions.NotACandidateException;
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

public class NviCalculator {

    private static final String AFFILIATION_SPARQL =
        IoUtils.stringFromResources(Path.of("sparql/affiliation.sparql"));
    //TODO to be configured somehow
    private static final String NVI_YEAR = "2023";
    private static final String ID_JSON_PATH = "/id";
    private static final Logger LOGGER = LoggerFactory.getLogger(NviCalculator.class);
    private static final String NVI_YEAR_REPLACE_STRING = "__NVI_YEAR__";
    private static final String NVI_CANDIDATE =
        IoUtils.stringFromResources(Path.of("sparql/nvi.sparql"))
            .replace(NVI_YEAR_REPLACE_STRING, NVI_YEAR);

    private static ArrayList<String> fetchAffiliationUris(Model model) {
        var affiliationUris = new ArrayList<String>();
        try (var exec = QueryExecutionFactory.create(AFFILIATION_SPARQL, model)) {
            var resultSet = exec.execSelect();
            while (resultSet.hasNext()) {
                affiliationUris.add(
                    resultSet.next().getResource("affiliation").getURI());
            }
        }
        return affiliationUris;
    }

    @JacocoGenerated
    private static void logInvalidJsonLdInput(Exception exception) {
        LOGGER.warn("Invalid JSON LD input encountered: ", exception);
    }

    public static CandidateResponse calculateCandidate(JsonNode body) {
        var model = buildModel(body);
        var affiliationUris = fetchAffiliationUris(model);
        if (affiliationUris.isEmpty()) {
            throw new NotACandidateException();
        }
        //TODO ADD Check of Affiliations NVI affinity
        var nviAffiliationsForApproval = new ArrayList<>(affiliationUris);

        var nviCandidate =
            attempt(() -> QueryExecutionFactory.create(NVI_CANDIDATE, model))
                .map(QueryExecution::execAsk)
                .orElseThrow();

        if (!nviCandidate) {
            throw new NotACandidateException();
        }

        var resourceUri = URI.create(body.at(ID_JSON_PATH).asText());
        return CandidateResponse.builder()
                           .resourceUri(resourceUri)
                           .approvalAffiliations(
                               nviAffiliationsForApproval.stream().map(URI::create).toList())
                           .build();
    }

    private static Model buildModel(JsonNode body) {
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
