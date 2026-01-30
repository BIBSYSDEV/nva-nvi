package no.sikt.nva.nvi.common.notification;

@FunctionalInterface
public interface NotificationClient<T> {

  T publish(String message, String topic);
}
