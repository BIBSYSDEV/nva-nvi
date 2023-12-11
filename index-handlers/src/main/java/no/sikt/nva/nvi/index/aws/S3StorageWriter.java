package no.sikt.nva.nvi.index.aws;

import java.io.IOException;
import java.net.URI;
import no.sikt.nva.nvi.common.StorageWriter;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UnixPath;
import software.amazon.awssdk.services.s3.S3Client;

public class S3StorageWriter implements StorageWriter<NviCandidateIndexDocument> {

    public static final String NVI_CANDIDATES_FOLDER = "nvi-candidates";
    public static final String GZIP_ENDING = ".gz";
    private final S3Driver s3Driver;

    @JacocoGenerated
    public S3StorageWriter(String bucket) {
        this(S3Driver.defaultS3Client().build(), bucket);
    }

    public S3StorageWriter(S3Client client, String bucket) {
        this.s3Driver = new S3Driver(client, bucket);
    }

    @Override
    public URI write(NviCandidateIndexDocument document) throws IOException {
        return s3Driver.insertFile(createFilePath(document), document.toJsonString());
    }

    private static UnixPath createFilePath(NviCandidateIndexDocument document) {
        return UnixPath.of(NVI_CANDIDATES_FOLDER).addChild(document.identifier() + GZIP_ENDING);
    }
}
