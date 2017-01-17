// Copyright 2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License"). You may
// not use this file except in compliance with the License. A copy of the
// License is located at
//
//     http://aws.amazon.com/apache2.0/
//
// or in the "license" file accompanying this file. This file is distributed
// on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
// express or implied. See the License for the specific language governing
// permissions and limitations under the License.

package deployment

import (
	"context"

	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/service/ecs"
	"github.com/blox/blox/daemon-scheduler/pkg/facade"
	"github.com/blox/blox/daemon-scheduler/pkg/types"
	log "github.com/cihub/seelog"
	"github.com/pkg/errors"
)

const (
	TaskPending = "PENDING"
)

type DeploymentWorker interface {
	// UpdateInProgressDeployment checks for in-progress deployments and moves them to complete when
	// the tasks started by the deployment have moved out of pending status
	UpdateInProgressDeployment(ctx context.Context, environmentName string) (*types.Deployment, error)
}

type deploymentWorker struct {
	environment Environment
	deployment  Deployment
	ecs         facade.ECS
	css         facade.ClusterState
}

func NewDeploymentWorker(
	environment Environment,
	deployment Deployment,
	ecs facade.ECS,
	css facade.ClusterState) DeploymentWorker {
	return deploymentWorker{
		environment: environment,
		deployment:  deployment,
		ecs:         ecs,
		css:         css,
	}
}

func (d deploymentWorker) UpdateInProgressDeployment(ctx context.Context,
	environmentName string) (*types.Deployment, error) {

	if environmentName == "" {
		return nil, errors.New("Environment name is missing")
	}

	deployment, err := d.deployment.GetInProgressDeployment(ctx, environmentName)
	if err != nil {
		return nil, err
	}

	if deployment == nil {
		return nil, nil
	}

	environment, err := d.environment.GetEnvironment(ctx, environmentName)
	if err != nil {
		return nil, errors.Wrapf(err, "Error finding environment with name %s", environmentName)
	}

	if environment == nil {
		return nil, nil
	}

	taskProgress, err := d.checkDeploymentTaskProgress(environment, deployment)
	if err != nil {
		return nil, errors.Wrapf(err, "Error checking deployment %s progress in environment %s",
			deployment.ID, environment.Name)
	}

	updatedDeployment, err := d.updateDeployment(ctx, environment, deployment, taskProgress)
	if err != nil {
		return nil, err
	}

	return updatedDeployment, nil
}

func (d deploymentWorker) checkDeploymentTaskProgress(environment *types.Environment,
	deployment *types.Deployment) (*ecs.DescribeTasksOutput, error) {

	if environment.Cluster == "" {
		return nil, errors.New("Environment cluster should not be empty")
	}

	// TODO: replace with cluster state calls
	tasks, err := d.ecs.ListTasks(environment.Cluster, deployment.ID)
	if err != nil {
		return nil, err
	}

	resp, err := d.ecs.DescribeTasks(environment.Cluster, tasks)
	if err != nil {
		return nil, err
	}

	return resp, nil
}

func (d deploymentWorker) updateDeployment(ctx context.Context,
	environment *types.Environment, deployment *types.Deployment,
	resp *ecs.DescribeTasksOutput) (*types.Deployment, error) {

	updatedDeployment, err := d.updateDeploymentObject(deployment, resp)
	if err != nil {
		return nil, err
	}

	// retrieve in-progress again to make sure it has not been updated by another process
	// TODO: wrap the in-progress check and updateDeployment in a transaction
	deployment, err = d.deployment.GetInProgressDeployment(ctx, environment.Name)
	if err != nil {
		return nil, err
	}

	if deployment == nil || deployment.ID != updatedDeployment.ID {
		log.Infof("Deployment %s is no longer the in-progress deployment", updatedDeployment.ID)
		return nil, nil
	}

	_, err = d.environment.UpdateDeployment(ctx, *environment, *updatedDeployment)
	if err != nil {
		return nil, errors.Wrapf(err, "Error updating the deployment %v in the environment %v",
			*updatedDeployment, environment.Name)
	}

	return updatedDeployment, nil
}

func (d deploymentWorker) updateDeploymentObject(deployment *types.Deployment,
	resp *ecs.DescribeTasksOutput) (*types.Deployment, error) {

	if d.deploymentCompleted(resp.Tasks, resp.Failures) {
		return deployment.UpdateDeploymentCompleted(resp.Failures)
	}

	updatedDeployment, err := deployment.UpdateDeploymentInProgress(
		deployment.DesiredTaskCount, resp.Failures)
	if err != nil {
		return nil, err
	}

	return updatedDeployment, nil
}

func (d deploymentWorker) deploymentCompleted(tasks []*ecs.Task, failures []*ecs.Failure) bool {
	if len(tasks) == 0 {
		return false
	}

	for _, t := range tasks {
		if aws.StringValue(t.LastStatus) == TaskPending {
			return false
		}
	}

	return true
}
