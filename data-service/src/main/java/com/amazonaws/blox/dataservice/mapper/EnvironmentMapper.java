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
package com.amazonaws.blox.dataservice.mapper;

import com.amazonaws.blox.dataservice.model.Environment;
import com.amazonaws.blox.dataservice.model.EnvironmentRevision;
import com.amazonaws.blox.dataservice.repository.model.EnvironmentDDBRecord;
import com.amazonaws.blox.dataservice.repository.model.EnvironmentRevisionDDBRecord;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface EnvironmentMapper {

  @Mapping(source = "environmentId.environmentName", target = "environmentName")
  @Mapping(source = "environmentType", target = "type")
  @Mapping(source = "environmentHealth", target = "health")
  @Mapping(source = "environmentStatus", target = "status")
  @Mapping(target = "recordVersion", ignore = true)
  @Mapping(
    target = "accountIdCluster",
    expression = "java(environment.getEnvironmentId().generateAccountIdCluster())"
  )
  EnvironmentDDBRecord toEnvironmentDDBRecord(Environment environment);

  @InheritInverseConfiguration
  @Mapping(
    target = "environmentId.accountId",
    expression =
        "java(EnvironmentId.getAccountIdFromAccountIdCluster(environmentDDBRecord.getAccountIdCluster()))"
  )
  @Mapping(
    target = "environmentId.cluster",
    expression =
        "java(EnvironmentId.getClusterFromAccountIdCluster(environmentDDBRecord.getAccountIdCluster()))"
  )
  Environment toEnvironment(EnvironmentDDBRecord environmentDDBRecord);

  @Mapping(source = "environmentId.cluster", target = "clusterName")
  @Mapping(source = "environmentId.environmentName", target = "environmentName")
  @Mapping(
    target = "accountIdClusterEnvironmentName",
    expression =
        "java(environmentRevision.getEnvironmentId().generateAccountIdClusterEnvironmentName())"
  )
  @Mapping(source = "instanceGroup.attributes", target = "attributes")
  @Mapping(target = "recordVersion", ignore = true)
  EnvironmentRevisionDDBRecord toEnvironmentRevisionDDBRecord(
      EnvironmentRevision environmentRevision);

  @InheritInverseConfiguration
  @Mapping(
    target = "environmentId.accountId",
    expression =
        "java(EnvironmentId.getAccountIdFromAccountIdClusterEnvironmentName(environmentRevisionDDBRecord.getAccountIdClusterEnvironmentName()))"
  )
  EnvironmentRevision toEnvironmentRevision(
      EnvironmentRevisionDDBRecord environmentRevisionDDBRecord);
}
