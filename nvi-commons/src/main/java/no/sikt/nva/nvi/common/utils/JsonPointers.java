package no.sikt.nva.nvi.common.utils;

public final class JsonPointers {

    public static final String JSON_PTR_CONTRIBUTOR = "/entityDescription/contributors";
    public static final String JSON_PTR_AFFILIATIONS = "/affiliations";
    public static final String JSON_PTR_ID = "/id";
    public static final String JSON_PTR_PUBLICATION_DATE = "/entityDescription/publicationDate";
    public static final String JSON_PTR_MAIN_TITLE = "/entityDescription/mainTitle";
    public static final String JSON_POINTER_IDENTITY_VERIFICATION_STATUS = "/identity/verificationStatus";
    public static final String JSON_PTR_INSTANCE_TYPE = "/entityDescription/reference/publicationInstance/type";
    public static final String JSON_PTR_PUBLICATION_CONTEXT = "/entityDescription/reference/publicationContext";
    public static final String JSON_PTR_SERIES_LEVEL = "/entityDescription/reference/publicationContext/series/level";
    public static final String JSON_PTR_SERIES = "/entityDescription/reference/publicationContext/series";
    public static final String JSON_PTR_PUBLISHER =
        "/entityDescription/reference/publicationContext/publisher";
    public static final String JSON_PTR_CHAPTER_PUBLISHER =
        "/entityDescription/reference/publicationContext/entityDescription/reference/publicationContext"
        + "/publisher";
    public static final String JSON_PTR_CHAPTER_SERIES_LEVEL =
        "/entityDescription/reference/publicationContext/entityDescription/reference/publicationContext/series/level";
    public static final String JSON_PTR_CHAPTER_SERIES =
        "/entityDescription/reference/publicationContext/entityDescription/reference/publicationContext/series";
    public static final String JSON_PTR_PUBLICATION_DATE_YEAR = "/entityDescription/publicationDate/year";
    public static final String JSON_PTR_YEAR = "/year";
    public static final String JSON_PTR_MONTH = "/month";
    public static final String JSON_PTR_DAY = "/day";
    public static final String JSON_PTR_LABELS = "/labels";
    public static final String JSON_PTR_IDENTITY = "/identity";
    public static final String JSON_PTR_NAME = "/name";
    public static final String JSON_PTR_ORCID = "/orcid";

    private JsonPointers() {
    }
}
