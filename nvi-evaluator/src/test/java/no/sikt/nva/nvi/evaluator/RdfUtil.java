package no.sikt.nva.nvi.evaluator;

import java.io.StringWriter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

public class RdfUtil {

    public static String toTurtle(Model model){
        StringWriter stringWriter = new StringWriter();
        RDFDataMgr.write(stringWriter, model, Lang.TURTLE);
        return stringWriter.toString();
    }

    public static String toNTriples(Model model){
        StringWriter stringWriter = new StringWriter();
        RDFDataMgr.write(stringWriter, model, Lang.NTRIPLES);
        return stringWriter.toString();
    }

    public static String toJsonLd(Model model){
        StringWriter stringWriter = new StringWriter();
        RDFDataMgr.write(stringWriter, model, Lang.JSONLD);
        return stringWriter.toString();
    }

}

