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
package com.amazonaws.blox.schedulingmanager.deployment.handler;

import com.amazonaws.AmazonClientException;
import com.amazonaws.blox.schedulingmanager.deployment.DeploymentWorkflowApplication;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.stepfunctions.AWSStepFunctions;
import com.amazonaws.services.stepfunctions.model.StartExecutionRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StartDeployment extends DeploymentWorkflowApplication
    implements RequestHandler<String, String> {

  @Override
  public String handleRequest(String input, Context context) {
    log.info("startDeployment lambda");

    final AWSStepFunctions stepFunctionsClient = applicationContext.getBean(AWSStepFunctions.class);

    final StartExecutionRequest startExecutionRequest =
        new StartExecutionRequest().withStateMachineArn("").withInput("initial input").withName("");
    try {
      stepFunctionsClient.startExecution(startExecutionRequest);
    } catch (final AmazonClientException e) {
      //log
      throw e;
    }

    return null;
  }
}
