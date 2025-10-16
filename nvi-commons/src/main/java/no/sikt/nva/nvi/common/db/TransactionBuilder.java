package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.db.DynamoRepository.createNewItem;

import java.util.function.UnaryOperator;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;

public class TransactionBuilder {
  private final TransactWriteItemsEnhancedRequest.Builder delegate;

  public TransactionBuilder() {
    this.delegate = TransactWriteItemsEnhancedRequest.builder();
  }

  public <T> TransactionBuilder addNew(
      T dao, DynamoDbTable<T> table, UnaryOperator<T> versionMutator) {
    delegate.addPutItem(table, createNewItem(versionMutator.apply(dao), table));
    return this;
  }

  public <T> TransactionBuilder addUpdated(
      T dao, DynamoDbTable<T> table, UnaryOperator<T> versionMutator) {
    //        delegate.addPutItem(table, put(versionMutator.apply(dao), table));
    return this;
  }

  public <T> TransactionBuilder addDelete(DynamoDbTable<T> table, T dao) {
    delegate.addDeleteItem(table, dao);
    return this;
  }

  public TransactWriteItemsEnhancedRequest build() {
    return delegate.build();
  }
}
