package no.sikt.nva.nvi.evaluator.aws;

import static no.sikt.nva.nvi.evaluator.aws.RegionUtil.acquireAwsRegion;
import no.sikt.nva.nvi.evaluator.StorageReader;
import no.unit.nva.events.models.EventReference;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.s3.S3Client;

public class S3StorageReader implements StorageReader<EventReference> {

    private static final String EXPANDED_RESOURCES_BUCKET = new Environment().readEnv(
        "EXPANDED_RESOURCES_BUCKET");

    private static final Logger LOGGER = LoggerFactory.getLogger(S3StorageReader.class);
    private final S3Client client;

    public S3StorageReader() {
        this(defaultS3Client());
    }

    public S3StorageReader(S3Client client) {
        this.client = client;
    }

    @Override
    public String read(EventReference blob) {
        var resourceRelativePath = UriWrapper.fromUri(blob.getUri()).toS3bucketPath();
        LOGGER.info("Getting s3 path for file {}", resourceRelativePath.toString());
        var s3Driver = new S3Driver(client, EXPANDED_RESOURCES_BUCKET);
        LOGGER.info("S3 driver initialized");
        return s3Driver.getFile(resourceRelativePath);
    }

    @JacocoGenerated
    private static S3Client defaultS3Client() {
        return S3Client.builder()
                   .region(acquireAwsRegion())
                   .httpClient(UrlConnectionHttpClient.builder().build())
                   .build();
    }
}
