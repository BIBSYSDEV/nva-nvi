package no.sikt.nva.nvi.evaluator.aws;

import static no.sikt.nva.nvi.evaluator.aws.RegionUtil.acquireAwsRegion;
import static no.unit.nva.s3.S3Driver.S3_SCHEME;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import no.sikt.nva.nvi.evaluator.StorageReader;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.SingletonCollector;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.s3.S3Client;

public class S3StorageReader implements StorageReader<S3Event> {

    private final S3Client client;

    public S3StorageReader() {
        this(defaultS3Client());
    }

    public S3StorageReader(S3Client client) {
        this.client = client;
    }

    @Override
    public String read(S3Event blob) {
        return blob.getRecords().stream()
                   .map(S3EventNotification.S3EventNotificationRecord::getS3)
                   .map(this::fetchDataFromBucket)
                   .collect(SingletonCollector.collectOrElse(null));
    }

    @JacocoGenerated
    private static S3Client defaultS3Client() {
        return S3Client.builder()
                   .region(acquireAwsRegion())
                   .httpClient(UrlConnectionHttpClient.builder().build())
                   .build();
    }

    private static UnixPath formatS3Uri(String bucketName, String key) {
        return new UriWrapper(S3_SCHEME, bucketName)
                   .addChild(key)
                   .toS3bucketPath();
    }

    private String read(String bucket, String fileName) {
        var s3Driver = new S3Driver(client, bucket);
        return s3Driver.getFile(formatS3Uri(bucket, fileName));
    }

    private String fetchDataFromBucket(S3EventNotification.S3Entity s3Entity) {
        var bucketName = s3Entity.getBucket().getName();
        var key = s3Entity.getObject().getKey();
        return read(bucketName, key);
    }
}
