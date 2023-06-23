package no.sikt.nva.nvi.index;

import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.index.aws.S3StorageReader;
import no.sikt.nva.nvi.index.model.NviCandidate;
import software.amazon.awssdk.services.s3.S3Client;

class FakeStorageReader implements StorageReader<NviCandidate> {

    private final StorageReader<NviCandidate> reader;

    public FakeStorageReader(S3Client s3Client) {
        this.reader = new S3StorageReader(s3Client);
    }

    @Override
    public String read(NviCandidate blob) {
        return reader.read(blob);
    }
}
