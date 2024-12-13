package no.sikt.nva.nvi.common.utils;

public final class JsonPointers {

    public static final String JSON_PTR_CONTRIBUTOR = "/entityDescription/contributors";
    public static final String JSON_PTR_AFFILIATIONS = "/affiliations";
    public static final String JSON_PTR_ID = "/id";
    public static final String JSON_PTR_TYPE = "/type";
    public static final String JSON_PTR_COUNTRY_CODE = "/countryCode";
    public static final String JSON_PTR_PUBLICATION_DATE = "/entityDescription/publicationDate";
    public static final String JSON_PTR_MAIN_TITLE = "/entityDescription/mainTitle";
    public static final String JSON_PTR_ABSTRACT = "/entityDescription/abstract";
    public static final String JSON_POINTER_IDENTITY_ID = "/identity/id";
    public static final String JSON_POINTER_IDENTITY_NAME = "/identity/name";
    public static final String JSON_POINTER_IDENTITY_VERIFICATION_STATUS = "/identity/verificationStatus";
    public static final String JSON_PTR_INSTANCE_TYPE = "/entityDescription/reference/publicationInstance/type";
    public static final String JSON_PTR_PUBLICATION_CONTEXT = "/entityDescription/reference/publicationContext";
    public static final String JSON_PTR_SERIES_SCIENTIFIC_VALUE =
        "/entityDescription/reference/publicationContext/series/scientificValue";
    public static final String JSON_PTR_SERIES = "/entityDescription/reference/publicationContext/series";
    public static final String JSON_PTR_PUBLISHER =
        "/entityDescription/reference/publicationContext/publisher";
    public static final String JSON_PTR_CHAPTER_PUBLISHER =
        "/entityDescription/reference/publicationContext/entityDescription/reference/publicationContext"
        + "/publisher";
    public static final String JSON_PTR_CHAPTER_SERIES_SCIENTIFIC_VALUE =
        "/entityDescription/reference/publicationContext/entityDescription/reference/publicationContext/series"
        + "/scientificValue";
    public static final String JSON_PTR_CHAPTER_SERIES =
        "/entityDescription/reference/publicationContext/entityDescription/reference/publicationContext/series";
    public static final String JSON_PTR_PAGES_BEGIN = "/entityDescription/reference/publicationInstance/pages/begin";
    public static final String JSON_PRT_PAGES_END = "/entityDescription/reference/publicationInstance/pages/end";
    public static final String JSON_PTR_PAGES_NUMBER = "/entityDescription/reference/publicationInstance/pages/pages";
    public static final String JSON_PTR_YEAR = "/year";
    public static final String JSON_PTR_MONTH = "/month";
    public static final String JSON_PTR_DAY = "/day";
    public static final String JSON_PTR_IDENTITY = "/identity";
    public static final String JSON_PTR_ROLE_TYPE = "/role/type";
    public static final String JSON_PTR_NAME = "/name";
    public static final String JSON_PTR_ORCID = "/orcid";
    public static final String JSON_PTR_BODY = "/body";
    public static final String JSON_PTR_TOP_LEVEL_ORGANIZATIONS = "/topLevelOrganizations";
    public static final String JSON_PTR_LABELS = "/labels";
    public static final String JSON_PTR_SERIES_NAME = "/entityDescription/reference/publicationContext/series/name";
    public static final String JSON_PTR_PUBLISHER_NAME = "/entityDescription/reference/publicationContext/publisher"
                                                         + "/name";
    public static final String JSON_PTR_JOURNAL_NAME = "/entityDescription/reference/publicationContext/name";
    public static final String JSON_PTR_LANGUAGE = "/entityDescription/language";

    public static final String JSON_PTR_SERIES_PISSN = "/entityDescription/reference/publicationContext/series"
                                                        + "/printIssn";
    public static final String JSON_POINTER_JOURNAL_PISSN = "/entityDescription/reference/publicationContext"
                                                             + "/printIssn";

    private JsonPointers() {
    }
}
