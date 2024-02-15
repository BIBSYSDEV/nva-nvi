package no.sikt.nva.nvi.events.cristin;

import static no.sikt.nva.nvi.events.cristin.CristinNviReportEventConsumer.NVI_ERRORS;
import static nva.commons.core.attempt.Try.attempt;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;
import software.amazon.awssdk.services.s3.S3Client;

public final class ErrorReport {

    private final String message;
    private final String bucket;
    private final String key;

    private ErrorReport(String message, String bucket, String key) {

        this.message = message;
        this.bucket = bucket;
        this.key = key;
    }

    public static ErrorReport withMessage(String message) {
        return ErrorReport.builder().withMessage(message).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public ErrorReport bucket(String bucket) {
        return this.copy().withBucket(bucket).build();
    }

    public ErrorReport key(String key) {
        return this.copy().withKey(key).build();
    }

    public void persist(S3Client s3Client) {
        var s3KeyName = getS3bucketPath(bucket, key);
        var s3Driver = new S3Driver(s3Client, bucket);
        attempt(() -> s3Driver.insertFile(s3KeyName, message)).orElseThrow();
    }

    public Builder copy() {
        return new Builder().withKey(key).withBucket(bucket).withMessage(message);
    }

    private static UnixPath getS3bucketPath(String bucket, String key) {
        return UriWrapper.fromHost(bucket).addChild(NVI_ERRORS).addChild(key).toS3bucketPath();
    }

    public static final class Builder {

        private String message;
        private String bucket;
        private String key;

        private Builder() {
        }

        public Builder withMessage(String message) {
            this.message = message;
            return this;
        }

        public Builder withBucket(String bucket) {
            this.bucket = bucket;
            return this;
        }

        public Builder withKey(String key) {
            this.key = key;
            return this;
        }

        public ErrorReport build() {
            return new ErrorReport(message, bucket, key);
        }
    }
}
