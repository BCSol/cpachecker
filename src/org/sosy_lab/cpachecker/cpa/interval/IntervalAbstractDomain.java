/*
 * CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2017  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.interval;

import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.exceptions.CPAException;

/**
 * Abstract domain for the interval analysis.
 */
public class IntervalAbstractDomain implements AbstractDomain {

  private final int threshold;

  public IntervalAbstractDomain(int pThreshold) {
    threshold = pThreshold;
  }

  @Override
  public AbstractState join(
      AbstractState newState, AbstractState reached) throws CPAException, InterruptedException {
    IntervalAnalysisState iNewState = (IntervalAnalysisState) newState;
    IntervalAnalysisState iReachedState = (IntervalAnalysisState) reached;
    return iNewState.join(iReachedState, threshold);
  }

  @Override
  public boolean isLessOrEqual(
      AbstractState state1, AbstractState state2) throws CPAException, InterruptedException {
    IntervalAnalysisState iState1 = (IntervalAnalysisState) state1;
    IntervalAnalysisState iState2 = (IntervalAnalysisState) state2;
    return iState1.isLessOrEqual(iState2);
  }
}