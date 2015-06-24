/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.hadoop.mapred.workflow;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;

/**
 * Describes the current status of a workflow.
 */
public class WorkflowStatus implements Writable {

  public enum RunState {
    PREP, SUBMITTED, RUNNING, SUCCEEDED, FAILED, KILLED
  };

  // Variables
  private WorkflowID workflowId;
  private RunState runState = RunState.PREP;
  private String failureInfo = "NA";
  private long submissionTime = -1;

  private Collection<String> prepJobs;
  private Collection<String> submittedJobs;
  private Collection<String> runningJobs;
  private Collection<String> finishedJobs;  // Equal to succeeded jobs.

  // Required for readFields()/reflection when building the object.
  public WorkflowStatus() {
    prepJobs = new HashSet<String>();
    submittedJobs = new HashSet<String>();
    runningJobs = new HashSet<String>();
    finishedJobs = new HashSet<String>();
  }

  public WorkflowStatus(WorkflowID workflowId) {
    this();
    this.workflowId = workflowId;
  }

  // TODO: failed jobs set..
  // TODO: job submitted for a workflow which actually doesn't contain it..
  // TODO: for all of them.. what if job is in the wrong state?
  // TODO: --> pass in workflowConf on construction, use it to block adding of
  // jobs which aren't in the workflow configuration?
  // TODO: any need to be synchronized, and with what lock?

  /**
   * Add a job to the list of jobs which have not yet been submitted.
   *
   * @param jobName A string representing the name of the job to add.
   */
  public void addPrepJob(String jobName) {
    prepJobs.add(jobName);
  }

  /**
   * Add a job to the list of jobs which have been submitted but are not yet
   * running.
   *
   * @param jobName A string representing the name of the job to add.
   */
  public void addSubmittedJob(String jobName) {
    prepJobs.remove(jobName);
    submittedJobs.add(jobName);

    if (runState == RunState.PREP) {
      runState = RunState.SUBMITTED;
    }
  }

  /** Add a job to the list of jobs which are running but are not yet finished.
   *
   * @param jobName A string representing the name of the job to add.
   */
  public void addRunningJob(String jobName) {
    submittedJobs.remove(jobName);
    runningJobs.add(jobName);

    if (runState == RunState.SUBMITTED) {
      runState = RunState.RUNNING;
    }
  }

  /**
   * Add a job to the list of jobs which have finished running (successfully).
   *
   * @param jobName A string representing the name of the job to add.
   */
  public void addFinishedJob(String jobName) {
    runningJobs.remove(jobName);
    finishedJobs.add(jobName);

    if (isFinished()) {
      runState = RunState.SUCCEEDED;
    }
  }

  /**
   * Return a collection of jobs which are in the preparation stage.
   *
   * @return A collection of jobs, identified by their job name.
   */
  public Collection<String> getPrepJobs() {
    return new HashSet<String>(prepJobs);
  }

  /**
   * Return a collection of jobs which have been submitted.
   *
   * @return A collection of jobs, identified by their job name.
   */
  public Collection<String> getSubmittedJobs() {
    return new HashSet<String>(submittedJobs);
  }

  /**
   * Return a collection of jobs which are currently running.
   *
   * @return A collection of jobs, identified by their job name.
   */
  public Collection<String> getRunningJobs() {
    return new HashSet<String>(runningJobs);
  }

  /**
   * Return a collection of finished jobs.
   *
   * @return A collection of finished jobs, identified by their job name.
   */
  public Collection<String> getFinishedJobs() {
    return new HashSet<String>(finishedJobs);
  }

  /**
   * Report whether the workflow is finished.
   *
   * @return True if the workflow is finished, false otherwise.
   */
  public boolean isFinished() {
    return prepJobs.isEmpty() && submittedJobs.isEmpty()
        && runningJobs.isEmpty();
  }

  /**
   * Get the run state of the workflow.
   *
   * @return The current run state of the workflow.
   */
  public synchronized RunState getRunState() {
    return runState;
  }

  /**
   * Get the submission time of the workflow.
   *
   * @return The submission time of the workflow.
   */
  public synchronized long getSubmissionTime() {
    return submissionTime;
  }

  /**
   * Set the submission time of the workflow.
   *
   * @param submissionTime The submission time to be set.
   */
  public synchronized void setSubmissionTime(long submissionTime) {
    if (runState == RunState.PREP) {
      this.submissionTime = submissionTime;
      runState = RunState.SUBMITTED;
    }
  }

  /**
   * Gets any available information regarding the reason of job failure.
   *
   * @return A string containing diagnostic information on job failure.
   */
  public synchronized String getFailureInfo() {
    // TODO: ? call to get failure info of current job.
    return failureInfo;
  }

  /**
   * Set the information regarding the reason of job failure.
   *
   * @param failureInfo A string containing diagnostic information on job
   *          failure.
   */
  public synchronized void setFailureInfo(String failureInfo) {
    this.failureInfo = failureInfo;
  }

  /**
   * Return the {@link WorkflowID} of the workflow.
   */
  public WorkflowID getWorkflowId() {
    return workflowId;
  }

  @Override
  public void write(DataOutput out) throws IOException {
    workflowId.write(out);
    Text.writeString(out, failureInfo);
    out.writeLong(submissionTime);

    writeJobSet(out, prepJobs);
    writeJobSet(out, submittedJobs);
    writeJobSet(out, runningJobs);
    writeJobSet(out, finishedJobs);
  }

  private void writeJobSet(DataOutput out, Collection<String> jobs)
      throws IOException {
    out.writeInt(jobs.size());
    for (String job : jobs) {
      Text.writeString(out, job);
    }
  }

  @Override
  public void readFields(DataInput in) throws IOException {
    workflowId = new WorkflowID();
    workflowId.readFields(in);
    failureInfo = Text.readString(in);
    submissionTime = in.readLong();

    readJobSet(in, prepJobs);
    readJobSet(in, submittedJobs);
    readJobSet(in, runningJobs);
    readJobSet(in, finishedJobs);
  }

  private void readJobSet(DataInput in, Collection<String> jobs)
      throws IOException {
    for (int numJobs = in.readInt(); numJobs > 0; numJobs--) {
      jobs.add(Text.readString(in));
    }
  }

}