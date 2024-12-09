package no.sikt.nva.nvi.common.utils;

import static java.util.Objects.isNull;
import static nva.commons.core.attempt.Try.attempt;
import static nva.commons.core.ioutils.IoUtils.stringToStream;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.StringWriter;
import org.apache.jena.query.QueryExecution;
import java.io.InputStream;
import java.nio.file.Path;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.ioutils.IoUtils;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GraphUtils {
    public static final String APPLICATION_JSON = "application/json";
    public static final String PART_OF_PROPERTY = "https://nva.sikt.no/ontology/publication#partOf";
    public static final String HAS_PART_PROPERTY = "https://nva.sikt.no/ontology/publication#hasPart";
    private static final String NVI_SPARQL = IoUtils.stringFromResources(Path.of("sparql/nvi.sparql"));
    private static final Logger LOGGER = LoggerFactory.getLogger(GraphUtils.class);

    @JacocoGenerated
    public GraphUtils() {
    }

    public static Model createModel(JsonNode body) {
        var model = ModelFactory.createDefaultModel();
        loadDataIntoModel(model, stringToStream(body.toString()));
        return model;
    }

    @JacocoGenerated
    public static String toTurtle(Model model) {
        StringWriter stringWriter = new StringWriter();
        RDFDataMgr.write(stringWriter, model, Lang.TURTLE);
        return stringWriter.toString();
    }

    @JacocoGenerated
    public static String toNTriples(Model model) {
        StringWriter stringWriter = new StringWriter();
        RDFDataMgr.write(stringWriter, model, Lang.NTRIPLES);
        return stringWriter.toString();
    }

    @JacocoGenerated
    public static String toJsonLd(Model model) {
        StringWriter stringWriter = new StringWriter();
        RDFDataMgr.write(stringWriter, model, Lang.JSONLD);
        return stringWriter.toString();
    }

    @JacocoGenerated
    public static boolean isNviCandidate(Model model) {
        return attempt(() -> QueryExecutionFactory.create(NVI_SPARQL, model)).map(QueryExecution::execAsk)
                   .map(Boolean::booleanValue)
                   .orElseThrow();
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
