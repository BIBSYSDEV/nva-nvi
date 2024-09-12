package no.sikt.nva.nvi.index.apigateway.utils;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import nva.commons.core.ioutils.IoUtils;

public final class LanguageLabelUtil {

    private static final Path LANGUAGE_LABELS_PATH = Path.of("supportedLanguageLabels.csv");
    private static final String LINE_SEPERATOR = ",";
    private static final Map<String, String> supportedLanguageLabels = readStaticLanguageLabels();

    private LanguageLabelUtil() {
    }

    public static Optional<String> getLabel(String languageUri) {
        return Optional.ofNullable(supportedLanguageLabels.get(languageUri));
    }

    private static Map<String, String> readStaticLanguageLabels() {
        return IoUtils.linesfromResource(LANGUAGE_LABELS_PATH).stream()
                   .map(LanguageLabelUtil::splitByLineSeperator)
                   .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1]));
    }

    private static String[] splitByLineSeperator(String line) {
        return line.split(LINE_SEPERATOR);
    }
}

