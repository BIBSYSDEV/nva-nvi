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
import no.sikt.nva.nvi.common.client.UserRetriever;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.RDFNode;

public class ViewingScopeValidator {

    private final UserRetriever userRetriever;
    private final OrganizationRetriever organizationRetriever;

    public ViewingScopeValidator(UserRetriever userRetriever,
                                 OrganizationRetriever organizationRetriever) {

        this.userRetriever = userRetriever;
        this.organizationRetriever = organizationRetriever;
    }

    public boolean userIsAllowedToAccess(String userName, List<URI> requestedOrganizations) {
        var viewingScope = fetchViewingScope(userName);
        var allowed = getAllowedUnits(viewingScope);
        var illegal = difference(allowed, requestedOrganizations);
        return illegal.isEmpty();
    }

    private static Set<URI> difference(Set<URI> allowed, List<URI> requested) {
        var difference = new HashSet<>(requested);
        difference.removeAll(allowed);
        return difference;
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
        return userRetriever.fetchUser(userName)
                   .viewingScope()
                   .getIncludedUnitUris();
    }

    private Set<URI> getSubUnits(Set<URI> viewingScope) {
        return viewingScope.stream()
                   .flatMap(unit -> getSubUnits(unit).stream())
                   .collect(Collectors.toSet());
    }

    private Set<URI> getSubUnits(URI unit) {
        return attempt(() -> organizationRetriever.fetchOrganization(unit))
                   .map(org -> createModel(dtoObjectMapper.readTree(org.toJsonString())))
                   .map(ViewingScopeValidator::getObjectsOfPropertyHasPart)
                   .map(ViewingScopeValidator::toStreamOfRfdNodes)
                   .map(node -> concat(unit, node))
                   .map(ViewingScopeValidator::toUris)
                   .orElseThrow()
                   .collect(Collectors.toSet());
    }
}
