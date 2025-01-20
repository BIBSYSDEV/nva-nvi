package no.sikt.nva.nvi.common.notification;

public interface NotificationClient<T> {

  T publish(String message, String topic);
}
