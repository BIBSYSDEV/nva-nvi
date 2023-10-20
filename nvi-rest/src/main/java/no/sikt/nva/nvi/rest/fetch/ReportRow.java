package no.sikt.nva.nvi.rest.fetch;

import no.sikt.nva.nvi.rest.fetch.Excel.ColumnName;
import no.sikt.nva.nvi.rest.fetch.Excel.MyWorkbook;
import nva.commons.core.JacocoGenerated;

@MyWorkbook
public record ReportRow(
    @ColumnName("EIERKODE") String ownerCode,
    @ColumnName("ARSTALL") Integer year,
    @ColumnName("INSTITUSJONSKODE") Integer institutionCode,
    @ColumnName("VARBEIDLOPENR") Integer runningNumber,
    @ColumnName("ARSTALL_REG") Integer registrationYear,
    @ColumnName("PUBIDNR_ITAR") Integer publicationIdItar,
    @ColumnName("KILDEID") Integer sourceId,
    @ColumnName("STATUS_KONTROLLERT") String statusControl,
    @ColumnName("PUBLIKASJONSFORM") Integer publicationForm,
    @ColumnName("PUBLISERINGSKANAL") Integer publicationChannel,
    @ColumnName("PUBLISERINGSKANALTYPE") Integer publicationType,
    @ColumnName("ISSN") String issn,
    @ColumnName("KVALITETSNIVAKODE") Integer qualityLevel,
    @ColumnName("PERSONLOPENR") Integer personId,
    @ColumnName("STATUS_GJEST") String statusGuest,
    @ColumnName("STILLINGSKODE") Integer positionCode,
    @ColumnName("MANED_ANSATT_RAPPAAR") Integer monthsEmployed,
    @ColumnName("INSTITUSJONSNR") Integer institutionNr,
    @ColumnName("AVDNR") Integer departmentNr,
    @ColumnName("UNDAVDNR") Integer subDepartmentNr,
    @ColumnName("GRUPPENR") Integer groupNr,
    @ColumnName("NSDSTEDKODE") Integer nsdCode,
    @ColumnName("FORFATTERE_STED") Integer writerPlace,
    @ColumnName("FORFATTERE_TOTALT") Integer writerTotal,
    @ColumnName("FORFATTERE_INT") Integer writerInternational,
    @ColumnName("VEKTET") Double weight,
    @ColumnName("MERKNAD") String remark,
    @ColumnName("ETTERNAVN") String lastname,
    @ColumnName("FORNAVN") String firstname,
    @ColumnName("KJØNN") String gender,
    @ColumnName("ALDER") Integer age,
    @ColumnName("STILLINGSANDEL") Integer positionPercentage,
    @ColumnName("PUBLISERINGSKANALNAVN") String publicationChannelName,
    @ColumnName("UTBREDELSESOMRADE") String prevelance,
    @ColumnName("SIDE_FRA") Integer pageFrom,
    @ColumnName("SIDE_TIL") Integer pageTo,
    @ColumnName("SIDEANTALL") Integer pageCount,
    @ColumnName("VA_TITTEL") String title,
    @ColumnName("SPRÅK") String language,
    @ColumnName("RAPPORTSTATUS") String reportStatus,
    @ColumnName("VEKTINGSTALL") Double weightNumber,
    @ColumnName("FAKTORTALL_SAMARBEID_2003") Double factorCoop2003,
    @ColumnName("FORFATTERANDEL_2003") Double writerShare2003,
    @ColumnName("FORFATTERVEKT_2003") Double writerWeight2003,
    @ColumnName("FAKTORTALL_SAMARBEID") Double factorCoop,
    @ColumnName("FORFATTERDEL") Double writerShare,
    @ColumnName("FORFATTERVEKT") Double writerWeight
) {

    public static Builder builder() {
        return new Builder();
    }

    @JacocoGenerated
    public static final class Builder {

        private String ownerCode;
        private Integer year;
        private Integer institutionCode;
        private Integer runningNumber;
        private Integer registrationYear;
        private Integer publicationIdItar;
        private Integer sourceId;
        private String statusControl;
        private Integer publicationForm;
        private Integer publicationChannel;
        private Integer publicationType;
        private String issn;
        private Integer qualityLevel;
        private Integer personId;
        private String statusGuest;
        private Integer positionCode;
        private Integer monthsEmployed;
        private Integer institutionNr;
        private Integer departmentNr;
        private Integer subDepartmentNr;
        private Integer groupNr;
        private Integer nsdCode;
        private Integer writerPlace;
        private Integer writerTotal;
        private Integer writerInternational;
        private Double weight;
        private String remark;
        private String lastname;
        private String firstname;
        private String gender;
        private Integer age;
        private Integer positionPercentage;
        private String publicationChannelName;
        private String prevelance;
        private Integer pageFrom;
        private Integer pageTo;
        private Integer pageCount;
        private String title;
        private String language;
        private String reportStatus;
        private Double weightNumber;
        private Double factorCoop2003;
        private Double writerShare2003;
        private Double writerWeight2003;
        private Double factorCoop;
        private Double writerShare;
        private Double writerWeight;

        private Builder() {
        }

        public Builder withOwnerCode(String ownerCode) {
            this.ownerCode = ownerCode;
            return this;
        }

        public Builder withYear(Integer year) {
            this.year = year;
            return this;
        }

        public Builder withInstitutionCode(Integer institutionCode) {
            this.institutionCode = institutionCode;
            return this;
        }

        public Builder withRunningNumber(Integer runningNumber) {
            this.runningNumber = runningNumber;
            return this;
        }

        public Builder withRegistrationYear(Integer registrationYear) {
            this.registrationYear = registrationYear;
            return this;
        }

        public Builder withPublicationIdItar(Integer publicationIdItar) {
            this.publicationIdItar = publicationIdItar;
            return this;
        }

        public Builder withSourceId(Integer sourceId) {
            this.sourceId = sourceId;
            return this;
        }

        public Builder withStatusControl(String statusControl) {
            this.statusControl = statusControl;
            return this;
        }

        public Builder withPublicationForm(Integer publicationForm) {
            this.publicationForm = publicationForm;
            return this;
        }

        public Builder withPublicationChannel(Integer publicationChannel) {
            this.publicationChannel = publicationChannel;
            return this;
        }

        public Builder withPublicationType(Integer publicationType) {
            this.publicationType = publicationType;
            return this;
        }

        public Builder withIssn(String issn) {
            this.issn = issn;
            return this;
        }

        public Builder withQualityLevel(Integer qualityLevel) {
            this.qualityLevel = qualityLevel;
            return this;
        }

        public Builder withPersonId(Integer personId) {
            this.personId = personId;
            return this;
        }

        public Builder withStatusGuest(String statusGuest) {
            this.statusGuest = statusGuest;
            return this;
        }

        public Builder withPositionCode(Integer positionCode) {
            this.positionCode = positionCode;
            return this;
        }

        public Builder withMonthsEmployed(Integer monthsEmployed) {
            this.monthsEmployed = monthsEmployed;
            return this;
        }

        public Builder withInstitutionNr(Integer institutionNr) {
            this.institutionNr = institutionNr;
            return this;
        }

        public Builder withDepartmentNr(Integer departmentNr) {
            this.departmentNr = departmentNr;
            return this;
        }

        public Builder withSubDepartmentNr(Integer subDepartmentNr) {
            this.subDepartmentNr = subDepartmentNr;
            return this;
        }

        public Builder withGroupNr(Integer groupNr) {
            this.groupNr = groupNr;
            return this;
        }

        public Builder withNsdCode(Integer nsdCode) {
            this.nsdCode = nsdCode;
            return this;
        }

        public Builder withWriterPlace(Integer writerPlace) {
            this.writerPlace = writerPlace;
            return this;
        }

        public Builder withWriterTotal(Integer writerTotal) {
            this.writerTotal = writerTotal;
            return this;
        }

        public Builder withWriterInternational(Integer writerInternational) {
            this.writerInternational = writerInternational;
            return this;
        }

        public Builder withWeight(Double weight) {
            this.weight = weight;
            return this;
        }

        public Builder withRemark(String remark) {
            this.remark = remark;
            return this;
        }

        public Builder withLastname(String lastname) {
            this.lastname = lastname;
            return this;
        }

        public Builder withFirstname(String firstname) {
            this.firstname = firstname;
            return this;
        }

        public Builder withGender(String gender) {
            this.gender = gender;
            return this;
        }

        public Builder withAge(Integer age) {
            this.age = age;
            return this;
        }

        public Builder withPositionPercentage(Integer positionPercentage) {
            this.positionPercentage = positionPercentage;
            return this;
        }

        public Builder withPublicationChannelName(String publicationChannelName) {
            this.publicationChannelName = publicationChannelName;
            return this;
        }

        public Builder withPrevelance(String prevelance) {
            this.prevelance = prevelance;
            return this;
        }

        public Builder withPageFrom(Integer pageFrom) {
            this.pageFrom = pageFrom;
            return this;
        }

        public Builder withPageTo(Integer pageTo) {
            this.pageTo = pageTo;
            return this;
        }

        public Builder withPageCount(Integer pageCount) {
            this.pageCount = pageCount;
            return this;
        }

        public Builder withTitle(String title) {
            this.title = title;
            return this;
        }

        public Builder withLanguage(String language) {
            this.language = language;
            return this;
        }

        public Builder withReportStatus(String reportStatus) {
            this.reportStatus = reportStatus;
            return this;
        }

        public Builder withWeightNumber(Double weightNumber) {
            this.weightNumber = weightNumber;
            return this;
        }

        public Builder withFactorCoop2003(Double factorCoop2003) {
            this.factorCoop2003 = factorCoop2003;
            return this;
        }

        public Builder withWriterShare2003(Double writerShare2003) {
            this.writerShare2003 = writerShare2003;
            return this;
        }

        public Builder withWriterWeight2003(Double writerWeight2003) {
            this.writerWeight2003 = writerWeight2003;
            return this;
        }

        public Builder withFactorCoop(Double factorCoop) {
            this.factorCoop = factorCoop;
            return this;
        }

        public Builder withWriterShare(Double writerShare) {
            this.writerShare = writerShare;
            return this;
        }

        public Builder withWriterWeight(Double writerWeight) {
            this.writerWeight = writerWeight;
            return this;
        }

        public ReportRow build() {
            return new ReportRow(ownerCode, year, institutionCode, runningNumber, registrationYear, publicationIdItar,
                                 sourceId, statusControl, publicationForm, publicationChannel, publicationType, issn,
                                 qualityLevel, personId, statusGuest, positionCode, monthsEmployed, institutionNr,
                                 departmentNr, subDepartmentNr, groupNr, nsdCode, writerPlace, writerTotal,
                                 writerInternational, weight, remark, lastname, firstname, gender, age,
                                 positionPercentage, publicationChannelName, prevelance, pageFrom, pageTo, pageCount,
                                 title, language, reportStatus, weightNumber, factorCoop2003, writerShare2003,
                                 writerWeight2003, factorCoop, writerShare, writerWeight);
        }
    }
}
