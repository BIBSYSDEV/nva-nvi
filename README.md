# nva-nvi

[![ci](https://github.com/BIBSYSDEV/nva-nvi/actions/workflows/build.yml/badge.svg)](https://github.com/BIBSYSDEV/nva-nvi/actions/workflows/build.yml)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/8405a7d7b690490f8690949d207d9cdf)](https://app.codacy.com/gh/BIBSYSDEV/nva-nvi/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)
[![Codacy Badge](https://app.codacy.com/project/badge/Coverage/8405a7d7b690490f8690949d207d9cdf)](https://app.codacy.com/gh/BIBSYSDEV/nva-nvi/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_coverage)

## Introduction

`nva-nvi` is designed to manage and process publication data for nvi (Norsk vitenskapsindeks) reporting.
The project enables evaluation, point calculation and curator management of publications that qualify as nvi candidates.

[SWAGGER UI](https://petstore.swagger.io/?url=https://raw.githubusercontent.com/BIBSYSDEV/nva-nvi/refs/heads/main/docs/openapi.yaml)

## Overview

![Alt text](resources/NVI-overview.png)

## Add a resource to your application

The application template uses AWS Serverless Application Model (AWS SAM) to define application resources. AWS SAM is an
extension of AWS CloudFormation with a simpler syntax for configuring common serverless application resources such as
functions, triggers, and APIs. For resources not included
in [the SAM specification](https://github.com/awslabs/serverless-application-model/blob/master/versions/2016-10-31.md),
you can use
standard [AWS CloudFormation](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-template-resource-type-ref.html)
resource types.

## When building a new environment

### Create OpenSearch Indexes

When building a new environment or the search indexes are deleted and needs to
be rebuilt, make sure to run the `InitHandler`.

## How to re-index

If the index already exists, and it is not necessary to update mappings, skip
to step 3.

1. Trigger `DeleteNviCandidateIndexHandler`. Verify that the index is deleted.
2. Trigger `InitHandler`. Verify that the index is created.
3. Trigger `NviBatchScanStartHandler` with input:

```json
{
  "types": [
    "CANDIDATE"
  ]
}
```

## How to start re-evaluation scan

To start a batch re-evaluation of existing publications for a given year as NVI candidates, trigger `BatchReEvaluateNviCandidatesHandler` with the following input:

```json
{
  "detail": {
    "pageSize": 500,
    "year": "2024"
  }
}
```

## Error handling

### How to requeue candidates in IndexDLQ

The `IndexDLQ` is a shared DLQ for all handlers related to indexing.
`NviRequeueDlqHandler` consumes messages from the `IndexDLQ`, and updates the
candidate in the DB with a new version. This will trigger the indexing flow
for the candidates.

Default `count` (number of messages consumed from DLQ) is 10. To specify
another `count`, provide it as input:

```json
{
  "count": 100
}
```

### DLQ Redrives

See template for which DLQs are available for redrive (configured with
`RedrivePolicy`). To start a DLQ redrive, locate the DLQ in the AWS console
(SQS) and press _Start DLQ Redrive_.

### Test CI

Some text here.
