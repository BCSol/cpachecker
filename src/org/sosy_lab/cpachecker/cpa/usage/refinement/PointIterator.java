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
package org.sosy_lab.cpachecker.cpa.usage.refinement;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.sosy_lab.cpachecker.cpa.usage.storage.AbstractUsagePointSet;
import org.sosy_lab.cpachecker.cpa.usage.storage.UnrefinedUsagePointSet;
import org.sosy_lab.cpachecker.cpa.usage.storage.UnsafeDetector;
import org.sosy_lab.cpachecker.cpa.usage.storage.UsageContainer;
import org.sosy_lab.cpachecker.cpa.usage.storage.UsageInfoSet;
import org.sosy_lab.cpachecker.cpa.usage.storage.UsagePoint;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;


public class PointIterator extends GenericIterator<SingleIdentifier, Pair<UsageInfoSet, UsageInfoSet>>{

  private UsageContainer container;
  private UnsafeDetector detector;

  //Internal state
  private UsagePoint firstPoint;
  private UsagePoint secondPoint;

  private Iterator<UsagePoint> firstPointIterator;
  private Iterator<UsagePoint> secondPointIterator;

  private UnrefinedUsagePointSet currentUsagePointSet;

  private Set<UsagePoint> toRemove = new HashSet<>();

  public PointIterator(ConfigurableRefinementBlock<Pair<UsageInfoSet, UsageInfoSet>> pWrapper) {
    super(pWrapper);
  }

  @Override
  protected void init(SingleIdentifier id) {
    AbstractUsagePointSet pointSet = container.getUsages(id);

    assert (pointSet instanceof UnrefinedUsagePointSet);

    currentUsagePointSet = (UnrefinedUsagePointSet)pointSet;
    firstPointIterator = currentUsagePointSet.getPointIterator();
    secondPointIterator = currentUsagePointSet.getPointIterator();
    firstPoint = firstPointIterator.next();
    secondPoint = secondPointIterator.next();
    assert firstPoint != null;
    assert secondPoint == firstPoint;
    Pair<UsageInfoSet, UsageInfoSet> resultingPair = prepareIterationPair(firstPoint, secondPoint);
    //because the points are equal
    postpone(resultingPair);
  }

  @Override
  protected Pair<UsageInfoSet, UsageInfoSet> getNext(SingleIdentifier pInput) {
    //Sanity check
    AbstractUsagePointSet pointSet = container.getUsages(pInput);
    assert currentUsagePointSet == pointSet;

    do {
      if (!secondPointIterator.hasNext()) {
        if (!firstPointIterator.hasNext()) {
          return null;
        }
        firstPoint = firstPointIterator.next();
        //Start from first point to save the time
        secondPointIterator = currentUsagePointSet.getPointIteratorFrom(firstPoint);
        assert secondPointIterator.hasNext();
      }
      secondPoint = secondPointIterator.next();

      assert firstPoint != null && secondPoint != null;

    } while (!detector.isUnsafePair(firstPoint, secondPoint)
        || toRemove.contains(firstPoint)
        || toRemove.contains(secondPoint));

    Pair<UsageInfoSet, UsageInfoSet> resultingPair = prepareIterationPair(firstPoint, secondPoint);
    if (firstPoint == secondPoint) {
      postpone(resultingPair);
      return getNext(pInput);
    }
    return resultingPair;
  }

  private Pair<UsageInfoSet, UsageInfoSet> prepareIterationPair(UsagePoint first, UsagePoint second) {
    UsageInfoSet firstUsageInfoSet = currentUsagePointSet.getUsageInfo(first);
    UsageInfoSet secondUsageInfoSet = currentUsagePointSet.getUsageInfo(second);

    if (firstUsageInfoSet == secondUsageInfoSet) {
      // To avoid concurrent modification
      secondUsageInfoSet = secondUsageInfoSet.copy();
    }

    assert secondUsageInfoSet != null;
    return Pair.of(firstUsageInfoSet, secondUsageInfoSet);
  }

  @Override
  protected void finishIteration(Pair<UsageInfoSet, UsageInfoSet> pPair, RefinementResult r) {
    UsageInfoSet firstUsageInfoSet = pPair.getFirst();
    UsageInfoSet secondUsageInfoSet = pPair.getSecond();

    if (firstUsageInfoSet.size() == 0) {
      //No reachable usages - remove point
      toRemove.add(firstPoint);
    }
    if (secondUsageInfoSet.size() == 0) {
      //No reachable usages - remove point
      toRemove.add(secondPoint);
    }
  }


  @Override
  protected void handleUpdateSignal(Class<? extends RefinementInterface> pCallerClass, Object pData) {
    if (pCallerClass.equals(IdentifierIterator.class)) {
      assert pData instanceof UsageContainer;
      container = (UsageContainer) pData;
      detector = container.getUnsafeDetector();
    }
  }

  @Override
  protected void handleFinishSignal(Class<? extends RefinementInterface> pCallerClass) {
    if (pCallerClass.equals(IdentifierIterator.class)) {
      toRemove.clear();
      firstPointIterator = null;
      secondPointIterator = null;
      firstPoint = null;
      secondPoint = null;
    }
  }

  @Override
  protected void printDetailedStatistics(StatisticsWriter pOut) {}
}
