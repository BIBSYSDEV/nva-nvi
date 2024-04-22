# nva-nvi

## Activating Lambda SnapStart

When building a new environment, remove SnapStart, and add it back once the functions are deployed
and have a published version.

From AWS docs, [Supported features and limitations](https://docs.aws.amazon.com/lambda/latest/dg/snapstart.html#snapstart-runtimes):
_You can use SnapStart only on published function versions and aliases that point to versions. You can't use SnapStart on a function's unpublished version ($LATEST)._

## OpenSearch Indexes

When building a new environment or the search indexes are deleted and needs to be rebuilt, make sure to run the InitHandler.