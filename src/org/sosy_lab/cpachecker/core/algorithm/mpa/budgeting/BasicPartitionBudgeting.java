/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2016  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.core.algorithm.mpa.budgeting;

import com.google.common.base.Optional;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.configuration.TimeSpanOption;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.TimeSpan;

import java.util.concurrent.TimeUnit;

@Options(prefix="analysis.mpa.partition")
public class BasicPartitionBudgeting implements PartitionBudgeting {

  @Option(secure=true, name="time.wall",
      description="Limit for wall time of one partition (use seconds or specify a unit; -1 for infinite)")
  @TimeSpanOption(codeUnit=TimeUnit.NANOSECONDS,
      defaultUserUnit=TimeUnit.SECONDS,
      min=-1)
  protected TimeSpan wallTime = TimeSpan.ofNanos(-1);

  @Option(secure=true, name="time.cpu",
      description="Limit for cpu time of one partition (use seconds or specify a unit; -1 for infinite)")
  @TimeSpanOption(codeUnit=TimeUnit.NANOSECONDS,
      defaultUserUnit=TimeUnit.SECONDS,
      min=-1)
  protected TimeSpan cpuTime = TimeSpan.ofNanos(-1);

  @Option(secure=true, name="property.time.wall",
      description="Limit for wall time of one property (use seconds or specify a unit; -1 for infinite)")
  @TimeSpanOption(codeUnit=TimeUnit.NANOSECONDS,
      defaultUserUnit=TimeUnit.SECONDS,
      min=-1)
  protected TimeSpan propertyWallTime = TimeSpan.ofNanos(-1);

  @Option(secure=true, name="property.time.cpu",
      description="Limit for cpu time of one property (use seconds or specify a unit; -1 for infinite)")
  @TimeSpanOption(codeUnit=TimeUnit.NANOSECONDS,
      defaultUserUnit=TimeUnit.SECONDS,
      min=-1)
  protected TimeSpan propertyCpuTime = TimeSpan.ofNanos(-1);

  protected final int budgetFactor;

  protected final LogManager logger;

  public BasicPartitionBudgeting(Configuration pConfig, LogManager pLogger)
      throws InvalidConfigurationException {
    pConfig.inject(this);
    logger = pLogger;
    budgetFactor = 1;
  }

  public BasicPartitionBudgeting(LogManager pLogger, TimeSpan pCpuTime, TimeSpan pWallTime, int pBudgetFactor) {
    logger = pLogger;
    cpuTime = pCpuTime;
    wallTime = pWallTime;
    budgetFactor = pBudgetFactor;
  }

  @Override
  public PartitionBudgeting getBudgetTimesTwo() {
    return new BasicPartitionBudgeting(logger, cpuTime, wallTime, budgetFactor * 2);
  }

  @Override
  public Optional<TimeSpan> getPartitionWallTimeLimit(int pForNumberOfProperties) {
    if (pForNumberOfProperties == 1
        && propertyWallTime.compareTo(TimeSpan.empty()) >= 0) {
      return Optional.of(propertyWallTime.multiply(budgetFactor));
    }

    if (wallTime.compareTo(TimeSpan.empty()) >= 0) {
      return Optional.of(wallTime.multiply(budgetFactor));
    }

    return Optional.absent();
  }

  @Override
  public Optional<TimeSpan> getPartitionCpuTimeLimit(int pForNumberOfProperties) {
    if (pForNumberOfProperties == 1
        && propertyCpuTime.compareTo(TimeSpan.empty()) >= 0) {
      return Optional.of(propertyCpuTime.multiply(budgetFactor));
    }

    if (cpuTime.compareTo(TimeSpan.empty()) >= 0) {
      return Optional.of(cpuTime.multiply(budgetFactor));
    }

    return Optional.absent();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();

    Optional<TimeSpan> ctl = getPartitionCpuTimeLimit(1);
    Optional<TimeSpan> wtl = getPartitionWallTimeLimit(1);

    if (ctl.isPresent()) {
      sb.append(String.format("CPU time: %s; ", ctl.toString()));
    }

    if (wtl.isPresent()) {
      sb.append(String.format("Wall time: %s; ", wtl.toString()));
    }

    return sb.toString();
  }

}
