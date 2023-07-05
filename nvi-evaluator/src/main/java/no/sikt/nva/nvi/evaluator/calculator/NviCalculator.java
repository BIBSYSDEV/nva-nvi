package no.sikt.nva.nvi.evaluator.calculator;

import static java.util.Objects.isNull;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import static nva.commons.core.ioutils.IoUtils.stringToStream;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.evaluator.model.CandidateResponse;
import no.sikt.nva.nvi.evaluator.model.CustomerResponse;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.core.paths.UriWrapper;
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

    public static final String CONTENT_TYPE = "application/json";
    public static final String COULD_NOT_FETCH_AFFILIATION_MESSAGE = "Could not fetch affiliation for: ";
    public static final String CUSTOMER = "customer";
    public static final String CRISTIN_ID = "cristinId";
    public static final String AFFILIATION_FETCHED_SUCCESSFULLY_MESSAGE = "Affiliation fetched successfully {}";
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
    private static final String API_HOST = new Environment().readEnv("API_HOST");
    private AuthorizedBackendUriRetriever uriRetriever;

    public NviCalculator(AuthorizedBackendUriRetriever uriRetriever) {
        this.uriRetriever = uriRetriever;
    }

    private NviCalculator() {
    }

    public CandidateType calculateNvi(JsonNode body) {
        var model = createModel(body);

        if (!isNviCandidate(model)) {
            return new NonNviCandidate();
        }

        var affiliationUris = fetchResourceUris(model, AFFILIATION_SPARQL, AFFILIATION);
        var nviAffiliationsForApproval = fetchNviInstitutions(affiliationUris);
        var publicationId = selectPublicationId(model);

        return nviAffiliationsForApproval.isEmpty()
                   ? new NonNviCandidate()
                   : createCandidateResponse(nviAffiliationsForApproval, publicationId);
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

    private static CustomerResponse toCustomer(String responseBody) {
        return attempt(() -> dtoObjectMapper.readValue(responseBody, CustomerResponse.class)).orElseThrow();
    }

    private static URI createUri(String affiliation) {
        return UriWrapper.fromHost(API_HOST)
                   .addChild(CUSTOMER)
                   .addChild(CRISTIN_ID)
                   .addChild(URLEncoder.encode(affiliation, StandardCharsets.UTF_8))
                   .getUri();
    }

    private static boolean isHttpOk(HttpResponse<String> response) {
        return response.statusCode() == HttpURLConnection.HTTP_OK;
    }

    private static boolean isNotFound(HttpResponse<String> response) {
        return response.statusCode() == HttpURLConnection.HTTP_NOT_FOUND;
    }

    private static boolean isSuccessOrNotFound(HttpResponse<String> response) {
        return isHttpOk(response) || isNotFound(response);
    }

    private static boolean getNviValue(HttpResponse<String> response) {
        return Optional.of(response.body())
                   .map(NviCalculator::toCustomer)
                   .map(CustomerResponse::nviInstitution)
                   .orElse(false);
    }

    private List<String> fetchNviInstitutions(List<String> affiliationUris) {
        return affiliationUris.stream()
                   .filter(this::isNviInstitution)
                   .collect(Collectors.toList());
    }

    private boolean isNviInstitution(String affiliation) {
        var response = getResponse(affiliation);
        if (isSuccessOrNotFound(response)) {
            LOGGER.info(AFFILIATION_FETCHED_SUCCESSFULLY_MESSAGE, response.statusCode());
            return getNviValue(response);
        }
        throw new RuntimeException(COULD_NOT_FETCH_AFFILIATION_MESSAGE + affiliation);
    }

    private HttpResponse<String> getResponse(String affiliation) {
        return Optional.ofNullable(uriRetriever.fetchResponse(createUri(affiliation), CONTENT_TYPE))
                   .stream()
                   .filter(Optional::isPresent)
                   .map(Optional::get)
                   .findAny()
                   .orElseThrow();
    }

    private CandidateType createCandidateResponse(List<String> affiliationIds,
                                                  URI publicationId) {
        return new NviCandidate(
            new CandidateResponse(
                publicationId,
                affiliationIds.stream().map(URI::create).toList()));
    }
}
