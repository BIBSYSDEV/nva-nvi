package no.sikt.nva.nvi.evaluator.calculator;

import static java.util.Objects.isNull;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_POINTER_IDENTITY_ID;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_POINTER_IDENTITY_VERIFICATION_STATUS;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_AFFILIATIONS;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_CHAPTER_PUBLISHER_LEVEL;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_CHAPTER_SERIES_LEVEL;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_CONTRIBUTOR;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_DAY;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_ID;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_INSTANCE_TYPE;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_MONTH;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_PUBLICATION_CONTEXT_LEVEL;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_PUBLICATION_DATE;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_PUBLISHER_LEVEL;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_ROLE_TYPE;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_SERIES_LEVEL;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_YEAR;
import static no.sikt.nva.nvi.common.utils.JsonUtils.extractJsonNodeTextValue;
import static no.sikt.nva.nvi.common.utils.JsonUtils.streamNode;
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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import no.sikt.nva.nvi.evaluator.model.CandidateType;
import no.sikt.nva.nvi.evaluator.model.CustomerResponse;
import no.sikt.nva.nvi.evaluator.model.NonNviCandidate;
import no.sikt.nva.nvi.evaluator.model.NviCandidate;
import no.sikt.nva.nvi.evaluator.model.NviCandidate.CandidateDetails;
import no.sikt.nva.nvi.evaluator.model.NviCandidate.CandidateDetails.Creator;
import no.sikt.nva.nvi.evaluator.model.NviCandidate.CandidateDetails.PublicationDate;
import no.sikt.nva.nvi.evaluator.model.Organization;
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

public class CandidateCalculator {

    private static final String CREATOR = "Creator";
    private static final String CONTENT_TYPE = "application/json";
    private static final String COULD_NOT_FETCH_CUSTOMER_MESSAGE = "Could not fetch customer for: ";
    private static final String CUSTOMER = "customer";
    private static final String CRISTIN_ID = "cristinId";
    private static final String VERIFIED = "Verified";
    private static final String COULD_NOT_FETCH_CRISTIN_ORG_MESSAGE = "Could not fetch Cristin organization for: ";
    private static final String ERROR_COULD_NOT_FETCH_CRISTIN_ORG = COULD_NOT_FETCH_CRISTIN_ORG_MESSAGE + "{}. "
                                                                    + "Response code: {}";
    private static final Logger LOGGER = LoggerFactory.getLogger(CandidateCalculator.class);
    private static final String ACADEMIC_MONOGRAPH = "AcademicMonograph";
    private static final String ACADEMIC_CHAPTER = "AcademicChapter";
    private static final String ACADEMIC_ARTICLE = "AcademicArticle";
    private static final String ACADEMIC_LITERATURE_REVIEW = "AcademicLiteratureReview";
    private static final String NVI_YEAR = "2023";
    private static final String NVI_YEAR_REPLACE_STRING = "__NVI_YEAR__";
    private static final String NVI_SPARQL = IoUtils.stringFromResources(Path.of("sparql/nvi.sparql"))
                                                 .replace(NVI_YEAR_REPLACE_STRING, NVI_YEAR);
    private static final String API_HOST = new Environment().readEnv("API_HOST");
    private final AuthorizedBackendUriRetriever uriRetriever;

    public CandidateCalculator(AuthorizedBackendUriRetriever uriRetriever) {
        this.uriRetriever = uriRetriever;
    }

    public CandidateType calculateNviType(JsonNode body) {
        var model = createModel(body);

        if (!isNviCandidate(model)) {
            return createNonCandidateResponse(body);
        }

        var verifiedCreatorsWithNviInstitutions = getVerifiedCreatorsWithNviInstitutions(body);

        return verifiedCreatorsWithNviInstitutions.isEmpty()
                   ? createNonCandidateResponse(body)
                   : createCandidateResponse(verifiedCreatorsWithNviInstitutions, body);
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

    private static boolean doesNotHaveNviInstitutions(Entry<URI, List<URI>> entry) {
        return !entry.getValue().isEmpty();
    }

    private static NonNviCandidate createNonCandidateResponse(JsonNode publication) {
        return new NonNviCandidate.Builder().withPublicationId(extractId(publication)).build();
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

    private static URI createCustomerApiUri(String institutionId) {
        var getCustomerEndpoint = UriWrapper.fromHost(API_HOST).addChild(CUSTOMER).addChild(CRISTIN_ID).getUri();
        return URI.create(getCustomerEndpoint + "/" + URLEncoder.encode(institutionId, StandardCharsets.UTF_8));
    }

    private static URI extractContributorId(JsonNode creatorNode) {
        return URI.create(extractJsonNodeTextValue(creatorNode, JSON_POINTER_IDENTITY_ID));
    }

    private static URI extractId(JsonNode jsonNode) {
        return URI.create(extractJsonNodeTextValue(jsonNode, JSON_PTR_ID));
    }

    private static boolean isVerified(JsonNode contributorNode) {
        return VERIFIED.equals(extractJsonNodeTextValue(contributorNode, JSON_POINTER_IDENTITY_VERIFICATION_STATUS));
    }

    @JacocoGenerated
    private static void logInvalidJsonLdInput(Exception exception) {
        LOGGER.warn("Invalid JSON LD input encountered: ", exception);
    }

    private static CustomerResponse toCustomer(String responseBody) {
        return attempt(() -> dtoObjectMapper.readValue(responseBody, CustomerResponse.class)).orElseThrow();
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

    private static boolean mapToNviInstitutionValue(HttpResponse<String> response) {
        return attempt(response::body).map(CandidateCalculator::toCustomer)
                   .map(CustomerResponse::nviInstitution)
                   .orElse(failure -> false);
    }

    private static String extractInstanceType(JsonNode publication) {
        return extractJsonNodeTextValue(publication, JSON_PTR_INSTANCE_TYPE);
    }

    private static PublicationDate extractPublicationDate(JsonNode publication) {
        return mapToPublicationDate(publication.at(JSON_PTR_PUBLICATION_DATE));
    }

    private static PublicationDate mapToPublicationDate(JsonNode publicationDateNode) {
        var year = publicationDateNode.at(JSON_PTR_YEAR);
        var month = publicationDateNode.at(JSON_PTR_MONTH);
        var day = publicationDateNode.at(JSON_PTR_DAY);

        return Optional.of(new PublicationDate(day.textValue(), month.textValue(), year.textValue()))
                   .orElse(new PublicationDate(null, null, year.textValue()));
    }

    private static List<Creator> mapToCreatorList(Map<URI, List<URI>> verifiedCreatorsWithNviInstitutions) {
        return verifiedCreatorsWithNviInstitutions.entrySet()
                   .stream()
                   .map(entry -> new Creator(entry.getKey(), entry.getValue()))
                   .toList();
    }

    private static String extractLevel(String instanceType, JsonNode jsonNode) {
        return switch (instanceType) {
            case ACADEMIC_ARTICLE, ACADEMIC_LITERATURE_REVIEW ->
                extractJsonNodeTextValue(jsonNode, JSON_PTR_PUBLICATION_CONTEXT_LEVEL);
            case ACADEMIC_MONOGRAPH -> extractAcademicMonographLevel(jsonNode);
            case ACADEMIC_CHAPTER -> extractAcademicChapterLevel(jsonNode);
            default -> throw new IllegalArgumentException();
        };
    }

    private static String extractAcademicChapterLevel(JsonNode jsonNode) {
        var seriesLevel = extractJsonNodeTextValue(jsonNode, JSON_PTR_CHAPTER_SERIES_LEVEL);
        var publisherLevel = extractJsonNodeTextValue(jsonNode, JSON_PTR_CHAPTER_PUBLISHER_LEVEL);
        return Objects.nonNull(seriesLevel) ? seriesLevel : publisherLevel;
    }

    private static String extractAcademicMonographLevel(JsonNode jsonNode) {
        var seriesLevel = extractJsonNodeTextValue(jsonNode, JSON_PTR_SERIES_LEVEL);
        var publisherLevel = extractJsonNodeTextValue(jsonNode, JSON_PTR_PUBLISHER_LEVEL);
        return Objects.nonNull(seriesLevel) ? seriesLevel : publisherLevel;
    }

    private static Stream<JsonNode> getJsonNodeStream(JsonNode jsonNode, String jsonPtr) {
        return StreamSupport.stream(jsonNode.at(jsonPtr).spliterator(), false);
    }

    private static boolean isCreator(JsonNode contributorNode) {
        return CREATOR.equals(extractJsonNodeTextValue(contributorNode, JSON_PTR_ROLE_TYPE));
    }

    private Map<URI, List<URI>> getVerifiedCreatorsWithNviInstitutions(JsonNode body) {
        return getJsonNodeStream(body, JSON_PTR_CONTRIBUTOR)
                   .filter(CandidateCalculator::isVerified)
                   .filter(CandidateCalculator::isCreator)
                   .collect(Collectors.toMap(
                       CandidateCalculator::extractContributorId,
                       this::getTopLevelNviInstitutions))
                   .entrySet()
                   .stream()
                   .filter(CandidateCalculator::doesNotHaveNviInstitutions)
                   .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }

    private List<URI> getTopLevelNviInstitutions(JsonNode creator) {
        return streamNode(creator.at(JSON_PTR_AFFILIATIONS))
                   .map(CandidateCalculator::extractId)
                   .map(this::fetchOrganization)
                   .map(Organization::getTopLevelOrg)
                   .map(Organization::id)
                   .filter(this::isNviInstitution)
                   .toList();
    }

    private CandidateType createCandidateResponse(Map<URI, List<URI>> verifiedCreatorsWithNviInstitutions,
                                                  JsonNode body) {
        var instanceType = extractInstanceType(body);
        return new NviCandidate(new CandidateDetails(URI.create(extractJsonNodeTextValue(body, JSON_PTR_ID)),
                                                     instanceType,
                                                     extractLevel(instanceType, body),
                                                     extractPublicationDate(body),
                                                     mapToCreatorList(verifiedCreatorsWithNviInstitutions)));
    }

    private Organization fetchOrganization(URI organizationId) {
        var response = getResponse(organizationId);
        if (isHttpOk(response)) {
            return toCristinOrganization(response.body());
        } else {
            LOGGER.error(ERROR_COULD_NOT_FETCH_CRISTIN_ORG, organizationId, response.statusCode());
            throw new RuntimeException(COULD_NOT_FETCH_CRISTIN_ORG_MESSAGE + organizationId);
        }
    }

    private boolean isNviInstitution(URI institutionId) {
        var response = getResponse(createCustomerApiUri(institutionId.toString()));
        if (isSuccessOrNotFound(response)) {
            return mapToNviInstitutionValue(response);
        }
        throw new RuntimeException(COULD_NOT_FETCH_CUSTOMER_MESSAGE + institutionId);
    }

    private Organization toCristinOrganization(String response) {
        return attempt(() -> dtoObjectMapper.readValue(response, Organization.class)).orElseThrow();
    }

    private HttpResponse<String> getResponse(URI uri) {
        return Optional.ofNullable(uriRetriever.fetchResponse(uri, CONTENT_TYPE))
                   .stream()
                   .filter(Optional::isPresent)
                   .map(Optional::get)
                   .findAny()
                   .orElseThrow();
    }
}
