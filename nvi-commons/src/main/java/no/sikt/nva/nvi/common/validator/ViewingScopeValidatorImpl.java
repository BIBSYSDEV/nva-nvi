package no.sikt.nva.nvi.common.validator;

import static no.sikt.nva.nvi.common.utils.GraphUtils.HAS_PART_PROPERTY;
import static no.sikt.nva.nvi.common.utils.GraphUtils.createModel;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.unit.nva.clients.IdentityServiceClient;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.RDFNode;

public class ViewingScopeValidatorImpl implements ViewingScopeValidator {

    private final IdentityServiceClient identityServiceClient;
    private final OrganizationRetriever organizationRetriever;

    public ViewingScopeValidatorImpl(IdentityServiceClient identityServiceClient,
                                     OrganizationRetriever organizationRetriever) {

        this.identityServiceClient = identityServiceClient;
        this.organizationRetriever = organizationRetriever;
    }

    @Override
    public boolean userIsAllowedToAccessAll(String userName, List<URI> organizations) {
        var viewingScope = fetchViewingScope(userName);
        return getAllowedUnits(viewingScope).containsAll(organizations);
    }

    @Override
    public boolean userIsAllowedToAccessOneOf(String userName, List<URI> organizations) {
        var viewingScope = fetchViewingScope(userName);
        var allowed = getAllowedUnits(viewingScope);
        return organizations.stream().anyMatch(allowed::contains);
    }

    private static Stream<String> concat(URI topLevelOrg, Stream<String> stringStream) {
        return Stream.concat(stringStream, Stream.of(topLevelOrg.toString()));
    }

    private static Stream<String> toStreamOfRfdNodes(NodeIterator nodeIterator) {
        return nodeIterator.toList().stream().map(RDFNode::toString);
    }

    private static NodeIterator getObjectsOfPropertyHasPart(Model model) {
        return model.listObjectsOfProperty(model.createProperty(HAS_PART_PROPERTY));
    }

    private static Stream<URI> toUris(Stream<String> stream) {
        return stream.map(URI::create);
    }

    private Set<URI> getAllowedUnits(Set<URI> viewingScope) {
        var allowed = new HashSet<URI>();
        allowed.addAll(viewingScope);
        allowed.addAll(getSubUnits(viewingScope));
        return allowed;
    }

    private Set<URI> fetchViewingScope(String userName) {
        var user = attempt(() -> identityServiceClient.getUser(userName)).orElseThrow();
        return new HashSet<>(user.viewingScope().includedUnits());
    }

    private Set<URI> getSubUnits(Set<URI> viewingScope) {
        return viewingScope.stream()
                   .flatMap(unit -> getSubUnits(unit).stream())
                   .collect(Collectors.toSet());
    }

    private Set<URI> getSubUnits(URI unit) {
        return attempt(() -> organizationRetriever.fetchOrganization(unit))
                   .map(org -> createModel(dtoObjectMapper.readTree(org.toJsonString())))
                   .map(ViewingScopeValidatorImpl::getObjectsOfPropertyHasPart)
                   .map(ViewingScopeValidatorImpl::toStreamOfRfdNodes)
                   .map(node -> concat(unit, node))
                   .map(ViewingScopeValidatorImpl::toUris)
                   .orElseThrow()
                   .collect(Collectors.toSet());
    }
}
