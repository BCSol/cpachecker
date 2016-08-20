/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2011  Dirk Beyer
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
package org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.translators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.ECPAtom;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.ECPConcatenation;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.ECPEdgeSet;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.ECPGuard;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.ECPNodeSet;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.ECPPredicate;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.ECPRepetition;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.ECPUnion;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.ECPVisitor;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.ElementaryCoveragePattern;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.StandardECPEdgeSet;
import org.sosy_lab.cpachecker.util.automaton.NFA;
import org.sosy_lab.cpachecker.util.automaton.NFA.State;

public class ToGuardedAutomatonTranslator {

  private static String stutterEdgeLabelPattern = "Set: [0-9]* Guard: \\[\\]";
  private static String notMainLabelPattern = "!Set: line [0-9]*:\tN[0-9]* -\\{main\\(\\)\\}-> N[0-9]* Guard: \\[\\]";

  public static NFA<GuardedEdgeLabel> toAutomaton(ElementaryCoveragePattern pPattern, GuardedEdgeLabel pAlphaLabel, GuardedEdgeLabel pInverseAlphaLabel, GuardedLabel pOmegaLabel, boolean pUseOmegaLabel) {
    NFA<GuardedLabel> lAutomaton1 = translate(pPattern);

    NFA<GuardedLabel>
        lAutomaton2 = removeLambdaEdges(lAutomaton1, pAlphaLabel, pOmegaLabel, pUseOmegaLabel);

    NFA<GuardedEdgeLabel> lAutomaton3 = removeNodeSetGuards(lAutomaton2);

    lAutomaton3.createEdge(lAutomaton3.getInitialState(), lAutomaton3.getInitialState(), pInverseAlphaLabel);

    return lAutomaton3;
  }

  public static NFA<GuardedLabel> translate(ElementaryCoveragePattern pPattern) {
    Visitor lVisitor = new Visitor();

    pPattern.accept(lVisitor);

    lVisitor.getAutomaton().addToFinalStates(lVisitor.getFinalState());

    return lVisitor.getAutomaton();
  }

  public static NFA<GuardedLabel> removeLambdaEdges(NFA<GuardedLabel> pAutomaton, GuardedEdgeLabel pAlphaLabel, GuardedLabel pOmegaLabel, boolean pUseOmegaLabel) {
    /** first we augment the given automaton with the alpha and omega edge */
    // TODO move into separate (private) method
    NFA.State lNewInitialState = pAutomaton.createState();
    NFA<GuardedLabel>.Edge lInitialEdge = pAutomaton.createEdge(lNewInitialState, pAutomaton.getInitialState(), pAlphaLabel);
    pAutomaton.setInitialState(lNewInitialState);

    if (pUseOmegaLabel) {
      NFA.State lNewFinalState = pAutomaton.createState();

      for (NFA.State lFinalState : pAutomaton.getFinalStates()) {
        pAutomaton.createEdge(lFinalState, lNewFinalState, pOmegaLabel);
      }

      pAutomaton.setFinalStates(Collections.singleton(lNewFinalState));
    }

    /** now we remove guarded lambda edges */

    NFA<GuardedLabel> lAutomaton = new NFA<>();
    Map<NFA.State, NFA.State> lStateMap = new HashMap<>();
    lStateMap.put(lNewInitialState, lAutomaton.getInitialState());

    List<NFA<GuardedLabel>.Edge> lWorklist = new LinkedList<>();
    lWorklist.add(lInitialEdge);

    Set<NFA<GuardedLabel>.Edge> lReachedEdges = new HashSet<>();

    while (!lWorklist.isEmpty()) {
      NFA<GuardedLabel>.Edge lCurrentEdge = lWorklist.remove(0);

      if (lReachedEdges.contains(lCurrentEdge)) {
        continue;
      }

      lReachedEdges.add(lCurrentEdge);

      GuardedState lInitialGuardedState = new GuardedState(lCurrentEdge.getTarget(), lCurrentEdge.getLabel().getGuards());

      /** determine the lambda successors */
      // TODO refactor into distinguished method
      List<GuardedState> lStatesWorklist = new LinkedList<>();
      lStatesWorklist.add(lInitialGuardedState);

      Set<GuardedState> lReachedStates = new HashSet<>();

      while (!lStatesWorklist.isEmpty()) {
        GuardedState lCurrentState = lStatesWorklist.remove(0);

        boolean lIsCovered = false;

        for (GuardedState lGuardedState : lReachedStates) {
          if (lGuardedState.covers(lCurrentState)) {
            lIsCovered = true;

            break;
          }
        }

        if (lIsCovered) {
          continue;
        }

        lReachedStates.add(lCurrentState);

        for (NFA<GuardedLabel>.Edge lOutgoingEdge : pAutomaton.getOutgoingEdges(lCurrentState.getState())) {
          if (lOutgoingEdge.getLabel() instanceof GuardedLambdaLabel) {
            GuardedState lNewState = new GuardedState(lOutgoingEdge.getTarget(), lCurrentState, lOutgoingEdge.getLabel().getGuards());
            lStatesWorklist.add(lNewState);
          }
        }
      }

      NFA.State lOldSource = lCurrentEdge.getSource();

      if (!lStateMap.containsKey(lOldSource)) {
        lStateMap.put(lOldSource, lAutomaton.createState());
      }

      NFA.State lSource = lStateMap.get(lOldSource);

      GuardedLabel lCurrentLabel = lCurrentEdge.getLabel();

      GuardedEdgeLabel lEdgeLabel = (GuardedEdgeLabel)lCurrentLabel;

      ECPEdgeSet lCurrentEdgeSet = lEdgeLabel.getEdgeSet();

      for (GuardedState lReachedState : lReachedStates) {
        boolean lHasNonLambdaEdge = false;

        // TODO create variable for lReachedState.getState()

        for (NFA<GuardedLabel>.Edge lOutgoingEdge : pAutomaton.getOutgoingEdges(lReachedState.getState())) {
          if (!(lOutgoingEdge.getLabel() instanceof GuardedLambdaLabel)) {
            lHasNonLambdaEdge = true;

            lWorklist.add(lOutgoingEdge);
          }
        }

        // final state has no outgoing edges
        if (pAutomaton.getOutgoingEdges(lReachedState.getState()).isEmpty()) {
          lHasNonLambdaEdge = true;
        }

        if (lHasNonLambdaEdge) {
          NFA.State lOldTarget = lReachedState.getState();

          if (!lStateMap.containsKey(lOldTarget)) {
            lStateMap.put(lOldTarget, lAutomaton.createState());
          }

          NFA.State lTarget = lStateMap.get(lOldTarget);

          lAutomaton.createEdge(lSource, lTarget, new GuardedEdgeLabel(lCurrentEdgeSet, lReachedState.getGuards()));
        }
      }
    }

    for (NFA.State lFinalState : pAutomaton.getFinalStates()) {
      lAutomaton.addToFinalStates(lStateMap.get(lFinalState));
    }

    return lAutomaton;
  }

  /**
   * @param pAutomaton Automaton that contains no lambda edges.
   * @return Automaton that is only labeled with GuardedEdgeLabel objects.
   */
  public static NFA<GuardedEdgeLabel> removeNodeSetGuards(NFA<GuardedLabel> pAutomaton) {
    NFA<GuardedEdgeLabel> lAutomaton = new NFA<>();

    Map<NFA.State, NFA.State> lStateMap = new HashMap<>();
    lStateMap.put(pAutomaton.getInitialState(), lAutomaton.getInitialState());

    List<NFA<GuardedLabel>.Edge> lWorklist = new LinkedList<>();
    lWorklist.addAll(pAutomaton.getOutgoingEdges(pAutomaton.getInitialState()));

    Set<NFA<GuardedLabel>.Edge> lReachedEdges = new HashSet<>();

    while (!lWorklist.isEmpty()) {
      NFA<GuardedLabel>.Edge lCurrentEdge = lWorklist.remove(0);

      if (lReachedEdges.contains(lCurrentEdge)) {
        continue;
      }

      lReachedEdges.add(lCurrentEdge);

      GuardedLabel lLabel = lCurrentEdge.getLabel();

      if (lLabel.hasGuards()) {
        ECPNodeSet lNodeSet = null;

        Set<ECPGuard> lRemainingGuards = new HashSet<>();

        for (ECPGuard lGuard : lLabel.getGuards()) {
          if (lGuard instanceof ECPNodeSet) {
            if (lNodeSet == null) {
              lNodeSet = (ECPNodeSet)lGuard;
            }
            else {
              lNodeSet = lNodeSet.intersect((ECPNodeSet)lGuard);
            }
          }
          else {
            lRemainingGuards.add(lGuard);
          }
        }

        if (lNodeSet != null) {
          // TODO move this condition upwards
          if (!lNodeSet.isEmpty()) {
            assert(lLabel instanceof GuardedEdgeLabel);

            GuardedEdgeLabel lEdgeLabel = (GuardedEdgeLabel)lLabel;

            ECPEdgeSet lCurrentEdgeSet = lEdgeLabel.getEdgeSet();

            Set<CFAEdge> lRemainingCFAEdges = new HashSet<>();

            for (CFAEdge lCFAEdge : lCurrentEdgeSet) {
              if (lNodeSet.contains(lCFAEdge.getSuccessor())) {
                lRemainingCFAEdges.add(lCFAEdge);
              }
            }

            if (!lRemainingCFAEdges.isEmpty()) {
              //ECPEdgeSet lNewEdgeSet = new ECPEdgeSet(lRemainingCFAEdges);
              ECPEdgeSet lNewEdgeSet = StandardECPEdgeSet.create(lRemainingCFAEdges);

              GuardedEdgeLabel lNewGuard = new GuardedEdgeLabel(lNewEdgeSet, lRemainingGuards);

              // add edge

              NFA.State lCurrentSource = lCurrentEdge.getSource();
              NFA.State lCurrentTarget = lCurrentEdge.getTarget();

              if (!lStateMap.containsKey(lCurrentSource)) {
                lStateMap.put(lCurrentSource, lAutomaton.createState());
              }

              if (!lStateMap.containsKey(lCurrentTarget)) {
                lStateMap.put(lCurrentTarget, lAutomaton.createState());
              }

              NFA.State lSourceState = lStateMap.get(lCurrentSource);
              NFA.State lTargetState = lStateMap.get(lCurrentTarget);

              lAutomaton.createEdge(lSourceState, lTargetState, lNewGuard);
            }
          }
        }
        else {
          assert(lLabel instanceof GuardedEdgeLabel);

          GuardedEdgeLabel lEdgeLabel = (GuardedEdgeLabel)lLabel;

          if (!lEdgeLabel.getEdgeSet().isEmpty()) {
            // add edge
            NFA.State lCurrentSource = lCurrentEdge.getSource();
            NFA.State lCurrentTarget = lCurrentEdge.getTarget();

            if (!lStateMap.containsKey(lCurrentSource)) {
              lStateMap.put(lCurrentSource, lAutomaton.createState());
            }

            if (!lStateMap.containsKey(lCurrentTarget)) {
              lStateMap.put(lCurrentTarget, lAutomaton.createState());
            }

            NFA.State lSourceState = lStateMap.get(lCurrentSource);
            NFA.State lTargetState = lStateMap.get(lCurrentTarget);

            lAutomaton.createEdge(lSourceState, lTargetState, lEdgeLabel);
          }
        }
      }
      else {
        assert(lLabel instanceof GuardedEdgeLabel);

        GuardedEdgeLabel lEdgeLabel = (GuardedEdgeLabel)lLabel;

        if (!lEdgeLabel.getEdgeSet().isEmpty()) {
          // add edge
          NFA.State lCurrentSource = lCurrentEdge.getSource();
          NFA.State lCurrentTarget = lCurrentEdge.getTarget();

          if (!lStateMap.containsKey(lCurrentSource)) {
            lStateMap.put(lCurrentSource, lAutomaton.createState());
          }

          if (!lStateMap.containsKey(lCurrentTarget)) {
            lStateMap.put(lCurrentTarget, lAutomaton.createState());
          }

          NFA.State lSourceState = lStateMap.get(lCurrentSource);
          NFA.State lTargetState = lStateMap.get(lCurrentTarget);

          lAutomaton.createEdge(lSourceState, lTargetState, lEdgeLabel);
        }
      }

      lWorklist.addAll(pAutomaton.getOutgoingEdges(lCurrentEdge.getTarget()));
    }

    for (NFA.State lFinalState : pAutomaton.getFinalStates()) {
      if (lStateMap.containsKey(lFinalState)) {
        lAutomaton.addToFinalStates(lStateMap.get(lFinalState));
      }
    }

    return lAutomaton;
  }

  private static class Visitor implements ECPVisitor<Void> {

    private static final Map<ECPAtom, GuardedLabel> sLabelCache = new HashMap<>();

    private NFA<GuardedLabel> mAutomaton;

    private NFA.State mInitialState;
    private NFA.State mFinalState;

    public Visitor(NFA<GuardedLabel> pAutomaton) {
      mAutomaton = pAutomaton;
      setInitialState(mAutomaton.getInitialState());
      setFinalState(mAutomaton.createState());
    }

    public Visitor() {
      this(new NFA<GuardedLabel>());
    }

    public NFA<GuardedLabel> getAutomaton() {
      return mAutomaton;
    }

    public NFA.State getInitialState() {
      return mInitialState;
    }

    public NFA.State getFinalState() {
      return mFinalState;
    }

    public void setInitialState(NFA.State pInitialState) {
      mInitialState = pInitialState;
    }

    public void setFinalState(NFA.State pFinalState) {
      mFinalState = pFinalState;
    }

    @Override
    public Void visit(ECPEdgeSet pEdgeSet) {
      GuardedLabel lLabel = sLabelCache.get(pEdgeSet);

      if (lLabel == null) {
        lLabel = new GuardedEdgeLabel(pEdgeSet);
        sLabelCache.put(pEdgeSet, lLabel);
      }

      mAutomaton.createEdge(getInitialState(), getFinalState(), lLabel);

      return null;
    }

    @Override
    public Void visit(ECPNodeSet pNodeSet) {
      GuardedLabel lLabel = sLabelCache.get(pNodeSet);

      if (lLabel == null) {
        lLabel = new GuardedLambdaLabel(pNodeSet);
        sLabelCache.put(pNodeSet, lLabel);
      }

      mAutomaton.createEdge(getInitialState(), getFinalState(), lLabel);

      return null;
    }

    @Override
    public Void visit(ECPPredicate pPredicate) {
      GuardedLabel lLabel = sLabelCache.get(pPredicate);

      if (lLabel == null) {
        lLabel = new GuardedLambdaLabel(pPredicate);
        sLabelCache.put(pPredicate, lLabel);
      }

      mAutomaton.createEdge(getInitialState(), getFinalState(), lLabel);

      return null;
    }

    @Override
    public Void visit(ECPConcatenation pConcatenation) {
      if (pConcatenation.isEmpty()) {
        mAutomaton.createEdge(getInitialState(), getFinalState(), GuardedLambdaLabel.UNGUARDED_LAMBDA_LABEL);
      }
      else {
        NFA.State lTmpInitialState = getInitialState();
        NFA.State lTmpFinalState = getFinalState();

        for (int i = 0; i < pConcatenation.size(); i++) {
          ElementaryCoveragePattern lSubpattern = pConcatenation.get(i);

          if (i > 0) {
            // use final state from before
            setInitialState(getFinalState());
          }

          if (i == pConcatenation.size() - 1) {
            // use lTmpFinalState
            setFinalState(lTmpFinalState);
          }
          else {
            // create new final state
            setFinalState(mAutomaton.createState());
          }

          lSubpattern.accept(this);
        }

        setInitialState(lTmpInitialState);
      }

      return null;
    }

    @Override
    public Void visit(ECPUnion pUnion) {
      if (pUnion.isEmpty()) {
        mAutomaton.createEdge(getInitialState(), getFinalState(), GuardedLambdaLabel.UNGUARDED_LAMBDA_LABEL);
      }
      else if (pUnion.size() == 1) {
        pUnion.get(0).accept(this);
      }
      else {
        NFA.State lTmpInitialState = getInitialState();

        for (ElementaryCoveragePattern lSubpattern : pUnion) {
          setInitialState(mAutomaton.createState());

          mAutomaton.createEdge(lTmpInitialState, getInitialState(), GuardedLambdaLabel.UNGUARDED_LAMBDA_LABEL);

          lSubpattern.accept(this);
        }

        setInitialState(lTmpInitialState);
      }

      return null;
    }

    @Override
    public Void visit(ECPRepetition pRepetition) {
      NFA.State lTmpInitialState = getInitialState();
      NFA.State lTmpFinalState = getFinalState();

      mAutomaton.createEdge(lTmpInitialState, lTmpFinalState, GuardedLambdaLabel.UNGUARDED_LAMBDA_LABEL);

      setInitialState(mAutomaton.createState());
      setFinalState(lTmpInitialState);

      mAutomaton.createEdge(lTmpInitialState, getInitialState(), GuardedLambdaLabel.UNGUARDED_LAMBDA_LABEL);

      pRepetition.getSubpattern().accept(this);

      setInitialState(lTmpInitialState);
      setFinalState(lTmpFinalState);

      return null;
    }

  }

  /*
   * We only need the backwards reachability closure since in case a state is not
   * reachable via the initial state, it will not be considered during analysis
   * anyhow.
   */
  public static NFA<GuardedEdgeLabel> removeDeadEnds(NFA<GuardedEdgeLabel> pAutomaton) {
    // TODO does hash set introduce nondeterminism?
    Set<NFA.State> lClosure = new HashSet<>();

    for (NFA.State lFinalState : pAutomaton.getFinalStates()) {
      LinkedList<NFA.State> lWorklist = new LinkedList<>();

      lWorklist.add(lFinalState);
      lClosure.add(lFinalState);

      while (!lWorklist.isEmpty()) {
        NFA.State lCurrentState = lWorklist.removeFirst();

        for (NFA<GuardedEdgeLabel>.Edge lIncomingTransition : pAutomaton.getIncomingEdges(lCurrentState)) {
          NFA.State lSource = lIncomingTransition.getSource();

          if (!lClosure.contains(lSource)) {
            lWorklist.add(lSource);
            lClosure.add(lSource);
          }
        }
      }
    }

    NFA<GuardedEdgeLabel> lNewAutomaton = new NFA<>();

    if (lClosure.contains(pAutomaton.getInitialState())) {
      Map<NFA.State, NFA.State> lStateMap = new HashMap<>();

      lStateMap.put(pAutomaton.getInitialState(), lNewAutomaton.getInitialState());

      for (NFA.State lState : pAutomaton.getStates()) {
        if (!lState.equals(pAutomaton.getInitialState())) {
          lStateMap.put(lState, lNewAutomaton.createState());
        }
      }

      for (NFA<GuardedEdgeLabel>.Edge lTransition : pAutomaton.getEdges()) {
        if (lClosure.contains(lTransition.getTarget())) {
          lNewAutomaton.createEdge(lStateMap.get(lTransition.getSource()), lStateMap.get(lTransition.getTarget()), lTransition.getLabel());
        }
      }

      // set final states
      for (NFA.State lFinalState : pAutomaton.getFinalStates()) {
        if (lClosure.contains(lFinalState)) {
          lNewAutomaton.addToFinalStates(lStateMap.get(lFinalState));
        }
      }
    }

    return lNewAutomaton;
  }

  public static NFA<GuardedEdgeLabel> reduceEdgeSets(NFA<GuardedEdgeLabel> pAutomaton) {
    NFA<GuardedEdgeLabel> lNewAutomaton = new NFA<>();

    Map<NFA.State, NFA.State> lStateMap = new HashMap<>();

    lStateMap.put(pAutomaton.getInitialState(), lNewAutomaton.getInitialState());

    for (NFA.State lState : pAutomaton.getStates()) {
      if (!lState.equals(pAutomaton.getInitialState())) {
        lStateMap.put(lState, lNewAutomaton.createState());
      }
    }

    // this implementation is a very simple heuristic ... TODO generalize
    for (NFA.State lState : pAutomaton.getStates()) {

      boolean lMatch = false;

      if (!pAutomaton.isFinalState(lState) && !pAutomaton.getInitialState().equals(lState)) {
        if (pAutomaton.getOutgoingEdges(lState).size() == 1) {
          NFA<GuardedEdgeLabel>.Edge lOutgoingEdge = pAutomaton.getOutgoingEdges(lState).iterator().next();

          GuardedEdgeLabel lLabel = lOutgoingEdge.getLabel();

          ECPEdgeSet lEdgeSet = lLabel.getEdgeSet();

          if (lEdgeSet.size() == 1) {
            CFAEdge lOutgoingCFAEdge = lEdgeSet.iterator().next();

            if (pAutomaton.getIncomingEdges(lState).size() == 1) {
              NFA<GuardedEdgeLabel>.Edge lIncomingEdge = pAutomaton.getIncomingEdges(lState).iterator().next();

              ECPEdgeSet lIncomingEdgeSet = lIncomingEdge.getLabel().getEdgeSet();

              CFANode lPredecessor = lOutgoingCFAEdge.getPredecessor();

              ArrayList<CFAEdge> lIncomingCFAEdges = new ArrayList<>(lPredecessor.getNumEnteringEdges());

              for (int lIndex = 0; lIndex < lPredecessor.getNumEnteringEdges(); lIndex++) {
                CFAEdge lIncomingCFAEdge = lPredecessor.getEnteringEdge(lIndex);

                if (lIncomingEdgeSet.contains(lIncomingCFAEdge)) {
                  lIncomingCFAEdges.add(lIncomingCFAEdge);
                }
              }

              //ECPEdgeSet lNewEdgeSet = new ECPEdgeSet(lIncomingCFAEdges);
              ECPEdgeSet lNewEdgeSet = StandardECPEdgeSet.create(lIncomingCFAEdges);

              GuardedEdgeLabel lNewLabel = new GuardedEdgeLabel(lNewEdgeSet, lIncomingEdge.getLabel().getGuards());

              lNewAutomaton.createEdge(lStateMap.get(lIncomingEdge.getSource()), lStateMap.get(lIncomingEdge.getTarget()), lNewLabel);

              lMatch = true;
            }
          }
        }
      }

      if (!lMatch) {
        // add incoming automaton edges
        for (NFA<GuardedEdgeLabel>.Edge lIncomingEdge : pAutomaton.getIncomingEdges(lState)) {
          lNewAutomaton.createEdge(lStateMap.get(lIncomingEdge.getSource()), lStateMap.get(lIncomingEdge.getTarget()), lIncomingEdge.getLabel());
        }
      }
    }

    // set final states
    for (NFA.State lFinalState : pAutomaton.getFinalStates()) {
      lNewAutomaton.addToFinalStates(lStateMap.get(lFinalState));
    }

    return lNewAutomaton;
  }

  public static NFA<GuardedEdgeLabel> removeInfeasibleTransitions(NFA<GuardedEdgeLabel> pAutomaton) {
    NFA<GuardedEdgeLabel> lNewAutomaton = new NFA<>();

    Map<NFA.State, NFA.State> lStateMap = new HashMap<>();

    lStateMap.put(pAutomaton.getInitialState(), lNewAutomaton.getInitialState());

    for (NFA.State lState : pAutomaton.getStates()) {
      if (!lState.equals(pAutomaton.getInitialState())) {
        lStateMap.put(lState, lNewAutomaton.createState());
      }
    }

    for (NFA.State lState : pAutomaton.getStates()) {
      for (NFA<GuardedEdgeLabel>.Edge lIncomingEdge : pAutomaton.getIncomingEdges(lState)) {
        boolean lMatch = false;

        GuardedEdgeLabel lIncomingLabel = lIncomingEdge.getLabel();

        if (!(lIncomingLabel instanceof InverseGuardedEdgeLabel)
            && !pAutomaton.getFinalStates().contains(lState)) {
          for (NFA<GuardedEdgeLabel>.Edge lOutgoingEdge : pAutomaton.getOutgoingEdges(lState)) {
            GuardedEdgeLabel lOutgoingLabel = lOutgoingEdge.getLabel();

            for (CFAEdge lIncomingCFAEdge : lIncomingLabel.getEdgeSet()) {
              for (CFAEdge lOutgoingCFAEdge : lOutgoingLabel.getEdgeSet()) {
                if (lIncomingCFAEdge.getSuccessor().equals(lOutgoingCFAEdge.getPredecessor())) {
                  lMatch = true;
                  break;
                }
              }

              if (lMatch) {
                break;
              }
            }

            if (lMatch) {
              break;
            }
          }
        }
        else {
          lMatch = true;
        }

        if (lMatch) {
          lNewAutomaton.createEdge(lStateMap.get(lIncomingEdge.getSource()), lStateMap.get(lIncomingEdge.getTarget()), lIncomingLabel);
        }
      }
    }

    // set final states
    for (NFA.State lFinalState : pAutomaton.getFinalStates()) {
      lNewAutomaton.addToFinalStates(lStateMap.get(lFinalState));
    }

    return lNewAutomaton;
  }

  public static NFA<GuardedEdgeLabel> removeEmptySelfLoops(
      NFA<GuardedEdgeLabel> pAutomaton) {
    NFA<GuardedEdgeLabel> lNewAutomaton = new NFA<>();

    Map<NFA.State, NFA.State> lStateMap = new HashMap<>();
    lStateMap.put(pAutomaton.getInitialState(), lNewAutomaton.getInitialState());

    lStateMap.put(pAutomaton.getInitialState(), lNewAutomaton.getInitialState());

    for (NFA.State lState : pAutomaton.getStates()) {
      if (!lState.equals(pAutomaton.getInitialState())) {
        lStateMap.put(lState, lNewAutomaton.createState());
      }
    }

    for (NFA<GuardedEdgeLabel>.Edge edge : pAutomaton.getEdges()) {
      if (!(edge.getSource().equals(edge.getTarget()) && Pattern.matches(stutterEdgeLabelPattern, edge.getLabel().toString()))) {
        lNewAutomaton.createEdge(edge.getSource(), edge.getTarget(), edge.getLabel());
      }
    }

    // set final states
    for (NFA.State lFinalState : pAutomaton.getFinalStates()) {
      lNewAutomaton.addToFinalStates(lStateMap.get(lFinalState));
    }

    return lNewAutomaton;
  }

  public static NFA<GuardedEdgeLabel> removeRedundantEdges(
      NFA<GuardedEdgeLabel> pAutomaton) {
    NFA<GuardedEdgeLabel> lNewAutomaton = new NFA<>();

    Map<NFA.State, NFA.State> lStateMap = new HashMap<>();
    lStateMap.put(pAutomaton.getInitialState(), lNewAutomaton.getInitialState());

    traverseAutomaton(pAutomaton, lNewAutomaton, lStateMap, pAutomaton.getInitialState(), null, false);

    // set final states
    for (NFA.State lFinalState : pAutomaton.getFinalStates()) {
      lNewAutomaton.addToFinalStates(lStateMap.get(lFinalState));
    }

    return lNewAutomaton;
  }

  public static NFA<GuardedEdgeLabel> removeSingleStutterEdges(
      NFA<GuardedEdgeLabel> pAutomaton) {
    NFA<GuardedEdgeLabel> lNewAutomaton = new NFA<>();

    Map<NFA.State, NFA.State> lStateMap = new HashMap<>();
    lStateMap.put(pAutomaton.getInitialState(), lNewAutomaton.getInitialState());

    Set<State> statesToIgnore = new HashSet<>();

    for (State state : pAutomaton.getStates()) {
      if (statesToIgnore.contains(state)) {
        continue;
      }

      Collection<NFA<GuardedEdgeLabel>.Edge> outgoingEdges =
          pAutomaton.getOutgoingEdges(state);

      for (NFA<GuardedEdgeLabel>.Edge edge : outgoingEdges) {
        if (Pattern.matches(stutterEdgeLabelPattern, edge.getLabel().toString())) {
          removeStutterEdge(pAutomaton, lNewAutomaton, lStateMap, state, edge);
          statesToIgnore.add(edge.getTarget());
        } else {
          addEdge(lNewAutomaton, lStateMap, state, edge);
        }
      }
    }

    // set final states
    for (NFA.State lFinalState : pAutomaton.getFinalStates()) {
      lNewAutomaton.addToFinalStates(lStateMap.get(lFinalState));
    }

    return lNewAutomaton;
  }

  private static void removeStutterEdge(
      NFA<GuardedEdgeLabel> pAutomaton,
      NFA<GuardedEdgeLabel> pNewAutomaton,
      Map<State, State> pStateMap, State pState,
      NFA<GuardedEdgeLabel>.Edge pEdge) {

    State succ = pEdge.getTarget();
    Collection<NFA<GuardedEdgeLabel>.Edge> succOutgoingEdges =
        pAutomaton.getOutgoingEdges(succ);

    State source = pStateMap.get(pState);
    for (NFA<GuardedEdgeLabel>.Edge edge : succOutgoingEdges) {
      State target = pStateMap.get(edge.getTarget());

      if (target == null) {
        State newState = pNewAutomaton.createState();
        target = newState;
        pStateMap.put(edge.getTarget(), target);
      }

      pNewAutomaton.createEdge(source, target, edge.getLabel());
    }
  }

  private static void addEdge(NFA<GuardedEdgeLabel> lNewAutomaton,
      Map<NFA.State, NFA.State> lStateMap,
      State state, NFA<GuardedEdgeLabel>.Edge edge) {
    State source = lStateMap.get(state);
    State target = lStateMap.get(edge.getTarget());

    if (source == null) {
      State newState = lNewAutomaton.createState();
      source = newState;
      lStateMap.put(edge.getSource(), source);
    }

    if (target == null) {
      State newState = lNewAutomaton.createState();
      target = newState;
      lStateMap.put(edge.getTarget(), target);
    }

    lNewAutomaton.createEdge(source, target, edge.getLabel());
  }

  private static void traverseAutomaton(
      NFA<GuardedEdgeLabel> pAutomaton,
      NFA<GuardedEdgeLabel> pNewAutomaton,
      Map<State, State> pStateMap, State pState,
      NFA<GuardedEdgeLabel>.Edge pIncomingEdge,
      boolean initialStateTraversed) {

    boolean stateTraversed = true;

    // add new state
    State newState = pStateMap.get(pState);
    if (newState == null) {
      newState = pNewAutomaton.createState();
      pStateMap.put(pState, newState);
      stateTraversed = false;
    }

    // add incoming transition
    if (pIncomingEdge != null) {
      State source = pStateMap.get(pIncomingEdge.getSource());
      pNewAutomaton.createEdge(source, newState, pIncomingEdge.getLabel());
    }

    if (initialStateTraversed && stateTraversed) {
      return;
    }

    Map<GuardedEdgeLabel, List<NFA<GuardedEdgeLabel>.Edge>> edges = new HashMap<>();

    for (NFA<GuardedEdgeLabel>.Edge edge : pAutomaton.getOutgoingEdges(pState)) {
      List<NFA<GuardedEdgeLabel>.Edge> curEdges = edges.get(edge.getLabel());
      if (curEdges == null) {
        curEdges = new ArrayList<>();
      }
      curEdges.add(edge);
      edges.put(edge.getLabel(), curEdges);
    }

    for (GuardedEdgeLabel label : edges.keySet()) {
      List<NFA<GuardedEdgeLabel>.Edge> curEdges = edges.get(label);

      if (curEdges.size() == 2) {
        NFA<GuardedEdgeLabel>.Edge edge = isTriangle(curEdges, pAutomaton);
        if (edge != null) {
          traverseAutomaton(pAutomaton, pNewAutomaton, pStateMap, edge.getTarget(), edge, true);
        } else {
          for (NFA<GuardedEdgeLabel>.Edge e : curEdges) {
            traverseAutomaton(pAutomaton, pNewAutomaton, pStateMap, e.getTarget(), e, true);
          }
        }
      } else {
        for (NFA<GuardedEdgeLabel>.Edge e : curEdges) {
          traverseAutomaton(pAutomaton, pNewAutomaton, pStateMap, e.getTarget(), e, true);
        }
      }
    }
  }

  private static NFA<GuardedEdgeLabel>.Edge isTriangle(
      List<NFA<GuardedEdgeLabel>.Edge> pCurEdges,
      NFA<GuardedEdgeLabel> pAutomaton) {
    NFA<GuardedEdgeLabel>.Edge firstEdge = pCurEdges.get(0);
    NFA<GuardedEdgeLabel>.Edge secondEdge = pCurEdges.get(1);

    State firstTargetState = null;
    State secondTargetState = null;

    State firstTarget = firstEdge.getTarget();
    Collection<NFA<GuardedEdgeLabel>.Edge> firstTargetOutgoing = pAutomaton.getOutgoingEdges(firstTarget);

    if (firstTargetOutgoing.size() == 1) {
      for (NFA<GuardedEdgeLabel>.Edge edge : firstTargetOutgoing) {
        if (Pattern.matches(stutterEdgeLabelPattern, edge.getLabel().toString())) {
          firstTargetState = edge.getTarget();
        }
      }
    } else {
      firstTargetState = firstTarget;
    }

    State secondTarget = secondEdge.getTarget();
    Collection<NFA<GuardedEdgeLabel>.Edge> secondTargetOutgoing = pAutomaton.getOutgoingEdges(secondTarget);

    if (secondTargetOutgoing.size() == 1) {
      for (NFA<GuardedEdgeLabel>.Edge edge : secondTargetOutgoing) {
        if (Pattern.matches(stutterEdgeLabelPattern, edge.getLabel().toString())) {
          secondTargetState = edge.getTarget();
          if (secondTargetState.equals(firstTargetState)) {
            return firstEdge;
          }
        }
      }
    } else {
      secondTargetState = secondEdge.getTarget();
      if (secondTargetState.equals(firstTargetState)) {
        return secondEdge;
      }
    }

    return null;
  }

  public static NFA<GuardedEdgeLabel> removeNotMainSelfLoop(
      NFA<GuardedEdgeLabel> pAutomaton) {
    NFA<GuardedEdgeLabel> lNewAutomaton = new NFA<>();

    Map<NFA.State, NFA.State> lStateMap = new HashMap<>();
    lStateMap.put(pAutomaton.getInitialState(), lNewAutomaton.getInitialState());

    lStateMap.put(pAutomaton.getInitialState(), lNewAutomaton.getInitialState());

    for (NFA.State lState : pAutomaton.getStates()) {
      if (!lState.equals(pAutomaton.getInitialState())) {
        lStateMap.put(lState, lNewAutomaton.createState());
      }
    }

    for (NFA<GuardedEdgeLabel>.Edge edge : pAutomaton.getEdges()) {
      if (!(edge.getSource().equals(edge.getTarget()) && pAutomaton.getInitialState().equals(edge.getSource()) && Pattern.matches(notMainLabelPattern, edge.getLabel().toString()))) {
        lNewAutomaton.createEdge(edge.getSource(), edge.getTarget(), edge.getLabel());
      }
    }

    // set final states
    for (NFA.State lFinalState : pAutomaton.getFinalStates()) {
      lNewAutomaton.addToFinalStates(lStateMap.get(lFinalState));
    }

    return lNewAutomaton;
  }

}
