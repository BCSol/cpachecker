/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2015  Dirk Beyer
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
package org.sosy_lab.cpachecker.util.mav;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.management.JMException;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.time.TimeSpan;
import org.sosy_lab.cpachecker.core.defaults.AdjustablePrecision;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.automaton.Automaton;
import org.sosy_lab.cpachecker.cpa.automaton.AutomatonInternalState;
import org.sosy_lab.cpachecker.cpa.automaton.AutomatonState;
import org.sosy_lab.cpachecker.cpa.automaton.AutomatonTransition;
import org.sosy_lab.cpachecker.cpa.automaton.ControlAutomatonCPA;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.mav.RuleSpecification.SpecificationStatus;
import org.sosy_lab.cpachecker.util.resources.ProcessCpuTime;

/**
 * Class implements Multi-Aspect Verification.
 */
@Options(prefix="analysis.mav")
public class MultiAspectVerification {

  /**
   * Where precisions will be cleaned:
   * NONE - do not clean;
   * WAITLIST - clean precisions in waitlist;
   * ALL - clean precisions in all abstract states.
   */
  public enum PrecisionCleanSet {NONE, WAITLIST, ALL}

  /**
   * Precision clean strategy:
   * NONE - do not clean;
   * FULL - clean all precisions;
   * BY_SPECIFICATION - clean all precisions, which correspond to current specification.
   */
  public enum PrecisionCleanStrategy {NONE, FULL, BY_SPECIFICATION}

  /**
   * Where specification ids are presented:
   * AUTOMATON - Automaton name (default);
   * VIOLATED_PROPERTY - Violated property description in automaton transitions
   */
  public enum SpecificationComparators {AUTOMATON, VIOLATED_PROPERTY}

  static SpecificationKey FIRST_SPEC = new SpecificationKey("");

  @Option(secure=true, name="stopAfterError",
      description="Stop checking for current specification after the first error was found" +
          "(for Multi-Aspect Verification only)")
  private boolean stopAfterError = true;

  @Option(secure=true, name="basicIntervalTimeLimit",
      description="time limit for all intervals in MAV algorithm")
  private int basicIntervalTimeLimit = 0;

  @Option(secure=true, name="assertTimeLimit",
      description="time limit for each assert in MAV")
  private int assertTimeLimit = 0;

  @Option(secure=true, name="divideTimePerPropertyEqually",
      description="divide all given resources per each property equally in MAV")
  private boolean divideTimePerPropertyEqually = false;

  @Option(secure=true, name="idleIntervalTimeLimit",
      description="time limit for idle intervals in MAV (hard time limit)")
  private int idleIntervalTimeLimit = 0;

  @Option(secure=true, name="firstIntervalTimeLimit",
      description="time limit for idle intervals in MAV (hard time limit)")
  private int firstIntervalTimeLimit = 0;

  // TODO: remove it
  @Deprecated
  @Option(secure=true, name="precisionClearingTimeLimit",
      description="precision clearing time limit")
  private int precisionClearingTimeLimit = 0;

  @Option(secure=true, name="resultsFile",
      description="file for information reuse")
  private String resultsFile = null;

  @Option(secure=true, name="relaunchInOneRun",
      description="Relaunch MAV inside one verification run")
  private boolean relaunchInOneRun = false;

  @Option(secure=true, name="specificationComparator",
      description="comparator for Specification Id. " +
          "Possible values - AUTOMATON (automaton name), VIOLATED_PROPERTY " +
          "(violated property description)")
  private SpecificationComparators specificationComparator = SpecificationComparators.AUTOMATON;

  @Option(secure=true, name="precisionCleanStrategy",
      description="Clean starategy for precisions for specification disable " +
          "(NONE - do not clean precisions, FULL - clean all precisions," +
          "BY_SPECIFICATION - clean all precisions for current specification)")
  private PrecisionCleanStrategy precisionCleanStrategy =
      PrecisionCleanStrategy.BY_SPECIFICATION;

  @Option(secure=true, name="precisionCleanSet",
      description="Specify states set in which precision will be cleaned on specification disable " +
          "(NONE - do not clean at all, WAITLIST - clean precisions in waitlist," +
          "ALL - clean precisions in all abstract states)")
  private PrecisionCleanSet precisionCleanSet =
      PrecisionCleanSet.WAITLIST;

  private Long previousCpuTimeMeasurement; // Internal result for calculating CPU time for each specification.

  private Map<SpecificationKey, RuleSpecification> ruleSpecifications;

  private SpecificationKey lastSpecificationKey;

  private SpecificationKey currentSpecificationKey;
  private ControlAutomatonCPA controlAutomaton;
  private final Configuration config;
  public MultiAspectVerification(Configuration pConfig)
          throws InvalidConfigurationException
  {
    ruleSpecifications = new HashMap<>();
    this.config = pConfig;
    this.config.inject(this);
    lastSpecificationKey = new SpecificationKey("");
    this.controlAutomaton = null;
  }

  public void addNewSpecification(List<Automaton> automata) {
    for (Automaton errorAutomaton : automata) {
      switch (specificationComparator) {
      case AUTOMATON:
        String specificationId = errorAutomaton.getName();
        SpecificationKey specificationKey = new SpecificationKey(specificationId);
        RuleSpecification specification = new RuleSpecification(specificationKey);
        addNewSpecification(specification);
        break;
      case VIOLATED_PROPERTY:
        for (AutomatonInternalState automatonInternalState : errorAutomaton.getStates()) {
          List<AutomatonTransition> automatonTransitionSet = automatonInternalState.getTransitions();
          for (AutomatonTransition automatonTransition : automatonTransitionSet) {
            specificationId = automatonTransition.getName();
            SpecificationKey errorLabel = new SpecificationKey(specificationId);
            if (!ruleSpecifications.containsKey(errorLabel)) {
              specification = new RuleSpecification(errorLabel);
            addNewSpecification(specification);
            }
          }
        }
        break;
      default:
        assert false;
        break;
      }
    }
  }

  private void addNewSpecification(RuleSpecification differentSpecification) {
    ruleSpecifications.put(differentSpecification.getSpecificationKey(), differentSpecification);
  }

  /**
   * Adds Adjustable precision to the current checked specification.
   */
  public void addPrecision(AdjustablePrecision addedPrecision) {
    if (currentSpecificationKey == null)
    {
      // No current specification.
      return;
    }

    // Collect Precisions only if they are needed in selected Precision Clean Strategy.
    if (precisionCleanStrategy == PrecisionCleanStrategy.BY_SPECIFICATION)
    {
      ruleSpecifications.get(currentSpecificationKey).addPrecision(addedPrecision);
    }
  }

  public void changeSpecificationStatus(SpecificationKey targetErrorLabel, SpecificationStatus specificationStatus) {
    if (targetErrorLabel == null)
    {
      return;
    }
    RuleSpecification tmpRuleSpecification = ruleSpecifications.get(targetErrorLabel);
    if (tmpRuleSpecification != null)
    {
      tmpRuleSpecification.setStatus(specificationStatus);
    }
  }

  public boolean checkAssertTimeLimit(long currentSpecificationTime) {
    // Do not check for time limit if is set to 0.
    if (assertTimeLimit <= 0) { return true; }
    if (currentSpecificationTime > assertTimeLimit * 1000) { return false; }
    return true;
  }

  public boolean checkBasicIntervalTimeLimit(long currentAbstractionTime) {
    // Do not check for time limit if is set to 0.
    if (basicIntervalTimeLimit <= 0) { return true; }
    if (currentAbstractionTime > basicIntervalTimeLimit * 1000) { return false; }
    return true;
  }

  public boolean checkIdleIntervalTimeLimit(long currentHardTime) {
    // Do not check for time limit if is set to 0.
    if (idleIntervalTimeLimit <= 0) { return true; }
    if (currentHardTime > idleIntervalTimeLimit * 1000) { return false; }
    return true;
  }

  public boolean checkFirstIntervalTimeLimit(long currentTime) {
    // Do not check for time limit if is set to 0.
    if (firstIntervalTimeLimit <= 0) { return true; }
    if (currentTime > firstIntervalTimeLimit * 1000) { return false; }
    return true;
  }

  public boolean checkPrecisionClearingTimeLimit(long currentTime) {
    // Do not check for time limit if is set to 0.
    if (precisionClearingTimeLimit <= 0) { return true; }
    if (currentTime > precisionClearingTimeLimit * 1000) { return false; }
    return true;
  }

  public boolean cleanPrecision(ReachedSet reachedSet, SpecificationKey specificationKey)
      throws CPAException {

    boolean isTimeout = false;

    if (specificationKey == null || !ruleSpecifications.containsKey(specificationKey)) {
      return false;
    }

    Collection<AbstractState> statesForCleaning = null;
    switch (precisionCleanSet) {
    case NONE:
      return false;
    case WAITLIST:
      statesForCleaning = reachedSet.getWaitlist();
      break;
    case ALL:
      statesForCleaning = reachedSet.asCollection();
      break;
    default:
      assert false;
      return false;
    }

    switch (precisionCleanStrategy) {
    case NONE:
      return false;
    case FULL:
      for (AbstractState state : statesForCleaning)
      {
        Precision currentPrecision = reachedSet.getPrecision(state);
        if (currentPrecision instanceof AdjustablePrecision)
        {
          AdjustablePrecision currentAdjustablePrecision = (AdjustablePrecision) currentPrecision;
          currentAdjustablePrecision.clear();
        }

        // Check PCTL.
        Long currentCpuTime = getCurrentCpuTime();
        if (!checkPrecisionClearingTimeLimit(currentCpuTime))
        {
          return true;
        }
      }
      break;
    case BY_SPECIFICATION:
      Collection<Class<? extends AdjustablePrecision>> precisionTypes =
        ruleSpecifications.get(specificationKey).getPrecisionTypes();
      if (precisionTypes == null || precisionTypes.isEmpty()) {
        // No precision to clean.
        return false;
      }

      for (Class<? extends AdjustablePrecision> precisionType : precisionTypes) {
        AdjustablePrecision precisionForCleaning = getPrecision(specificationKey, precisionType);

        for (AbstractState state : statesForCleaning)
        {
          Precision currentPrecision = reachedSet.getPrecision(state);
          if (currentPrecision instanceof AdjustablePrecision)
          {
            AdjustablePrecision currentAdjustablePrecision = (AdjustablePrecision) currentPrecision;
            isTimeout = currentAdjustablePrecision.subtract(precisionForCleaning);
          }

          // Check PCTL.
          Long currentCpuTime = getCurrentCpuTime();
          if (isTimeout || !checkPrecisionClearingTimeLimit(currentCpuTime))
          {
            setLastCheckedSpecification(null);
            return true;
          }
        }
      }
      break;
    default:
      assert false;
      return false;
    }

    return false;
  }

  public void disableSpecification(ControlAutomatonCPA controlAutomatonCPA,
      SpecificationKey specificationKey) {
    if (specificationKey == null || controlAutomatonCPA == null) {
      // Do nothing.
      return;
    }
    switch (specificationComparator) {
    case AUTOMATON:
      assert Objects.equals(controlAutomatonCPA.getAutomaton().getName(), specificationKey.getId());
      controlAutomatonCPA.disable();
      break;
    case VIOLATED_PROPERTY:
      controlAutomatonCPA.disable(specificationKey.getId());
      break;
    default:
      assert false;
      break;
    }
  }

  public Collection<RuleSpecification> getAllSpecifications() {
    return ruleSpecifications.values();
  }

  public SpecificationComparators getComparator() {
    return specificationComparator;
  }

  public Long getCpuTime(SpecificationKey targetErrorLabel) {
    if (targetErrorLabel == null) {
      return -1L;
    }
    RuleSpecification tmpRuleSpecification = ruleSpecifications.get(targetErrorLabel);
    if (tmpRuleSpecification != null)
    {
      return tmpRuleSpecification.getCpuTime();
    }
    return -1L;
  }

  public ControlAutomatonCPA getCurrentControlAutomaton() {
    return controlAutomaton;
  }

  public Long getCurrentCpuTime() throws CPAException {
    Long currentCpuTime = 0L;
    try {
      Long fullCpuTime = ProcessCpuTime.read();
      currentCpuTime = fullCpuTime - previousCpuTimeMeasurement;
      currentCpuTime = (long) (currentCpuTime / 1e6); // convert to ms
    } catch (JMException e) {
      throw new CPAException(e.getMessage());
    }
    return currentCpuTime;
  }

  public SpecificationKey getCurrentSpecification() {
    return currentSpecificationKey;
  }

  public SpecificationKey getLastCheckedSpecification() {
    return lastSpecificationKey;
  }

  /**
   * Get Precision of selected class.
   */
  public AdjustablePrecision getPrecision(SpecificationKey specificationKey,
      Class<? extends AdjustablePrecision> pPrecisionType) {
    if (specificationKey == null)
    {
      return null;
    }
    RuleSpecification tmpRuleSpecification = ruleSpecifications.get(specificationKey);
    if (tmpRuleSpecification != null)
    {
      return tmpRuleSpecification.getPrecision(pPrecisionType);
    }
    return null;
  }

  /**
   * Create specification key by known target state.
   */
  public SpecificationKey getViolatedSpecification(AutomatonState targetState) {
    SpecificationKey specificationKey = null;
    switch (specificationComparator) {
    case AUTOMATON:
      ControlAutomatonCPA targetAutomaton = targetState.getAutomaton();
      specificationKey = new SpecificationKey(targetAutomaton.getAutomaton().getName());
      break;
    case VIOLATED_PROPERTY:
      specificationKey = new SpecificationKey(targetState.getTransitionName());
      break;
    default:
      assert false;
      break;
    }
    return specificationKey;
  }

  private void incTime(SpecificationKey targetErrorLabel, Long cpuTime) {
    if (targetErrorLabel == null)
    {
      return;
    }
    RuleSpecification tmpRuleSpecification = ruleSpecifications.get(targetErrorLabel);
    if (tmpRuleSpecification != null)
    {
      tmpRuleSpecification.addCpuTime(cpuTime);
    }
  }

  public boolean isRelaunchInOneRun() {
    return relaunchInOneRun;
  }

  public boolean isStopAfterError() {
    return stopAfterError;
  }

  public void printToFile() throws CPAException {
    if (resultsFile == null) {
      // Do not print verdicts in file.
      return;
    }
    try {
      File file = new File(resultsFile);
      try (
          BufferedWriter writer = Files.newBufferedWriter(file.toPath(), Charset.defaultCharset());
          )
      {
        writer.write(toString());
        if (lastSpecificationKey != null) {
          writer.write(lastSpecificationKey.toString()+"\n");
        }
        writer.flush();
      }
    } catch (IOException e) {
      throw new CPAException("Can not write to file MAV results");
    }
  }

  public void setCurrentSpecification(SpecificationKey currentCheckedSpecification, ControlAutomatonCPA automaton) {
    this.currentSpecificationKey = currentCheckedSpecification;
    this.controlAutomaton = automaton;
  }

  public void setLastCheckedSpecification(SpecificationKey pLastCheckedLabel) {
    lastSpecificationKey = pLastCheckedLabel;
  }

  public void startTimers() throws CPAException {
    if (divideTimePerPropertyEqually) {
      @SuppressWarnings("deprecation")
      TimeSpan cpuTimeLimit = TimeSpan.valueOf(config.getProperty("limits.time.cpu"));
      assertTimeLimit = (int) (cpuTimeLimit.asSeconds() / ruleSpecifications.size());
    }
    try {
      previousCpuTimeMeasurement = ProcessCpuTime.read();
    } catch (JMException e) {
      throw new CPAException(e.getMessage());
    }
  }

  public void printResults(PrintStream pOut) {
    for (RuleSpecification differentSpecification : ruleSpecifications.values()) {
      pOut.println(String.format("\tProperty %s: %s", differentSpecification.getSpecificationKey().getId(), differentSpecification.getVerdict()));
    }
  }

  @Override
  public String toString() {
    String result = "";
    for (RuleSpecification differentSpecification : ruleSpecifications.values()) {
      result = result + differentSpecification.toString() + "\n";
    }
    return result;
  }

  public void updateTime(SpecificationKey targetErrorLabel) throws CPAException {
    // Calculate current time.
    Long currentCpuTime = 0L;
    try {
      Long fullCpuTime = ProcessCpuTime.read();
      currentCpuTime = fullCpuTime - previousCpuTimeMeasurement;
      previousCpuTimeMeasurement = fullCpuTime;
      currentCpuTime = (long) (currentCpuTime / 1e6); // convert to ms
    } catch (JMException e) {
      throw new CPAException(e.getMessage());
    }

    // Update current time.
    incTime(targetErrorLabel, currentCpuTime);
  }

}
