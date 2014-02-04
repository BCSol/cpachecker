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
package org.sosy_lab.cpachecker.tiger.goals.clustering;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;

import org.sosy_lab.common.Pair;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.tiger.fql.ast.Edges;
import org.sosy_lab.cpachecker.tiger.fql.ast.FQLSpecification;
import org.sosy_lab.cpachecker.tiger.fql.ast.Nodes;
import org.sosy_lab.cpachecker.tiger.fql.ast.Paths;
import org.sosy_lab.cpachecker.tiger.fql.ast.Predicate;
import org.sosy_lab.cpachecker.tiger.fql.ast.coveragespecification.Concatenation;
import org.sosy_lab.cpachecker.tiger.fql.ast.coveragespecification.CoverageSpecification;
import org.sosy_lab.cpachecker.tiger.fql.ast.coveragespecification.CoverageSpecificationVisitor;
import org.sosy_lab.cpachecker.tiger.fql.ast.coveragespecification.Quotation;
import org.sosy_lab.cpachecker.tiger.fql.ast.coveragespecification.Union;
import org.sosy_lab.cpachecker.tiger.fql.ast.filter.Identity;
import org.sosy_lab.cpachecker.tiger.fql.ast.pathpattern.PathPatternVisitor;
import org.sosy_lab.cpachecker.tiger.fql.ast.pathpattern.Repetition;

public class InfeasibilityPropagation {

  public static enum Prediction {
    UNKNOWN,
    INFEASIBLE;
  }

  /*private CFAEdge getSingletonCFAEdge(ElementaryCoveragePattern pPattern, int pIndex) {
    ECPConcatenation lConcatenation = (ECPConcatenation)pPattern;

    int lIndex = 1;

    for (ElementaryCoveragePattern lSubpattern : lConcatenation) {
      if (lSubpattern instanceof SingletonECPEdgeSet) {
        if (lIndex == pIndex) {
          SingletonECPEdgeSet lSingletonSet = (SingletonECPEdgeSet)lSubpattern;
          return lSingletonSet.getCFAEdge();
        }
        else {
          lIndex++;
        }
      }
    }

    throw new RuntimeException("Unhandled case!");
  }

  private boolean dfs(CFANode pInitialCFANode, CFAEdge pForbiddenCFAEdge, CFAEdge pTargetCFAEdge) {
    LinkedList<CFANode> lWorklist = new LinkedList<CFANode>();
    lWorklist.add(pInitialCFANode);

    HashSet<CFANode> lVisitedCFANodes = new HashSet<CFANode>();

    while (!lWorklist.isEmpty()) {
      CFANode lCurrentCFANode = lWorklist.poll();

      if (lVisitedCFANodes.contains(lCurrentCFANode)) {
        continue;
      }

      lVisitedCFANodes.add(lCurrentCFANode);

      for (int lEdgeIndex = 0; lEdgeIndex < lCurrentCFANode.getNumLeavingEdges(); lEdgeIndex++) {
        CFAEdge lCFAEdge = lCurrentCFANode.getLeavingEdge(lEdgeIndex);

        if (!pForbiddenCFAEdge.equals(lCFAEdge)) {
          if (lCFAEdge.equals(pTargetCFAEdge)) {
            return true;
          }

          lWorklist.add(lCFAEdge.getSuccessor());
        }
      }
    }

    return false;
  }*/

  public static Collection<CFAEdge> dfs2(CFANode pInitialCFANode, CFAEdge pForbiddenCFAEdge, Collection<CFAEdge> pTargetEdges) {
    HashSet<CFAEdge> lFoundTargetEdges = new HashSet<>();
    HashSet<CFAEdge> lTargetEdges = new HashSet<>(pTargetEdges);

    LinkedList<CFANode> lWorklist = new LinkedList<>();
    lWorklist.add(pInitialCFANode);

    HashSet<CFANode> lVisitedCFANodes = new HashSet<>();

    while (!lWorklist.isEmpty() && !lTargetEdges.isEmpty()) {
      CFANode lCurrentCFANode = lWorklist.poll();

      if (lVisitedCFANodes.contains(lCurrentCFANode)) {
        continue;
      }

      lVisitedCFANodes.add(lCurrentCFANode);

      for (int lEdgeIndex = 0; lEdgeIndex < lCurrentCFANode.getNumLeavingEdges(); lEdgeIndex++) {
        CFAEdge lCFAEdge = lCurrentCFANode.getLeavingEdge(lEdgeIndex);

        if (!pForbiddenCFAEdge.equals(lCFAEdge)) {
          if (lTargetEdges.contains(lCFAEdge)) {
            lFoundTargetEdges.add(lCFAEdge);
            lTargetEdges.remove(lCFAEdge);
          }

          lWorklist.add(lCFAEdge.getSuccessor());
        }
      }
    }

    return lFoundTargetEdges;
  }

  public static Pair<Boolean, LinkedList<Edges>> canApplyInfeasibilityPropagation(FQLSpecification pFQLSpecification) {
    if (pFQLSpecification.hasPassingClause()) {
      return Pair.of(Boolean.FALSE, null); // TODO think about that
    }

    // TODO check for predication filter ?

    CoverageSpecification lCovers = pFQLSpecification.getCoverageSpecification();

    LinkedList<CoverageSpecification> lSequence = extractSequence(lCovers);

    if (lSequence == null) {
      return Pair.of(Boolean.FALSE, null);
    }

    LinkedList<Edges> lSubgoals = new LinkedList<>();

    for (CoverageSpecification lElement : lSequence) {
      if (lElement instanceof Edges) {
        lSubgoals.add((Edges)lElement);
      }
    }

    return Pair.of(Boolean.TRUE, lSubgoals);
  }

  private static LinkedList<CoverageSpecification> extractSequence(CoverageSpecification pCoverageSpecification) {
    MyVisitor lVisitor = new MyVisitor();

    pCoverageSpecification.accept(lVisitor);

    if (lVisitor.mUseable) {
      boolean lHasToBeIdStar = true;

      for (CoverageSpecification lSubspecification : lVisitor.mSequence) {
        if (lHasToBeIdStar) {
          if (lSubspecification instanceof Quotation) {
            Quotation lQuotation = (Quotation)lSubspecification;

            if (lQuotation.getPathPattern().accept(IsIdStarVisitor.INSTANCE)) {
              lHasToBeIdStar = false;
            }

            break;
          }
          else {
            break;
          }
        }
        else {
          if (lSubspecification instanceof Edges) {
            lHasToBeIdStar = true;
          }
          else {
            break;
          }
        }
      }

      if (!lHasToBeIdStar) {
        return lVisitor.mSequence;
      }
    }

    return null;
  }

  private static class IsIdStarVisitor implements PathPatternVisitor<Boolean> {

    private static IsIdStarVisitor INSTANCE = new IsIdStarVisitor();

    private boolean mInRepetition = false;

    @Override
    public Boolean visit(
        org.sosy_lab.cpachecker.tiger.fql.ast.pathpattern.Concatenation pConcatenation) {
      return Boolean.FALSE;
    }

    @Override
    public Boolean visit(Repetition pRepetition) {
      mInRepetition = true;
      Boolean lResult = pRepetition.getSubpattern().accept(this);
      mInRepetition = false;

      return lResult;
    }

    @Override
    public Boolean visit(
        org.sosy_lab.cpachecker.tiger.fql.ast.pathpattern.Union pUnion) {
      return Boolean.FALSE;
    }

    @Override
    public Boolean visit(Edges pEdges) {
      if (!mInRepetition) {
        return Boolean.FALSE;
      }

      return pEdges.getFilter().equals(Identity.getInstance());
    }

    @Override
    public Boolean visit(Nodes pNodes) {
      return Boolean.FALSE;
    }

    @Override
    public Boolean visit(Paths pPaths) {
      return Boolean.FALSE;
    }

    @Override
    public Boolean visit(Predicate pPredicate) {
      return Boolean.FALSE;
    }

  }

  private static class MyVisitor implements CoverageSpecificationVisitor<Void> {

    LinkedList<CoverageSpecification> mSequence = new LinkedList<>();
    boolean mUseable = true;

    @Override
    public Void visit(Concatenation pConcatenation) {
      pConcatenation.getFirstSubspecification().accept(this);
      pConcatenation.getSecondSubspecification().accept(this);

      return null;
    }

    @Override
    public Void visit(Quotation pQuotation) {
      mSequence.addLast(pQuotation);

      return null;
    }

    @Override
    public Void visit(Union pUnion) {
      // TODO think about that again
      mUseable = false;

      return null;
    }

    @Override
    public Void visit(Edges pEdges) {
      mSequence.addLast(pEdges);

      return null;
    }

    @Override
    public Void visit(Nodes pNodes) {
      // TODO think about that again
      mUseable = false;

      return null;
    }

    @Override
    public Void visit(Paths pPaths) {
      // TODO think about that again
      mUseable = false;

      return null;
    }

    @Override
    public Void visit(Predicate pPredicate) {
      // TODO think about that again
      mUseable = false;

      return null;
    }

  }

}
