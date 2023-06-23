package no.sikt.nva.nvi.index.utils;

public final class ResourceJsonConstants {

    public static final String JSON_PTR_CONTRIBUTOR = "/entityDescription/contributors";
    public static final String JSON_PTR_AFFILIATIONS = "/affiliations";
    public static final String JSON_PTR_ID = "/id";
    public static final String JSON_PTR_PUBLICATION_CHANNEL_TYPE = "/entityDescription/reference/publicationContext"
                                                                   + "/type";
    public static final String JSON_PTR_PUBLICATION_CHANNEL_LEVEL = "/entityDescription/reference/publicationContext"
                                                                    + "/level";
    public static final String JSON_PTR_PUBLICATION_CHANNEL_NAME = "/entityDescription/reference/publicationContext"
                                                                   + "/name";
    public static final String JSON_PTR_PUBLICATION_CHANNEL_ID = "/entityDescription/reference/publicationContext/id";
    public static final String JSON_PTR_PUBLICATION_DATE = "/entityDescription/publicationDate";
    public static final String JSON_PTR_MAIN_TITLE = "/entityDescription/mainTitle";
    public static final String JSON_PTR_INSTANCE_TYPE = "/entityDescription/reference/publicationInstance/type";
    public static final String JSON_PTR_PUBLICATION_DATE_YEAR = "/entityDescription/publicationDate/year";
    public static final String FIELD_PUBLICATION_DATE_YEAR = "year";
    public static final String FIELD_PUBLICATION_DATE_MONTH = "month";
    public static final String FIELD_PUBLICATION_DATE_DAY = "day";
    public static final String FIELD_ID = "id";
    public static final String FIELD_LABELS = "labels";
    public static final String FIELD_IDENTITY = "identity";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_ORCID = "orcid";

    private ResourceJsonConstants() {
    }
}
