/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
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
package org.sosy_lab.cpachecker.core;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import org.sosy_lab.cpachecker.core.algorithm.AlgorithmResult;
import org.sosy_lab.cpachecker.core.interfaces.Property;
import org.sosy_lab.cpachecker.core.interfaces.PropertySummary;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;

import java.io.PrintStream;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import java.io.PrintStream;

import javax.annotation.Nullable;

/**
 * Class that represents the result of a CPAchecker analysis.
 */
public class CPAcheckerResult {

  /**
   * Enum for the possible outcomes of a CPAchecker analysis:
   * - UNKNOWN: analysis did not terminate
   * - FALSE: bug found
   * - TRUE: no bug found
   */
  public static enum Result { NOT_YET_STARTED, UNKNOWN, FALSE, TRUE }

  private final Result result;
  private final AlgorithmResult algorithmResult;

  private final PropertySummary propertySummary;

  private final @Nullable ReachedSet reached;

  private final @Nullable CFA cfa;

  private final @Nullable Statistics stats;

  private @Nullable Statistics proofGeneratorStats = null;

  CPAcheckerResult(Result pResult,
        PropertySummary pSummary,
        @Nullable AlgorithmResult pAlgResult,
        @Nullable ReachedSet reached,
        @Nullable CFA cfa,
        @Nullable Statistics stats) {

    this.propertySummary = checkNotNull(pSummary);
    this.result = checkNotNull(pResult);
    this.algorithmResult = pAlgResult;
    this.reached = reached;
    this.cfa = cfa;
    this.stats = stats;
  }

  /**
   * Return the result of the analysis.
   */
  public Result getResult() {
    return result;
  }

  public AlgorithmResult getAlgorithmResult() {
    return algorithmResult;
  }

  /**
   * Return the final reached set.
   */
  public UnmodifiableReachedSet getReached() {
    return reached;
  }

  /**
   * Return the CFA.
   */
  public CFA getCfa() {
    return cfa;
  }

  public void addProofGeneratorStatistics(Statistics pProofGeneratorStatistics) {
    proofGeneratorStats = pProofGeneratorStatistics;
  }

  /**
   * Write the statistics to a given PrintWriter. Additionally some output files
   * may be written here, if configuration says so.
   */
  public void printStatistics(PrintStream target) {
    if (stats != null) {
      stats.printStatistics(target, result, reached);
    }
    if (proofGeneratorStats != null) {
      proofGeneratorStats.printStatistics(target, result, reached);
    }
  }

  private Collection<Property> sortPropertiesByStrings(Set<Property> pProps) {
    List<Property> result = Lists.newArrayList(pProps);
    Collections.sort(result, new java.util.Comparator<Property>() {
      @Override
      public int compare(Property pO1, Property pO2) {
        return pO1.toString().compareTo(pO2.toString());
      }
    });

    return result;
  }

  public void printResult(PrintStream out) {
    if (result == Result.NOT_YET_STARTED) {
      return;
    }

    out.println("Verification result: " + getResultString());

    out.println(String.format("\tNumber of considered properties: %d", propertySummary.getConsideredProperties().size()));
    out.println(String.format("\tNumber of violated properties: %d", propertySummary.getViolatedProperties().size()));

    out.println(String.format("\tNumber of conditional violated properties: %d", propertySummary.getConditionalViolatedProperties().keySet().size()));

    if (propertySummary.getUnknownProperties().isPresent()) {
      out.println(String.format("\tNumber of unknown properties: %d", propertySummary.getUnknownProperties().get().size()));
    }

    if (propertySummary.getSatisfiedProperties().isPresent()) {
      out.println(String.format("\tNumber of satisfied properties: %d", propertySummary.getSatisfiedProperties().get().size()));
    }

    if (propertySummary.getRelevantProperties().isPresent()) {
      out.println(String.format("\tNumber of relevant properties: %d", propertySummary.getRelevantProperties().get().size()));
    }

    out.println("\tStatus by property:");

    for (Property prop: sortPropertiesByStrings(propertySummary.getViolatedProperties())) {
      out.println(String.format("\t\tProperty %s: %s", prop.toString(), verdictWithRelevance(prop, "FALSE", propertySummary)));
    }

    if (propertySummary.getUnknownProperties().isPresent()) {
      for (Property prop: sortPropertiesByStrings(propertySummary.getUnknownProperties().get())) {
        out.println(String.format("\t\tProperty %s: %s", prop.toString(), verdictWithRelevance(prop, "UNKNOWN", propertySummary)));
      }
    }

    if (propertySummary.getSatisfiedProperties().isPresent()) {
      for (Property prop: sortPropertiesByStrings(propertySummary.getSatisfiedProperties().get())) {
        out.println(String.format("\t\tProperty %s: %s", prop.toString(), verdictWithRelevance(prop, "TRUE", propertySummary)));
      }
    }

  }

  private String verdictWithRelevance(Property pProp, String pString, PropertySummary pPropertySummary) {
    if (pPropertySummary.getRelevantProperties().isPresent()) {
      boolean relevant = pPropertySummary.getRelevantProperties().get().contains(pProp);
      if (!relevant) {
        return String.format("%s (irrelevant)", pString);
      }
    }
    return pString;
  }

  private String getResultString() {
    StringBuilder ret = new StringBuilder();

    switch (result) {
      case UNKNOWN:
        ret.append("UNKNOWN, incomplete analysis.");
        break;
      case FALSE:
        ret.append("FALSE. Property violation");
        break;
      case TRUE:
        ret.append("TRUE. No property violation found by chosen configuration.");
        break;
      default:
        ret.append("UNKNOWN result: " + result);
    }

    if (!propertySummary.getViolatedProperties().isEmpty()) {
      ret.append(" (").append(Joiner.on(", ").join(propertySummary.getViolatedProperties())).append(")");
      ret.append(" found by chosen configuration.");
    }

    return ret.toString();
  }
}
