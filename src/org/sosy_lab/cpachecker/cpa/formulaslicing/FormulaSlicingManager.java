package org.sosy_lab.cpachecker.cpa.formulaslicing;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.SingletonPrecision;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult.Action;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.loopstack.LoopstackState;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.LiveVariables;
import org.sosy_lab.cpachecker.util.LoopStructure;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormula;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap;
import org.sosy_lab.cpachecker.util.predicates.smt.BooleanFormulaManagerView.BooleanFormulaTransformationVisitor;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.smt.Solver;
import org.sosy_lab.solver.SolverException;
import org.sosy_lab.solver.api.BooleanFormula;
import org.sosy_lab.solver.api.BooleanFormulaManager;
import org.sosy_lab.solver.api.Formula;
import org.sosy_lab.solver.api.FunctionDeclaration;
import org.sosy_lab.solver.api.FunctionDeclarationKind;
import org.sosy_lab.solver.basicimpl.tactics.Tactic;
import org.sosy_lab.solver.visitors.DefaultFormulaVisitor;
import org.sosy_lab.solver.visitors.TraversalProcess;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Options(prefix="cpa.slicing")
public class FormulaSlicingManager implements IFormulaSlicingManager {
  private final PathFormulaManager pfmgr;
  private final BooleanFormulaManager bfmgr;
  private final FormulaManagerView fmgr;
  private final InductiveWeakeningManager inductiveWeakeningManager;
  private final Solver solver;
  private final FormulaSlicingStatistics statistics;
  private final RCNFManager rcnfManager;
  private final LiveVariables liveVariables;
  private final LoopStructure loopStructure;

  @SuppressWarnings({"FieldCanBeLocal", "unused"})
  private final LogManager logger;

  @Option(secure=true, description="Check target states reachability")
  private boolean checkTargetStates = true;

  @Option(secure=true, description="Filter lemmas by liveness")
  private boolean filterByLiveness = true;

  FormulaSlicingManager(
      Configuration config,
      PathFormulaManager pPfmgr,
      FormulaManagerView pFmgr,
      CFA pCfa,
      InductiveWeakeningManager pInductiveWeakeningManager,
      RCNFManager pRcnfManager,
      Solver pSolver,
      LogManager pLogger)
      throws InvalidConfigurationException {
    logger = pLogger;
    config.inject(this);
    fmgr = pFmgr;
    pfmgr = pPfmgr;
    inductiveWeakeningManager = pInductiveWeakeningManager;
    solver = pSolver;
    bfmgr = pFmgr.getBooleanFormulaManager();
    rcnfManager = pRcnfManager;
    statistics = new FormulaSlicingStatistics(pSolver);
    Preconditions.checkState(pCfa.getLiveVariables().isPresent() &&
      pCfa.getLoopStructure().isPresent());
    liveVariables = pCfa.getLiveVariables().get();
    loopStructure = pCfa.getLoopStructure().get();
  }

  @Override
  public Collection<? extends SlicingState> getAbstractSuccessors(
      SlicingState oldState, CFAEdge edge)
      throws CPATransferException, InterruptedException {

    statistics.propagation.start();
    SlicingIntermediateState iOldState;

    if (oldState.isAbstracted()) {
      iOldState = abstractStateToIntermediate(oldState.asAbstracted());
    } else {
      iOldState = oldState.asIntermediate();
    }

    PathFormula outPath = pfmgr.makeAnd(iOldState.getPathFormula(), edge);
    SlicingIntermediateState out = SlicingIntermediateState.of(
        edge.getSuccessor(), outPath, iOldState.getAbstractParent());
    statistics.propagation.stop();

    return Collections.singleton(out);
  }

  @Override
  public Optional<PrecisionAdjustmentResult> prec(
      SlicingState pState,
      UnmodifiableReachedSet pStates, AbstractState pFullState)
      throws CPAException, InterruptedException
  {
    SlicingIntermediateState iState;
    if (pState.isAbstracted()) {

      // We do not use the other invariant => do not repeat the computation.
      return Optional.of(
          PrecisionAdjustmentResult.create(
              pState, SingletonPrecision.getInstance(), Action.CONTINUE)
      );
    } else {
      iState = pState.asIntermediate();
    }

    boolean hasTargetState = Iterables.filter(
        AbstractStates.asIterable(pFullState),
        AbstractStates.IS_TARGET_STATE).iterator().hasNext();
    boolean shouldPerformAbstraction = shouldPerformAbstraction(
        iState.getNode(), pFullState);
    if (hasTargetState && checkTargetStates || shouldPerformAbstraction) {
      if (isUnreachable(iState)) {
        return Optional.absent();
      }
    }

    if (shouldPerformAbstraction) {

      Optional<SlicingAbstractedState> oldState = findOldToMerge(
          pStates, pFullState, pState);

      SlicingAbstractedState out;
      if (oldState.isPresent()) {
        // Perform slicing, there is a relevant "to-merge" element.
        out = performSlicing(iState, oldState.get());
      } else {
        out = SlicingAbstractedState.ofClauses(
            toRcnf(iState),
            iState.getPathFormula().getSsa(),
            iState.getPathFormula().getPointerTargetSet(),
            fmgr,
            iState.getNode(),
            Optional.of(iState)
        );
      }

      return Optional.of(
          PrecisionAdjustmentResult.create(
              out, SingletonPrecision.getInstance(), Action.CONTINUE)
      );
    } else {
      return Optional.of(PrecisionAdjustmentResult.create(
          pState, SingletonPrecision.getInstance(), Action.CONTINUE));
    }
  }

  /**
   * Convert the input state to the set of instantiated lemmas in RCNF.
   */
  private Set<BooleanFormula> toRcnf(SlicingIntermediateState iState)
      throws InterruptedException {
    PathFormula pf = iState.getPathFormula();
    SSAMap ssa = pf.getSsa();
    CFANode node = iState.getNode();
    SlicingAbstractedState abstractParent = iState.getAbstractParent();

    BooleanFormula transition = bfmgr.and(
        fmgr.simplify(pf.getFormula()),
        bfmgr.and(abstractParent.getInstantiatedAbstraction())
    );

    // Special handling for UFs as they can't be quantified.
    transition = removeLiteralsWithDeadUFs(transition, ssa);
    BooleanFormula quantified = fmgr.quantifyDeadVariables(
        transition, ssa);

    Set<BooleanFormula> lemmas = rcnfManager.toLemmas(quantified);

    Set<BooleanFormula> finalLemmas = new HashSet<>();
    for (BooleanFormula lemma : lemmas) {
      if (filterByLiveness &&
          Sets.intersection(
              ImmutableSet.copyOf(
                  liveVariables.getLiveVariableNamesForNode(node).filter(
                      Predicates.<String>notNull())),
              fmgr.extractFunctionNames(fmgr.uninstantiate(lemma))).isEmpty()
          ) {

        continue;
      }
      finalLemmas.add(fmgr.uninstantiate(lemma));
    }
    return finalLemmas;
  }


  private final Map<Pair<SlicingIntermediateState, SlicingAbstractedState>,
      SlicingAbstractedState> slicingCache = new HashMap<>();

  private SlicingAbstractedState performSlicing(
      SlicingIntermediateState iState,
      SlicingAbstractedState oldState
  ) throws CPAException, InterruptedException {
    SlicingAbstractedState out = slicingCache.get(Pair.of(iState, oldState));
    if (out != null) {
      return out;
    }

    SlicingAbstractedState fromState = iState.getAbstractParent();

    Set<BooleanFormula> candidateLemmas = Sets.intersection(
        oldState.getAbstraction(), fromState.getAbstraction());

    PathFormulaWithStartSSA path =
        new PathFormulaWithStartSSA(iState.getPathFormula(), iState
            .getAbstractParent().getSSA());
    if (oldState == fromState) {

      // TODO: optimization can be extended to nested loops
      // as well.
      if (fromState.getInductiveUnder().contains(path)) {

        // Optimization for non-nested loops.
        return SlicingAbstractedState.copyOf(fromState);
      }
    }

    Set<BooleanFormula> finalClauses;
    Set<PathFormulaWithStartSSA> inductiveUnder;
    try {
      statistics.inductiveWeakening.start();

      if (fromState != oldState) {
        finalClauses = inductiveWeakeningManager.findInductiveWeakeningForRCNF(
            fromState.getSSA(),
            fromState.getAbstraction(),
            iState.getPathFormula(),
            candidateLemmas
        );
        inductiveUnder = ImmutableSet.of();
      } else {

        // No nested loops: remove lemmas on both sides.
        finalClauses = inductiveWeakeningManager.findInductiveWeakeningForRCNF(
            fromState.getSSA(),
            iState.getPathFormula(),
            candidateLemmas
        );
        inductiveUnder = Sets.union(
            fromState.getInductiveUnder(), ImmutableSet.of(path)
        );
      }
    } catch (SolverException pE) {
      throw new CPAException("Solver call failed", pE);
    } finally {
      statistics.inductiveWeakening.stop();
    }

    out = SlicingAbstractedState.makeSliced(
        finalClauses,
        // It is crucial to use the previous SSA so that PathFormulas stay
        // the same and can be cached.
        oldState.getSSA(),
        iState.getPathFormula().getPointerTargetSet(),
        fmgr,
        iState.getNode(),
        Optional.of(iState),
        inductiveUnder
    );
    slicingCache.put(Pair.of(iState, oldState), out);
    return out;
  }

  private boolean isUnreachable(SlicingIntermediateState iState)
      throws InterruptedException, CPAException {

    BooleanFormula prevSlice = bfmgr.and(iState.getAbstractParent().getAbstraction());
    BooleanFormula instantiatedFormula =
        fmgr.instantiate(prevSlice, iState.getAbstractParent().getSSA());
    BooleanFormula reachabilityQuery = bfmgr.and(
        iState.getPathFormula().getFormula(), instantiatedFormula);
    try {
      return solver.isUnsat(reachabilityQuery);
    } catch (SolverException pE) {
      throw new CPAException("Solver exception suppressed: ", pE);
    }
  }

  @Override
  public SlicingState getInitialState(CFANode node) {
    return SlicingAbstractedState.empty(fmgr, node);
  }

  @Override
  public boolean isLessOrEqual(SlicingState pState1, SlicingState pState2) {
    Preconditions.checkState(pState1.isAbstracted() == pState2.isAbstracted());

    if (pState1.isAbstracted()) {
      return isLessOrEqualAbstracted(pState1.asAbstracted(), pState2.asAbstracted());
    } else {
      return isLessOrEqualIntermediate(pState1.asIntermediate(), pState2.asIntermediate());
    }
  }

  private boolean isLessOrEqualIntermediate(
      SlicingIntermediateState pState1,
      SlicingIntermediateState pState2) {
    SlicingIntermediateState iState1 = pState1.asIntermediate();
    SlicingIntermediateState iState2 = pState2.asIntermediate();
    return (iState1.isMergedInto(iState2) ||
        iState1.getPathFormula().getFormula().equals(iState2.getPathFormula().getFormula()))
        && isLessOrEqualAbstracted(iState1.getAbstractParent(), iState2.getAbstractParent());
  }

  private boolean isLessOrEqualAbstracted(
      SlicingAbstractedState pState1,
      SlicingAbstractedState pState2
  ) {
    // More clauses => more constraints => the state is *smaller*.
    return pState1.getAbstraction().containsAll(pState2.getAbstraction());
  }

  private SlicingIntermediateState joinIntermediateStates(
      SlicingIntermediateState newState,
      SlicingIntermediateState oldState) throws InterruptedException {

    if (!newState.getAbstractParent().equals(oldState.getAbstractParent())) {

      // No merge.
      return oldState;
    }

    if (newState.isMergedInto(oldState)) {
      return oldState;
    } else if (oldState.isMergedInto(newState)) {
      return newState;
    }

    if (oldState.getPathFormula().equals(newState.getPathFormula())) {
      return newState;
    }
    PathFormula mergedPath = pfmgr.makeOr(newState.getPathFormula(),
        oldState.getPathFormula());

    SlicingIntermediateState out = SlicingIntermediateState.of(
        oldState.getNode(), mergedPath, oldState.getAbstractParent()
    );
    newState.setMergedInto(out);
    oldState.setMergedInto(out);
    return out;
  }

  private SlicingIntermediateState abstractStateToIntermediate(
      SlicingAbstractedState pSlicingAbstractedState) {
    return SlicingIntermediateState.of(
        pSlicingAbstractedState.getNode(),
        new PathFormula(
            bfmgr.makeBoolean(true),
            pSlicingAbstractedState.getSSA(),
            pSlicingAbstractedState.getPointerTargetSet(),
            0), pSlicingAbstractedState);
  }

  private boolean shouldPerformAbstraction(CFANode node, AbstractState pFullState) {

    LoopstackState loopState = AbstractStates.extractStateByType(pFullState,
        LoopstackState.class);
    Preconditions.checkState(loopState != null, "LoopstackCPA must be enabled for formula slicing"
        + " to work.");

    // Slicing is only performed on the loop heads.
    return loopStructure.getAllLoopHeads().contains(node)
        && loopState.isLoopCounterAbstracted();
  }

  @Override
  public SlicingState merge(SlicingState pState1, SlicingState pState2) throws InterruptedException {
    Preconditions.checkState(pState1.isAbstracted() == pState2.isAbstracted());

    if (pState1.isAbstracted()) {

      // No merge.
      return pState2;
    } else {
      SlicingIntermediateState iState1 = pState1.asIntermediate();
      SlicingIntermediateState iState2 = pState2.asIntermediate();
      return joinIntermediateStates(iState1, iState2);
    }
  }

  /**
   * Find a previous closest occurrence in ARG in the same partition, or
   * {@code Optional.absent()}
   */
  private Optional<SlicingAbstractedState> findOldToMerge
  (UnmodifiableReachedSet states, AbstractState pArgState, SlicingState state) {
    Set<SlicingAbstractedState> filteredSiblings =
        ImmutableSet.copyOf(
            AbstractStates.projectToType(
                states.getReached(pArgState),
                SlicingAbstractedState.class)
        );
    if (filteredSiblings.isEmpty()) {
      return Optional.absent();
    }

    // We follow the chain of backpointers until we intersect something in the
    // same partition.
    // The chain is necessary as we might have nested loops.
    SlicingState a = state;
    while (true) {
      if (a.isAbstracted()) {
        SlicingAbstractedState aState = a.asAbstracted();

        if (filteredSiblings.contains(aState)) {
          return Optional.of(aState);
        } else {
          if (!aState.getGeneratingState().isPresent()) {
            // Empty.
            return Optional.absent();
          }
          a = aState.getGeneratingState().get().getAbstractParent();
        }
      } else {
        SlicingIntermediateState iState = a.asIntermediate();
        a = iState.getAbstractParent();
      }
    }
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    pStatsCollection.add(statistics);
  }

  /**
   * Special handling for intermediate UFs as they can not be quantified.
   */
  private BooleanFormula removeLiteralsWithDeadUFs(
      BooleanFormula input, final SSAMap ssa) throws InterruptedException {
    input = fmgr.applyTactic(input, Tactic.NNF);
    return bfmgr.transformRecursively(
        new BooleanFormulaTransformationVisitor(fmgr) {

          @Override
          public BooleanFormula visitAnd(List<BooleanFormula> pOperands) {

            // TODO: extract common code for such operations.
            List<BooleanFormula> newOperands = new ArrayList<>();
            for (BooleanFormula op : pOperands) {
              if (!bfmgr.isTrue(op)) {
                newOperands.add(op);
              }
            }
            if (newOperands.size() == 0) {
              return bfmgr.makeBoolean(true);
            }
            return bfmgr.and(newOperands);
          }

          @Override
          public BooleanFormula visitOr(List<BooleanFormula> pOperands) {
            for (BooleanFormula op : pOperands) {
              if (bfmgr.isTrue(op)) {
                return bfmgr.makeBoolean(true);
              }
            }
            if (pOperands.size() == 0) {
              return bfmgr.makeBoolean(false);
            }
            return bfmgr.or(pOperands);
          }

          @Override
          public BooleanFormula visitAtom(
              BooleanFormula pAtom, FunctionDeclaration<BooleanFormula> decl) {
            if (hasDeadUf(pAtom, ssa)) {
              return bfmgr.makeBoolean(true);
            }
            return super.visitAtom(pAtom, decl);
          }

          @Override
          public BooleanFormula visitNot(BooleanFormula pOperand) {
            if (bfmgr.isTrue(pOperand)) {
              // TODO: temporary hacky bugfix.
              // pOperand is already _processed_ and we have no way of
              // knowing what it was before the processing.
              // TODO: change JavaSMT interface to return the formula both
              // before and after the processing.
              return pOperand;
            }
            return super.visitNot(pOperand);
          }
        }, input);
  }

  private boolean hasDeadUf(BooleanFormula atom, final SSAMap pSSAMap) {
    final AtomicBoolean out = new AtomicBoolean(false);
    fmgr.visitRecursively(new DefaultFormulaVisitor<TraversalProcess>() {
      @Override
      protected TraversalProcess visitDefault(Formula f) {
        return TraversalProcess.CONTINUE;
      }

      @Override
      public TraversalProcess visitFunction(
          Formula f,
          List<Formula> args,
          FunctionDeclaration<?> functionDeclaration) {
        if (functionDeclaration.getKind() == FunctionDeclarationKind.UF) {
          if (fmgr.isIntermediate(functionDeclaration.getName(), pSSAMap)) {
            out.set(true);
            return TraversalProcess.ABORT;
          }
        }
        return TraversalProcess.CONTINUE;
      }
    }, atom);
    return out.get();
  }
}
