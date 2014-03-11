/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2013  Dirk Beyer
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
package org.sosy_lab.cpachecker.core.algorithm.testgen.old;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.Pair;
import org.sosy_lab.common.Triple;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.CoreComponentsFactory;
import org.sosy_lab.cpachecker.core.ShutdownNotifier;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.AbstractStates;

import com.google.common.base.Function;


public class ARGStateDummyCreator {

  private final TransferRelation transferRelation;
  private final Precision prec;
  private CFA cfa;
//  private final static ImmutableList<PrecisionAdjustment> precs = ImmutableList.of();
//  private ARGPrecisionAdjustment precision;

  @Deprecated
  public ARGStateDummyCreator(CFA pCfa, Configuration config, LogManager pLogger) throws InvalidConfigurationException, CPAException {
    cfa = pCfa;
    ShutdownNotifier notifier = ShutdownNotifier.create();
    ConfigurableProgramAnalysis cpa = new CoreComponentsFactory(config, pLogger,notifier).createCPA(pCfa, null);
    transferRelation = cpa.getTransferRelation();
    prec = cpa.getInitialPrecision(cfa.getMainFunction());
    //transferRelation = new ARGTransferRelation(new ExplicitTransferRelation(config, pLogger, pCfa));
//    precision = new ARGPrecisionAdjustment(
//        new ComponentAwareExplicitPrecisionAdjustment(precs, config, pCfa),false);
  }
  public ARGStateDummyCreator(/*CFA pCfa,*/ ConfigurableProgramAnalysis pCpa, LogManager pLogger) throws InvalidConfigurationException, CPAException {
//    cfa = pCfa;
    transferRelation = pCpa.getTransferRelation();
    prec = pCpa.getInitialPrecision(cfa.getMainFunction());
  }

  /**
   *
   * @param pState the parent state for which to compute the successors
   * @param pNotToChildState a successor of pState that should not be visited.
   * @return
   * @throws CPATransferException
   * @throws InterruptedException
   */
  public ARGState computeOtherSuccessor(ARGState pState, ARGState pNotToChildState) throws CPATransferException, InterruptedException{
    CFAEdge edgeToWrongChild = pState.getEdgeToChild(pNotToChildState);
    CFAEdge correctEdge = null;
    CFANode location = AbstractStates.extractLocation(pState);
    for (int i = 0; i < location.getNumLeavingEdges(); i++) {
      CFAEdge edge = location.getLeavingEdge(i);
      if(edge.equals(edgeToWrongChild)) {
        continue;
      } else {
        correctEdge = edge;
      }

    }
    if(location.getNumLeavingEdges() > 2) {
      throw new IllegalStateException("Node has more than two leaving edges, which is unsupported.");
    }
    Collection<? extends AbstractState> successors = transferRelation.getAbstractSuccessors(pState, prec, null);
    ARGState newState = null;
    for (AbstractState argState : successors) {
      CFANode l = AbstractStates.extractLocation(argState);
      if(location.getEdgeTo(l).equals(edgeToWrongChild)) {
        continue;
      } else {
        newState = (ARGState)argState;
      }
    }

    return newState;

  }

  /**
   * @param pReached The ReachedSet to work on
   * @param pIsTraget Function that identifies the target State to switch
   * @return Triple(the new path, the root ARGState, the target ARGState identified by pIsTarget)
   */
  public Triple<Set<ARGState>, ARGState, ARGState> produceOtherPath(ReachedSet pReached,
      Function<ARGState, Boolean> pIsTarget) throws CPATransferException, InterruptedException {
    ARGState lastState = (ARGState) pReached.getLastState();
    ARGPath path = ARGUtils.getOnePathTo(lastState);

    ARGState targetState = null;
    ARGState targetSuccessorInPath = null;

    Set<ARGState> newPath = new HashSet<>();

    for (Pair<ARGState, CFAEdge> stateEdgePair : path) {
      ARGState curState = stateEdgePair.getFirst();
      if (targetState != null) {
        targetSuccessorInPath = curState;
        break;
      } else if (pIsTarget.apply(curState)) {
        targetState = curState;
      }

      newPath.add(curState);
    }

    assert targetState != null : "Target State not found";
    assert targetSuccessorInPath != null : "Target State found but has no successor in path";

    ARGState targetOtherSuccessor = computeOtherSuccessor(targetState, targetSuccessorInPath);
    targetSuccessorInPath.removeFromARG();
    targetOtherSuccessor.addParent(targetState);
    newPath.add(targetOtherSuccessor);

    return Triple.of(newPath, path.getFirst().getFirst(), targetState);
  }


}
