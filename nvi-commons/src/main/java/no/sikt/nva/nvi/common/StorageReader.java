package no.sikt.nva.nvi.common;

import java.net.URI;

public interface StorageReader<T> {

    String readMessage(T blob);

    String readUri(URI uri);
}
