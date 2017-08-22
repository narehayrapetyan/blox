/*
 * Copyright 2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may
 * not use this file except in compliance with the License. A copy of the
 * License is located at
 *
 *     http://aws.amazon.com/apache2.0/
 *
 * or in the "LICENSE" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.amazonaws.blox.dataservice.storage;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.blox.dataservice.exception.StorageException;
import com.amazonaws.blox.dataservice.model.Deployment;
import com.amazonaws.blox.dataservice.model.DeploymentStatus;
import com.amazonaws.blox.dataservice.storage.model.DeploymentDDBRecord;
import com.amazonaws.blox.dataservice.storage.model.DeploymentDDBRecordTranslator;
import com.amazonaws.blox.dataservicemodel.v1.exception.DeploymentDoesNotExist;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class DeploymentDDBStore implements DeploymentStore {

  @NonNull private DynamoDBMapper dynamoDBMapper;

  @Override
  public Deployment createDeployment(final Deployment deployment) throws StorageException {
    try {
      final DeploymentDDBRecord record =
          DeploymentDDBRecordTranslator.toDeploymentDDBRecord(deployment);
      dynamoDBMapper.save(record);
      return DeploymentDDBRecordTranslator.fromDeploymentDDBRecord(record);
    } catch (final AmazonServiceException e) {
      throw new StorageException(String.format("Could not save deployment %s", deployment));
    }
  }

  @Override
  public Deployment updateDeployment(final Deployment deployment) {
    DeploymentDDBRecord record =
        dynamoDBMapper.load(DeploymentDDBRecord.withHashKey(deployment.getDeploymentId()));
    record = DeploymentDDBRecordTranslator.updateDeploymentDDBRecord(deployment, record);
    dynamoDBMapper.save(record);
    return DeploymentDDBRecordTranslator.fromDeploymentDDBRecord(record);
  }

  @Override
  public void deleteAllDeployments() throws StorageException {
    final DynamoDBScanExpression expression = new DynamoDBScanExpression();
    final List<DeploymentDDBRecord> records =
        dynamoDBMapper.scan(DeploymentDDBRecord.class, expression);

    List<DynamoDBMapper.FailedBatch> failedBatch = dynamoDBMapper.batchDelete(records);
    if (!failedBatch.isEmpty()) {
      //TODO: fix message
      throw new StorageException("");
    }
  }

  @Override
  public Deployment getDeploymentById(String deploymentId)
      throws DeploymentDoesNotExist, StorageException {
    try {
      final DeploymentDDBRecord record =
          dynamoDBMapper.load(DeploymentDDBRecord.withHashKey(deploymentId));

      if (record == null) {
        throw new DeploymentDoesNotExist(
            String.format("Deployment with id %s does not exist", deploymentId));
      }

      return DeploymentDDBRecordTranslator.fromDeploymentDDBRecord(record);
    } catch (final AmazonServiceException e) {
      throw new StorageException(
          String.format("Could not load deployment with id %s", deploymentId));
    }
  }

  @Override
  public List<Deployment> getDeploymentsByStatus(DeploymentStatus status) {
    DynamoDBQueryExpression<DeploymentDDBRecord> expression =
        new DynamoDBQueryExpression<DeploymentDDBRecord>()
            .withIndexName(DeploymentDDBRecord.DEPLOYMENT_STATUS_GSI_NAME)
            .withConsistentRead(false)
            .withHashKeyValues(DeploymentDDBRecord.withIndexHashKey(DeploymentStatus.Pending));
    final List<DeploymentDDBRecord> records =
        dynamoDBMapper.query(DeploymentDDBRecord.class, expression);
    return records
        .stream()
        .map(r -> DeploymentDDBRecordTranslator.fromDeploymentDDBRecord(r))
        .collect(Collectors.toList());
  }

  @Override
  public List<Deployment> getAllDeploymentsInIndex() {
    final DynamoDBScanExpression expression =
        new DynamoDBScanExpression()
            .withIndexName(DeploymentDDBRecord.DEPLOYMENT_STATUS_GSI_NAME)
            .withConsistentRead(false);

    final List<DeploymentDDBRecord> records =
        dynamoDBMapper.scan(DeploymentDDBRecord.class, expression);
    return records
        .stream()
        .map(r -> DeploymentDDBRecordTranslator.fromDeploymentDDBRecord(r))
        .collect(Collectors.toList());
  }
}