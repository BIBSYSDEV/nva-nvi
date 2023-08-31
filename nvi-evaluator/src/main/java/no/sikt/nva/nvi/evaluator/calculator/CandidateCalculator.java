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
import no.sikt.nva.nvi.common.model.events.CandidateType;
import no.sikt.nva.nvi.common.model.events.NonNviCandidate;
import no.sikt.nva.nvi.common.model.events.NviCandidate;
import no.sikt.nva.nvi.common.model.events.NviCandidate.CandidateDetails;
import no.sikt.nva.nvi.common.model.events.NviCandidate.CandidateDetails.Creator;
import no.sikt.nva.nvi.common.model.events.NviCandidate.CandidateDetails.PublicationDate;
import no.sikt.nva.nvi.common.model.events.Publication;
import no.sikt.nva.nvi.common.model.events.Publication.EntityDescription.Contributor;
import no.sikt.nva.nvi.common.model.events.Publication.EntityDescription.Contributor.Affiliation;
import no.sikt.nva.nvi.evaluator.model.CustomerResponse;
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

    private static final String CONTENT_TYPE = "application/json";
    private static final String COULD_NOT_FETCH_CUSTOMER_MESSAGE = "Could not fetch customer for: ";
    private static final String CUSTOMER = "customer";
    private static final String CRISTIN_ID = "cristinId";
    private static final String VERIFIED = "Verified";

    private static final String COULD_NOT_FETCH_CRISTIN_ORG_MESSAGE = "Could not fetch Cristin organization for: ";
    private static final String ERROR_COULD_NOT_FETCH_CRISTIN_ORG = COULD_NOT_FETCH_CRISTIN_ORG_MESSAGE + "{}. "
                                                                   + "Response code: {}";
    private static final Logger LOGGER = LoggerFactory.getLogger(CandidateCalculator.class);
    //TODO to be configured somehow
    private static final String NVI_YEAR = "2023";
    private static final String NVI_YEAR_REPLACE_STRING = "__NVI_YEAR__";
    private static final String NVI_SPARQL = IoUtils.stringFromResources(Path.of("sparql/nvi.sparql"))
                                                 .replace(NVI_YEAR_REPLACE_STRING, NVI_YEAR);
    private static final String API_HOST = new Environment().readEnv("API_HOST");
    private final AuthorizedBackendUriRetriever uriRetriever;

    public CandidateCalculator(AuthorizedBackendUriRetriever uriRetriever) {
        this.uriRetriever = uriRetriever;
    }

    public CandidateType calculateNviType(JsonNode body) throws JsonProcessingException {
        var model = createModel(body);
        var publication = dtoObjectMapper.readValue(body.toString(), Publication.class);

        if (!isNviCandidate(model)) {
            return createNonCandidateResponse(publication);
        }
        var verifiedCreators = extractVerifiedCreator(publication);

        return doesNotHaveNviInstitutions(verifiedCreators)
                   ? createNonCandidateResponse(publication)
                   : createCandidateResponse(verifiedCreators, publication);
    }

    private static boolean doesNotHaveNviInstitutions(List<Creator> verifiedCreators) {
        return verifiedCreators.stream().map(Creator::nviInstitutions).flatMap(List::stream).toList().isEmpty();
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

    private static URI createCustomerApiUri(String affiliation) {
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

    private static boolean mapToNviInstitutionValue(HttpResponse<String> response) {
        return attempt(response::body).map(CandidateCalculator::toCustomer)
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
        var publicationDate = publication.entityDescription().publicationDate();
        return new PublicationDate(publicationDate.day(), publicationDate.month(), publicationDate.year());
    }

    private List<Creator> extractVerifiedCreator(Publication publication) {
        return publication.entityDescription()
                   .contributors()
                   .stream()
                   .filter(CandidateCalculator::isVerified)
                   .map(this::filterInstitutionsToKeepNvaCustomers)
                   .map(CandidateCalculator::toCreator)
                   .toList();
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

    private Contributor filterInstitutionsToKeepNvaCustomers(Contributor contributor) {
        return new Contributor(contributor.identity(), getTopLevelOrgNviInstitutions(contributor));
    }

    private List<Affiliation> getTopLevelOrgNviInstitutions(Contributor contributor) {
        return contributor.affiliations()
                   .stream()
                   .map(Affiliation::id)
                   .map(this::fetchOrganization)
                   .map(Organization::getTopLevelOrg)
                   .map(Organization::id)
                   .map(URI::toString)
                   .filter(this::isNviInstitution)
                   .map(URI::create)
                   .map(Affiliation::new)
                   .toList();
    }

    private Organization toCristinOrganization(String response) {
        return attempt(() -> dtoObjectMapper.readValue(response, Organization.class)).orElseThrow();
    }

    private boolean isNviInstitution(String institutionId) {
        var response = getResponse(createCustomerApiUri(institutionId));
        if (isSuccessOrNotFound(response)) {
            return mapToNviInstitutionValue(response);
        }
        throw new RuntimeException(COULD_NOT_FETCH_CUSTOMER_MESSAGE + institutionId);
    }

    private HttpResponse<String> getResponse(URI uri) {
        return Optional.ofNullable(uriRetriever.fetchResponse(uri, CONTENT_TYPE))
                   .stream()
                   .filter(Optional::isPresent)
                   .map(Optional::get)
                   .findAny()
                   .orElseThrow();
    }

    private CandidateType createCandidateResponse(List<Creator> verifiedCreators, Publication publication) {
        return new NviCandidate(constructCandidateDetails(verifiedCreators, publication));
    }

    private CandidateDetails constructCandidateDetails(List<Creator> verifiedCreators, Publication publication) {
        return new CandidateDetails(publication.id(), extractInstanceType(publication), extractLevel(publication),
                                    extractPublicationDate(publication), verifiedCreators);
    }
}
