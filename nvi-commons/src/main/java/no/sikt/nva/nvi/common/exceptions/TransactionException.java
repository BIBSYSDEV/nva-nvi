package no.sikt.nva.nvi.common.exceptions;

import java.util.ArrayList;
import java.util.Optional;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.Delete;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.Update;

@JacocoGenerated
public class TransactionException extends RuntimeException {

  public TransactionException(String message) {
    super(message);
  }

  public static TransactionException from(
      TransactionCanceledException exception, TransactWriteItemsEnhancedRequest request) {
    return new TransactionException(constructErrorMessage(exception, request));
  }

  @JacocoGenerated
  private static String constructErrorMessage(
      TransactionCanceledException e, TransactWriteItemsEnhancedRequest request) {
    var reasons = e.cancellationReasons();
    var items = request.transactWriteItems();
    var failureMessages = new ArrayList<String>();

    for (int i = 0; i < reasons.size(); i++) {
      var reason = reasons.get(i);
      var item = items.get(i);
      var operation = getOperation(item);
      var condition = getCondition(item);

      var message =
          "Operation %s with condition %s for item %d failed with code %s and message %s"
              .formatted(operation, condition, i, reason.code(), reason.message());

      failureMessages.add(message);
    }

    return String.join("; ", failureMessages);
  }

  private static String getOperation(TransactWriteItem item) {
    if (Optional.ofNullable(item).map(TransactWriteItem::put).isPresent()) {
      return "PUT";
    } else if (Optional.ofNullable(item).map(TransactWriteItem::update).isPresent()) {
      return "UPDATE";
    } else if (Optional.ofNullable(item).map(TransactWriteItem::delete).isPresent()) {
      return "DELETE";
    } else {
      return "UNKNOWN_OPERATION";
    }
  }

  private static String getCondition(TransactWriteItem item) {
    if (Optional.ofNullable(item)
        .map(TransactWriteItem::put)
        .map(Put::conditionExpression)
        .isPresent()) {
      return item.put().conditionExpression();
    } else if (Optional.ofNullable(item)
        .map(TransactWriteItem::update)
        .map(Update::conditionExpression)
        .isPresent()) {
      return item.update().conditionExpression();
    } else if (Optional.ofNullable(item)
        .map(TransactWriteItem::delete)
        .map(Delete::conditionExpression)
        .isPresent()) {
      return item.delete().conditionExpression();
    } else if (Optional.ofNullable(item).map(TransactWriteItem::conditionCheck).isPresent()) {
      return item.conditionCheck().conditionExpression();
    } else {
      return "NO_CONDITION";
    }
  }
}
