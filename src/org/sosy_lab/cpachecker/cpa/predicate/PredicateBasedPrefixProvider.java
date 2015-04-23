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
package org.sosy_lab.cpachecker.cpa.predicate;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath.PathIterator;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.MutableARGPath;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.SolverException;
import org.sosy_lab.cpachecker.util.predicates.Solver;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.InterpolatingProverEnvironmentWithAssumptions;
import org.sosy_lab.cpachecker.util.predicates.interfaces.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormula;
import org.sosy_lab.cpachecker.util.refinement.InfeasiblePrefix;
import org.sosy_lab.cpachecker.util.refinement.PrefixProvider;

import com.google.common.collect.Iterables;

public class PredicateBasedPrefixProvider implements PrefixProvider {
  private final LogManager logger;

  private final Solver solver;

  private final PathFormulaManager pathFormulaManager;

  /**
   * This method acts as the constructor of the class.
   *
   * @param pSolver the solver to use
   */
  public PredicateBasedPrefixProvider(LogManager pLogger, Solver pSolver, PathFormulaManager pPathFormulaManager) {
    logger = pLogger;
    solver = pSolver;
    pathFormulaManager = pPathFormulaManager;
  }

  /* (non-Javadoc)
   * @see org.sosy_lab.cpachecker.cpa.predicate.PrefixProvider#getInfeasilbePrefixes(org.sosy_lab.cpachecker.cpa.arg.ARGPath)
   */
  @Override
  public <T> List<InfeasiblePrefix> extractInfeasilbePrefixes(final ARGPath path) throws CPAException, InterruptedException {
    List<InfeasiblePrefix> prefixes = new ArrayList<>();
    MutableARGPath feasiblePrefixPath = new MutableARGPath();
    List<T> feasiblePrefixTerms = new ArrayList<>(path.size());

    try (@SuppressWarnings("unchecked")
      InterpolatingProverEnvironmentWithAssumptions<T> prover =
      (InterpolatingProverEnvironmentWithAssumptions<T>)solver.newProverEnvironmentWithInterpolation()) {

      PathFormula formula = pathFormulaManager.makeEmptyPathFormula();

      PathIterator iterator = path.pathIterator();
      while (iterator.hasNext()) {
        feasiblePrefixPath.addLast(Pair.of(iterator.getAbstractState(), iterator.getOutgoingEdge()));
        try {
          formula = pathFormulaManager.makeAnd(pathFormulaManager.makeEmptyPathFormula(formula), iterator.getOutgoingEdge());
          T term = prover.push(formula.getFormula());
          feasiblePrefixTerms.add(term);

          if (iterator.getOutgoingEdge().getEdgeType() == CFAEdgeType.AssumeEdge && prover.isUnsat()) {
            logger.log(Level.FINE, "found infeasible prefix: ", iterator.getOutgoingEdge(), " resulted in an unsat-formula");

            List<BooleanFormula> interpolantSequence = extractInterpolantSequence(feasiblePrefixTerms, prover);

            // add infeasible prefix
            InfeasiblePrefix infeasiblePrefix = buildInfeasiblePrefix(path, feasiblePrefixPath, interpolantSequence, solver.getFormulaManager());
            prefixes.add(infeasiblePrefix);

            // remove failing operation
            Pair<ARGState, CFAEdge> failingOperation =
                removeFailingOperation(feasiblePrefixPath, feasiblePrefixTerms, prover);

            // add noop-operation
            formula = addNoopOperation(feasiblePrefixPath, feasiblePrefixTerms, prover, formula, failingOperation);

            if(prefixes.size() > 50) {
              return prefixes;
            }
          }
        }

        catch (SolverException | CPATransferException e) {
          throw new CPAException("Error during computation of prefixes: " + e.getMessage(), e);
        }

        iterator.advance();
      }
    }

    return prefixes;
  }

  private <T> List<BooleanFormula> extractInterpolantSequence(List<T> feasiblePrefixFormulas,
      InterpolatingProverEnvironmentWithAssumptions<T> prover) throws SolverException {

    List<BooleanFormula> interpolantSequence = new ArrayList<>();

    for(int i = 1; i < feasiblePrefixFormulas.size(); i++) {
      interpolantSequence.add(prover.getInterpolant(feasiblePrefixFormulas.subList(0, i)));
    }

    return interpolantSequence;
  }

  private InfeasiblePrefix buildInfeasiblePrefix(final ARGPath path,
      MutableARGPath currentPrefix,
      List<BooleanFormula> interpolantSequence,
      FormulaManagerView fmgr) {
    MutableARGPath infeasiblePrefix = new MutableARGPath();
    infeasiblePrefix.addAll(currentPrefix);

    // for interpolation, one transition after the infeasible
    // transition is needed, so we add the final (error) state
    infeasiblePrefix.add(Pair.of(Iterables.getLast(path.asStatesList()), Iterables.getLast(path.asEdgesList())));

    return InfeasiblePrefix.buildForPredicateDomain(infeasiblePrefix.immutableCopy(), interpolantSequence, fmgr);
  }

  private <T> Pair<ARGState, CFAEdge> removeFailingOperation(MutableARGPath feasiblePrefixPath,
      List<T> feasiblePrefixTerms, InterpolatingProverEnvironmentWithAssumptions<T> prover) {
    Pair<ARGState, CFAEdge> failingOperation = feasiblePrefixPath.removeLast();

    // also remove formula, term for failing assume edge from stack, formula
    prover.pop();
    feasiblePrefixTerms.remove(feasiblePrefixTerms.size() - 1);
    return failingOperation;
  }

  private <T> PathFormula addNoopOperation(MutableARGPath feasiblePrefixPath, List<T> feasiblePrefixTerms,
      InterpolatingProverEnvironmentWithAssumptions<T> prover, PathFormula formula,
      Pair<ARGState, CFAEdge> failingOperation) throws CPATransferException, InterruptedException {
    CFAEdge noopEdge = BlankEdge.buildNoopEdge(
        failingOperation.getSecond().getPredecessor(),
        failingOperation.getSecond().getSuccessor());

    feasiblePrefixPath.add(Pair.<ARGState, CFAEdge>of(failingOperation.getFirst(), noopEdge));

    formula = pathFormulaManager.makeAnd(pathFormulaManager.makeEmptyPathFormula(formula), noopEdge);
    feasiblePrefixTerms.add(prover.push(formula.getFormula()));
    return formula;
  }
}
