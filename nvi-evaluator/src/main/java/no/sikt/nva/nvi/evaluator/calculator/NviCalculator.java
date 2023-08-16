package no.sikt.nva.nvi.evaluator.calculator;

import static java.util.Objects.isNull;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import static nva.commons.core.ioutils.IoUtils.stringToStream;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.evaluator.calculator.NviCandidate.CandidateDetails;
import no.sikt.nva.nvi.evaluator.calculator.NviCandidate.CandidateDetails.Creator;
import no.sikt.nva.nvi.evaluator.calculator.model.Publication;
import no.sikt.nva.nvi.evaluator.calculator.model.Publication.EntityDescription.Contributor;
import no.sikt.nva.nvi.evaluator.calculator.model.Publication.EntityDescription.Contributor.Affiliation;
import no.sikt.nva.nvi.evaluator.calculator.model.Publication.EntityDescription.PublicationDate;
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
    public static final String AFFILIATION_FETCHED_SUCCESSFULLY_MESSAGE =
        "Affiliation fetched successfully with " + "status {}";
    public static final String VERIFIED = "Verified";
    private static final Logger LOGGER = LoggerFactory.getLogger(NviCalculator.class);
    //TODO to be configured somehow
    private static final String NVI_YEAR = "2023";
    private static final String NVI_YEAR_REPLACE_STRING = "__NVI_YEAR__";
    private static final String NVI_SPARQL = IoUtils.stringFromResources(Path.of("sparql/nvi.sparql"))
                                                    .replace(NVI_YEAR_REPLACE_STRING, NVI_YEAR);
    private static final String API_HOST = new Environment().readEnv("API_HOST");
    private AuthorizedBackendUriRetriever uriRetriever;

    public NviCalculator(AuthorizedBackendUriRetriever uriRetriever) {
        this.uriRetriever = uriRetriever;
    }

    private NviCalculator() {
    }

    public CandidateType calculateNvi(JsonNode body) throws JsonProcessingException {
        var model = createModel(body);
        var publication = dtoObjectMapper.readValue(body.toString(), Publication.class);

        if (!isNviCandidate(model)) {
            return createNonCandidateResponse(publication);
        }
        var institutions = fetchNviInstitutions(extractInstitutions(publication));

        return institutions.isEmpty()
                   ? createNonCandidateResponse(publication)
                   : createCandidateResponse(institutions, publication);
    }

    private static NonNviCandidate createNonCandidateResponse(Publication publication) {
        return new NonNviCandidate.Builder().withPublicationId(publication.id()).build();
    }

    private static boolean isNviCandidate(Model model) {
        return attempt(() -> QueryExecutionFactory.create(NVI_SPARQL, model)).map(QueryExecution::execAsk)
                                                                             .map(Boolean::booleanValue)
                                                                             .orElseThrow();
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
        var getCustomerEndpoint = UriWrapper.fromHost(API_HOST).addChild(CUSTOMER).addChild(CRISTIN_ID).getUri();
        return URI.create(getCustomerEndpoint + "/" + URLEncoder.encode(affiliation, StandardCharsets.UTF_8));
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
        return attempt(response::body).map(NviCalculator::toCustomer)
                                      .map(CustomerResponse::nviInstitution)
                                      .orElse(failure -> false);
    }

    private static String extractLevel(Publication publication) {
        return publication.entityDescription().reference().publicationContext().level();
    }

    private static String extractInstanceType(Publication publication) {
        return publication.entityDescription().reference().publicationInstance().type();
    }

    private static Creator toCreator(Contributor contributor) {
        return new Creator(contributor.identity().id(), contributor.affiliations()
                                                                   .stream()
                                                                   .map(Affiliation::id)
                                                                   .toList());
    }

    private static boolean isVerified(Contributor contributor) {
        return VERIFIED.equals(contributor.identity().verificationStatus());
    }

    private static PublicationDate extractPublicationDate(Publication publication) {
        return publication.entityDescription().publicationDate();
    }

    private List<String> extractInstitutions(Publication publication) {
        return publication.entityDescription()
                          .contributors()
                          .stream()
                          .map(Contributor::affiliations)
                          .flatMap(List::stream)
                          .map(Affiliation::id)
                          .map(URI::toString)
                          .toList();
    }

    private List<URI> fetchNviInstitutions(List<String> affiliationUris) {
        return affiliationUris.stream().filter(this::isNviInstitution).map(URI::create).collect(Collectors.toList());
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

    private CandidateType createCandidateResponse(List<URI> institutions, Publication publication) {
        return new NviCandidate(institutions, constructCandidateDetails(publication));
    }

    private CandidateDetails constructCandidateDetails(Publication publication) {
        return new CandidateDetails(publication.id(), extractInstanceType(publication), extractLevel(publication),
                                    extractPublicationDate(publication), extractVerifiedCreators(publication));
    }

    private List<Creator> extractVerifiedCreators(Publication publication) {
        return publication.entityDescription()
                          .contributors()
                          .stream()
                          .filter(NviCalculator::isVerified)
                          .map(NviCalculator::toCreator)
                          .toList();
    }
}
