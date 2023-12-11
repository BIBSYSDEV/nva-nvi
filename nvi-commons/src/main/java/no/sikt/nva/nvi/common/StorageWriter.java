package no.sikt.nva.nvi.common;

import java.io.IOException;
import java.net.URI;

public interface StorageWriter<T> {

    URI write(T blob) throws IOException;
}
