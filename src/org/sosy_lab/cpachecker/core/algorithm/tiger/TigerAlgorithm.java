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
package org.sosy_lab.cpachecker.core.algorithm.tiger;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.ConfigurationBuilder;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.MoreFiles;
import org.sosy_lab.common.io.PathTemplate;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.WeavingLocation;
import org.sosy_lab.cpachecker.cfa.ast.AExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.CoreComponentsFactory;
import org.sosy_lab.cpachecker.core.MainCPAStatistics;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm;
import org.sosy_lab.cpachecker.core.algorithm.AlgorithmResult;
import org.sosy_lab.cpachecker.core.algorithm.AlgorithmWithResult;
import org.sosy_lab.cpachecker.core.algorithm.CEGARAlgorithm;
import org.sosy_lab.cpachecker.core.algorithm.counterexamplecheck.CounterexampleCheckAlgorithm;
import org.sosy_lab.cpachecker.core.algorithm.mpa.PropertyStats;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.PredefinedCoverageCriteria;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ast.Edges;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ast.FQLSpecification;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.SingletonECPEdgeSet;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.translators.GuardedEdgeLabel;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.translators.InverseGuardedEdgeLabel;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.translators.ecp.CoverageSpecificationTranslator;
import org.sosy_lab.cpachecker.core.algorithm.tiger.goals.Goal;
import org.sosy_lab.cpachecker.core.algorithm.tiger.goals.clustering.ClusteredElementaryCoveragePattern;
import org.sosy_lab.cpachecker.core.algorithm.tiger.goals.clustering.InfeasibilityPropagation;
import org.sosy_lab.cpachecker.core.algorithm.tiger.goals.clustering.InfeasibilityPropagation.Prediction;
import org.sosy_lab.cpachecker.core.algorithm.tiger.util.PrecisionCallback;
import org.sosy_lab.cpachecker.core.algorithm.tiger.util.PresenceConditions;
import org.sosy_lab.cpachecker.core.algorithm.tiger.util.TestCase;
import org.sosy_lab.cpachecker.core.algorithm.tiger.util.TestGoalUtils;
import org.sosy_lab.cpachecker.core.algorithm.tiger.util.TestStep;
import org.sosy_lab.cpachecker.core.algorithm.tiger.util.TestStep.AssignmentType;
import org.sosy_lab.cpachecker.core.algorithm.tiger.util.TestStep.VariableAssignment;
import org.sosy_lab.cpachecker.core.algorithm.tiger.util.TestSuite;
import org.sosy_lab.cpachecker.core.algorithm.tiger.util.ThreeValuedAnswer;
import org.sosy_lab.cpachecker.core.algorithm.tiger.util.WorkerRunnable;
import org.sosy_lab.cpachecker.core.algorithm.tiger.util.WorklistEntryComparator;
import org.sosy_lab.cpachecker.core.algorithm.tiger.util.Wrapper;
import org.sosy_lab.cpachecker.core.counterexample.CFAEdgeWithAssumptions;
import org.sosy_lab.cpachecker.core.counterexample.CFAPathWithAssumptions;
import org.sosy_lab.cpachecker.core.counterexample.CounterexampleInfo;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Property;
import org.sosy_lab.cpachecker.core.interfaces.Refiner;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.interfaces.WrapperCPA;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSetFactory;
import org.sosy_lab.cpachecker.cpa.arg.ARGCPA;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath.PathIterator;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGStatistics;
import org.sosy_lab.cpachecker.cpa.arg.ARGToDotWriter;
import org.sosy_lab.cpachecker.cpa.automaton.Automaton;
import org.sosy_lab.cpachecker.cpa.automaton.ControlAutomatonCPA;
import org.sosy_lab.cpachecker.cpa.automaton.InvalidAutomatonException;
import org.sosy_lab.cpachecker.cpa.automaton.MarkingAutomatonBuilder;
import org.sosy_lab.cpachecker.cpa.automaton.PowersetAutomatonCPA;
import org.sosy_lab.cpachecker.cpa.automaton.ReducedAutomatonProduct;
import org.sosy_lab.cpachecker.cpa.bdd.BDDCPA;
import org.sosy_lab.cpachecker.cpa.bdd.BDDTransferRelation;
import org.sosy_lab.cpachecker.cpa.composite.CompositeCPA;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateCPARefiner;
import org.sosy_lab.cpachecker.cpa.predicate.PredicatePrecision;
import org.sosy_lab.cpachecker.exceptions.CPAEnabledAnalysisPropertyViolationException;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.Precisions;
import org.sosy_lab.cpachecker.util.automaton.NondeterministicFiniteAutomaton;
import org.sosy_lab.cpachecker.util.automaton.NondeterministicFiniteAutomaton.State;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.presence.interfaces.PresenceCondition;
import org.sosy_lab.cpachecker.util.presence.interfaces.PresenceConditionManager;
import org.sosy_lab.cpachecker.util.resources.ProcessCpuTime;
import org.sosy_lab.cpachecker.util.statistics.AbstractStatistics;
import org.sosy_lab.cpachecker.util.statistics.StatCpuTime;
import org.sosy_lab.cpachecker.util.statistics.StatCpuTime.NoTimeMeasurement;
import org.sosy_lab.cpachecker.util.statistics.StatCpuTime.StatCpuTimer;
import org.sosy_lab.solver.SolverException;
import org.sosy_lab.solver.api.BooleanFormula;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.management.JMException;

@Options(prefix = "tiger")
public class TigerAlgorithm
    implements Algorithm, AlgorithmWithResult, PrecisionCallback<PredicatePrecision>, StatisticsProvider, Statistics {

  private class TigerStatistics extends AbstractStatistics {

    StatCpuTime acceptsTime = new StatCpuTime();
    StatCpuTime updateTestsuiteByCoverageOfTime = new StatCpuTime();
    StatCpuTime createTestcaseTime = new StatCpuTime();
    StatCpuTime addTestToSuiteTime = new StatCpuTime();
    StatCpuTime restrictBddTime = new StatCpuTime();
    StatCpuTime handleInfeasibleTestGoalTime = new StatCpuTime();
    StatCpuTime runAlgorithmWithLimitTime = new StatCpuTime();
    StatCpuTime runAlgorithmTime = new StatCpuTime();
    StatCpuTime initializeAlgorithmTime = new StatCpuTime();
    StatCpuTime initializeReachedSetTime = new StatCpuTime();
    StatCpuTime composeCPATime = new StatCpuTime();
    StatCpuTime testGenerationTime = new StatCpuTime();

    public TigerStatistics() {
      super();
    }

    @Override
    public void printStatistics(PrintStream pOut, Result pResult, ReachedSet pReached) {
      super.printStatistics(pOut, pResult, pReached);
      pOut.append("Time for test generation " + testGenerationTime + "\n");
      pOut.append("  Time for composing the CPA " + composeCPATime + "\n");
      pOut.append("  Time for initializing the reached set " + initializeReachedSetTime + "\n");
      pOut.append("  Time for initializing the algorithm " + initializeAlgorithmTime + "\n");
      pOut.append("  Time for running the CPA algorithm " + runAlgorithmTime + "\n");
      pOut.append("    Time for running the CPA algorithm with limit " + runAlgorithmWithLimitTime + "\n");
      pOut.append("    Time for handling infeasible goals " + handleInfeasibleTestGoalTime + "\n");
      pOut.append("    Time for restricting the BDD " + restrictBddTime + "\n");
      pOut.append("    Time for adding a test to the suite " + addTestToSuiteTime + "\n");
      pOut.append("      Time for creating a test case " + createTestcaseTime + "\n");
      pOut.append("      Time for updating the test coverage " + updateTestsuiteByCoverageOfTime + "\n");
      pOut.append("        Time for checking acceptance " + acceptsTime + "\n");
    }

  }

  private TigerStatistics tigerStats = new TigerStatistics();

  public static String originalMainFunction = null;

  @Option(secure = true, name = "fqlQuery", description = "Coverage criterion given as an FQL query")
  private String fqlQuery = PredefinedCoverageCriteria.BASIC_BLOCK_COVERAGE; // default is basic block coverage

  @Option(secure = true, name = "optimizeGoalAutomata", description = "Optimize the test goal automata")
  private boolean optimizeGoalAutomata = true;

  @Option(secure = true, name = "printARGperGoal", description = "Print the ARG for each test goal")
  private boolean dumpARGperPartition = false;

  @Option(secure = true, name = "useAutomataCrossProduct", description = "Compute the cross product of the goal automata?")
  private boolean useAutomataCrossProduct = false;

  @Option(secure = true, name = "useMarkingAutomata", description = "Compute the cross product of the goal automata?")
  private boolean useMarkingAutomata = false;

  @Option(
      secure = true,
      name = "checkCoverage",
      description = "Checks whether a test case for one goal covers another test goal")
  private boolean checkCoverage = true;

  @Option(secure = true, name = "usePowerset", description = "Construct the powerset of automata states.")
  private boolean usePowerset = false;

  @Option(secure = true, name = "useComposite", description = "Handle all automata CPAs as one composite CPA.")
  private boolean useComposite = false;

  @Option(secure = true, name = "testsuiteFile", description = "Filename for output of generated test suite")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private Path testsuiteFile = Paths.get("testsuite.txt");

  @Option(
      secure = true,
      name = "testcaseGeneartionTimesFile",
      description = "Filename for output of geneartion times of test cases")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private Path testcaseGenerationTimesFile = Paths.get("generationTimes.csv");

  @Option(
      secure = true,
      description = "File for saving processed goal automata in DOT format (%s will be replaced with automaton name)")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private PathTemplate dumpGoalAutomataTo = PathTemplate.ofFormatString("Automaton_%s.dot");

  @Option(
      secure = true,
      name = "useInfeasibilityPropagation",
      description = "Map information on infeasibility of one test goal to other test goals.")
  private boolean useInfeasibilityPropagation = false;

  enum TimeoutStrategy {
    SKIP_AFTER_TIMEOUT,
    RETRY_AFTER_TIMEOUT
  }

  @Option(
      secure = true,
      name = "timeoutStrategy",
      description = "How to proceed with timed-out goals if some time remains after processing all other goals.")
  private TimeoutStrategy timeoutStrategy = TimeoutStrategy.SKIP_AFTER_TIMEOUT;

  @Option(
      secure = true,
      name = "limitsPerGoal.time.cpu.increment",
      description = "Value for which timeout gets incremented if timed-out goals are re-processed.")
  private int timeoutIncrement = 0;

  /*@Option(name = "globalCoverageCheckBeforeTimeout", description = "Perform a coverage check on all remaining coverage goals before the global time out happens.")
  private boolean globalCoverageCheckBeforeTimeout = false;

  @Option(name = "timeForGlobalCoverageCheck", description = "Time budget for coverage check before global time out.")
  private String timeForGlobalCoverageCheck = "0s";*/

  @Option(
      secure = true,
      name = "limitsPerGoal.time.cpu",
      description = "Time limit per test goal in seconds (-1 for infinity).")
  private long cpuTimelimitPerGoal = -1;

  @Option(
      secure = true,
      name = "inverseOrder",
      description = "Inverses the order of test goals each time a new round of re-processing of timed-out goals begins.")
  private boolean inverseOrder = true;

  @Option(
      secure = true,
      name = "useOrder",
      description = "Enforce the original order each time a new round of re-processing of timed-out goals begins.")
  private boolean useOrder = true;

  @Option(
      secure = true,
      name = "algorithmConfigurationFile",
      description = "Configuration file for internal cpa algorithm.")
  @FileOption(FileOption.Type.REQUIRED_INPUT_FILE)
  private Path algorithmConfigurationFile = Paths.get("config/tiger-internal-algorithm.properties");

  @Option(
      secure = true,
      name = "tiger_with_presenceConditions",
      description = "Use Test Input Generator algorithm with an extension using the BDDCPA to model product line presence conditions.")
  public boolean useTigerAlgorithm_with_pc = false;

  @Option(
      secure = true,
      name = "useOmegaLabel",
      description = "Inserts the omega label at the end of each test goal automaton to enforce the tiger algorithm to generate only test cases from counterexamples that reach the end of the program.")
  public boolean useOmegaLabel = true;

  @Option(
      secure = true,
      name = "numberOfTestGoalsPerRun",
      description = "The number of test goals processed per CPAchecker run (0: all test goals in one run).")
  private int numberOfTestGoalsPerRun = 1;

  @Option(
      secure = true,
      name = "allCoveredGoalsPerTestCase",
      description = "Returns all test goals covered by a test case.")
  private boolean allCoveredGoalsPerTestCase = false;

  @Option(
      secure = true,
      name = "inputInterface",
      description = "List of input variables: v1,v2,v3...")
  private String inputInterface = "";

  @Option(
      secure = true,
      name = "outputInterface",
      description = "List of output variables: v1,v2,v3...")
  private String outputInterface = "";

  @Option(
      secure = true,
      name = "printLabels",
      description = "Prints labels reached with the error path of a test case.")
  private boolean printLabels = false;

  @Option(
      secure = true,
      name = "printPathFormulasPerGoal",
      description = "Writes all target state path formulas for a goal in a file.")
  private boolean printPathFormulasPerGoal = false;

  @Option(secure = true, name = "pathFormulasFile", description = "Filename for output of path formulas")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private Path pathFormulaFile = Paths.get("pathFormulas.txt");


  private final Configuration config;
  private final LogManager logger;
  final private ShutdownManager mainShutdownManager;
  private final MainCPAStatistics mainStats;
  private final CFA cfa;

  private ConfigurableProgramAnalysis cpa;
  private Refiner refiner;

  private CoverageSpecificationTranslator mCoverageSpecificationTranslator;
  private FQLSpecification fqlSpecification;

  private Wrapper wrapper;
  private GuardedEdgeLabel mAlphaLabel;
  private GuardedEdgeLabel mOmegaLabel;
  private InverseGuardedEdgeLabel mInverseAlphaLabel;

  private TestSuite testsuite;
  private ReachedSet reachedSet = null;
  private ReachedSet outsideReachedSet = null;
  private Set<String> inputVariables;
  private Set<String> outputVariables;

  private PredicatePrecision reusedPrecision = null;

  private int statistics_numberOfTestGoals;
  private int statistics_numberOfProcessedTestGoals = 0;
  private StatCpuTime statCpuTime = null;

  private Prediction[] lGoalPrediction;

  private String programDenotation;
  private int testCaseId = 0;

  private Map<Goal, List<List<BooleanFormula>>> targetStateFormulas;

  private TestGoalUtils testGoalUtils = null;
  private Map<CFAEdge, List<NondeterministicFiniteAutomaton<GuardedEdgeLabel>>> edgeToTgaMapping;

  private final ReachedSetFactory reachedSetFactory;

  public TigerAlgorithm(
      Algorithm pAlgorithm, ConfigurableProgramAnalysis pCpa,
      ShutdownManager pShutdownManager, CFA pCfa, Configuration pConfig, LogManager pLogger,
      String pProgramDenotation, ReachedSetFactory pReachedSetFactory, MainCPAStatistics pMainStats)
          throws InvalidConfigurationException {

    reachedSetFactory = pReachedSetFactory;
    programDenotation = pProgramDenotation;
    statCpuTime = new StatCpuTime();
    mainStats = pMainStats;

    mainShutdownManager = pShutdownManager;
    logger = pLogger;
    config = pConfig;
    config.inject(this);

    cpa = pCpa;
    cfa = pCfa;

    // Check if BDD is enabled for variability-aware test-suite generation

    testsuite = new TestSuite(printLabels, useTigerAlgorithm_with_pc);
    inputVariables = new TreeSet<>();
    for (String variable : inputInterface.split(",")) {
      inputVariables.add(variable.trim());
    }

    outputVariables = new TreeSet<>();
    for (String variable : outputInterface.split(",")) {
      outputVariables.add(variable.trim());
    }

    assert TigerAlgorithm.originalMainFunction != null;
    mCoverageSpecificationTranslator =
        new CoverageSpecificationTranslator(pCfa.getFunctionHead(TigerAlgorithm.originalMainFunction));

    wrapper = new Wrapper(pCfa, TigerAlgorithm.originalMainFunction);

    mAlphaLabel = new GuardedEdgeLabel(new SingletonECPEdgeSet(wrapper.getAlphaEdge()));
    mInverseAlphaLabel = new InverseGuardedEdgeLabel(mAlphaLabel);
    mOmegaLabel = new GuardedEdgeLabel(new SingletonECPEdgeSet(wrapper.getOmegaEdge()));

   edgeToTgaMapping = new HashMap<>();
   targetStateFormulas = new HashMap<>();

   testGoalUtils = new TestGoalUtils(logger, useTigerAlgorithm_with_pc, mAlphaLabel,
        mInverseAlphaLabel, mOmegaLabel);

    // get internal representation of FQL query
    fqlSpecification = testGoalUtils.parseFQLQuery(fqlQuery);
  }

  private PresenceConditionManager pcm() {
    return PresenceConditions.manager();
  }

  @Override
  public String getName() {
    return "TigerAlgorithm";
  }

  @Override
  public void setPrecision(PredicatePrecision pNewPrec) {
    reusedPrecision = pNewPrec;
  }

  @Override
  public PredicatePrecision getPrecision() {
    return reusedPrecision;
  }

  public long getCpuTime() {
    long cpuTime = -1;
    try {
      long currentCpuTime = (long) (ProcessCpuTime.read() / 1e6);
      long currentWallTime = System.currentTimeMillis();
      statCpuTime.onMeasurementResult(currentCpuTime - statCpuTime.getCpuTimeSum().asMillis(),
          currentWallTime - statCpuTime.getWallTimeSumMsec());
      cpuTime = statCpuTime.getCpuTimeSum().asMillis();
    } catch (NoTimeMeasurement | JMException e) {
      logger.logUserException(Level.WARNING, e, "Could not get CPU time for statistics.");
    }

    return cpuTime;
  }

  @Override
  public AlgorithmResult getResult() {
    return testsuite;
  }

  @Override
  public AlgorithmStatus run(ReachedSet pReachedSet) throws CPAException, InterruptedException {
    // we empty pReachedSet to stop complaints of an incomplete analysis
    // Problem: pReachedSet does not match the internal CPA structure!
    logger.logf(Level.INFO, "We will not use the provided reached set since it violates the internal structure of Tiger's CPAs");
    logger.logf(Level.INFO, "We empty pReachedSet to stop complaints of an incomplete analysis");

    outsideReachedSet = pReachedSet;
    outsideReachedSet.clear();

    statCpuTime.start();
    testsuite.setGenerationStartTime(getCpuTime());

    // Optimization: Infeasibility propagation
    Pair<Boolean, LinkedList<Edges>> lInfeasibilityPropagation = initializeInfisabilityPropagation();

    Set<Goal> goalsToCover = testGoalUtils.extractTestGoalPatterns(fqlSpecification, lGoalPrediction,
        lInfeasibilityPropagation, mCoverageSpecificationTranslator, optimizeGoalAutomata, useOmegaLabel, useTigerAlgorithm_with_pc);
    fillEdgeToTgaMapping(goalsToCover);

    statistics_numberOfTestGoals = goalsToCover.size();
    logger.logf(Level.INFO, "Number of test goals: %d", statistics_numberOfTestGoals);


    // (iii) do test generation for test goals ...
    boolean wasSound = true;
    try {
      if (!testGeneration(goalsToCover, lInfeasibilityPropagation)) {
        logger.logf(Level.WARNING, "Test generation contained unsound reachability analysis runs!");
        wasSound = false;
      }
    } catch (InvalidConfigurationException e1) {
      throw new CPAException("Invalid configuration!", e1);
    }

    // TODO: change testGeneration() such that it returns timedout if there was a timeout
//    assert (!testsuite.getTimedOutGoals().isEmpty() ? goalsToCover.isEmpty() : true);

    // Write generated test suite and mapping to file system
    dumpTestSuite();

    if (wasSound) {
      return AlgorithmStatus.SOUND_AND_PRECISE;
    } else {
      return AlgorithmStatus.UNSOUND_AND_PRECISE;
    }
  }

  private void fillEdgeToTgaMapping(Set<Goal> pGoalsToCover) {
    for (Goal goal : pGoalsToCover) {
      NondeterministicFiniteAutomaton<GuardedEdgeLabel> automaton = goal.getAutomaton();
      for (NondeterministicFiniteAutomaton<GuardedEdgeLabel>.Edge edge : automaton.getEdges()) {
        if (edge.getSource().equals(edge.getTarget())) {
          continue;
        }

        GuardedEdgeLabel label = edge.getLabel();
        for (CFAEdge e : label.getEdgeSet()) {
          List<NondeterministicFiniteAutomaton<GuardedEdgeLabel>> tgaSet = edgeToTgaMapping.get(e);

          if (tgaSet == null) {
            tgaSet = new ArrayList<>();
            edgeToTgaMapping.put(e, tgaSet);
          }

          tgaSet.add(automaton);
        }
      }
    }
  }

  private void dumpTestSuite() {
    if (testsuiteFile != null) {

      try (Writer writer = MoreFiles.openOutputFile(testsuiteFile, Charset.defaultCharset())) {
        writer.write(testsuite.toString());
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private Pair<Boolean, LinkedList<Edges>> initializeInfisabilityPropagation() {
    Pair<Boolean, LinkedList<Edges>> lInfeasibilityPropagation;

    if (useInfeasibilityPropagation) {
      lInfeasibilityPropagation = InfeasibilityPropagation.canApplyInfeasibilityPropagation(fqlSpecification);
    } else {
      lInfeasibilityPropagation = Pair.of(Boolean.FALSE, null);
    }

    return lInfeasibilityPropagation;
  }

  private ImmutableSet<Goal> nextTestGoalSet(Set<Goal> pGoalsToCover, TestSuite pSuite) {
    final int testGoalSetSize = (numberOfTestGoalsPerRun <= 0)
        ? pGoalsToCover.size()
        : (pGoalsToCover.size() > numberOfTestGoalsPerRun) ? numberOfTestGoalsPerRun : pGoalsToCover.size();

    Builder<Goal> builder = ImmutableSet.<Goal> builder();

    Iterator<Goal> it = pGoalsToCover.iterator();
    for (int i = 0; i < testGoalSetSize; i++) {
      if (it.hasNext()) {
        builder.add(it.next());
      }
    }

    ImmutableSet<Goal> result = builder.build();

    return result;
  }

  private boolean testGeneration(Set<Goal> pGoalsToCover,
      Pair<Boolean, LinkedList<Edges>> pInfeasibilityPropagation)
          throws CPAException, InterruptedException, InvalidConfigurationException {
    try (StatCpuTimer t = tigerStats.testGenerationTime.start()) {
      boolean wasSound = true;
      int numberOfTestGoals = pGoalsToCover.size();
      testsuite.addGoals(pGoalsToCover);

      NondeterministicFiniteAutomaton<GuardedEdgeLabel> previousAutomaton = null;
      boolean retry = false;

      do {
        if (retry) {
          // retry timed-out goals
          boolean order = true;

          if (timeoutIncrement > 0) {
            long oldCPUTimeLimitPerGoal = cpuTimelimitPerGoal;
            cpuTimelimitPerGoal += timeoutIncrement;
            logger.logf(Level.INFO, "Incremented timeout from %d to %d seconds.", oldCPUTimeLimitPerGoal,
                cpuTimelimitPerGoal);
            Collection<Entry<Integer, Pair<Goal, PresenceCondition>>> set;
            if (useOrder) {
              if (inverseOrder) {
                order = !order;
              }

              // keep original order of goals (or inverse of it)
              if (order) {
                set = new TreeSet<>(WorklistEntryComparator.ORDER_RESPECTING_COMPARATOR);
              } else {
                set = new TreeSet<>(WorklistEntryComparator.ORDER_INVERTING_COMPARATOR);
              }

              set.addAll(testsuite.getTimedOutGoals().entrySet());
            } else {
              set = new LinkedList<>();
              set.addAll(testsuite.getTimedOutGoals().entrySet());
            }

            pGoalsToCover.clear();
            for (Entry<Integer, Pair<Goal, PresenceCondition>> entry : set) {
              pGoalsToCover.add(entry.getValue().getFirst());
            }

            statistics_numberOfProcessedTestGoals -= testsuite.getTimedOutGoals().size();
            testsuite.prepareForRetryAfterTimeout();
          }
        }

        while (!pGoalsToCover.isEmpty()) {
          Set<Goal> goalsToBeProcessed = nextTestGoalSet(pGoalsToCover, testsuite);
          statistics_numberOfProcessedTestGoals += goalsToBeProcessed.size();
          pGoalsToCover.removeAll(goalsToBeProcessed);

          if (useTigerAlgorithm_with_pc) {
            /* force that a new reachedSet is computed when first starting on a new TestGoal with initial PC TRUE.
             * This enforces that no very constrained ARG is reused when computing a new ARG for a new testgoal with broad pc (TRUE).
             * This strategy allows us to set option tiger.reuseARG=true such that ARG is reused in testgoals (pcs get only more specific).
             * Keyword: overapproximation
             */
            //assert false;
            reachedSet = null;
          }

          String logString = "Processing test goals ";
          for (Goal g : goalsToBeProcessed) {
            logString += g.getIndex() + " (" + testsuite.getTestGoalLabel(g) + "), ";
          }
          logString = logString.substring(0, logString.length() - 2);

          if (useTigerAlgorithm_with_pc) {
//            Region remainingPresenceCondition =
//                BDDUtils.composeRemainingPresenceConditions(goalsToBeProcessed, testsuite, bddCpaNamedRegionManager);
            logger.logf(Level.INFO, "%s of %d for a PC.", logString, numberOfTestGoals);
          } else {
            logger.logf(Level.INFO, "%s of %d.", logString, numberOfTestGoals);
          }

          // TODO: enable tiger techniques for multi-goal generation in one run
          //        if (lGoalPrediction != null && lGoalPrediction[goal.getIndex() - 1] == Prediction.INFEASIBLE) {
          //          // GoalPrediction does not use the target presence condition (remainingPCforGoalCoverage)
          //          // I think this is OK (any infeasible goal will be even more infeasible when restricted with a certain pc)
          //          // TODO: remainingPCforGoalCoverage could perhaps be used to improve precision of the prediction?
          //          logger.logf(Level.INFO, "This goal is predicted as infeasible!");
          //          testsuite.addInfeasibleGoal(goal, goal.getRemainingPresenceCondition(), lGoalPrediction);
          //          continue;
          //        }
          //
          //        NondeterministicFiniteAutomaton<GuardedEdgeLabel> currentAutomaton = goal.getAutomaton();
          //        if (ARTReuse.isDegeneratedAutomaton(currentAutomaton)) {
          //          // current goal is for sure infeasible
          //          logger.logf(Level.INFO, "Test goal infeasible.");
          //          if (useTigerAlgorithm_with_pc) {
          //            logger.logf(Level.WARNING, "Goal %d is infeasible for remaining PC %s !", goal.getIndex(),
          //                bddCpaNamedRegionManager.dumpRegion(goal.getInfeasiblePresenceCondition()));
          //          }
          //          testsuite.addInfeasibleGoal(goal, goal.getRemainingPresenceCondition(), lGoalPrediction);
          //          continue; // we do not want to modify the ARG for the degenerated automaton to keep more reachability information
          //        }
          //
          //          if (checkCoverage) {
          //            for (Goal goalToBeChecked : goalsToBeProcessed) {
          //              if (checkAndCoverGoal(goalToBeChecked)) {
          //                if (useTigerAlgorithm_with_pc) {
          //                  pGoalsToCover.remove(goalToBeChecked);
          //                }
          //                if (lGoalPrediction != null) {
          //                  lGoalPrediction[goalToBeChecked.getIndex() - 1] = Prediction.FEASIBLE;
          //                }
          //              }
          //            }
          //          }

          //          if (testsuite.areGoalsCoveredOrInfeasible(goalsToBeProcessed)) {
          //            continue;
          //          }

          // goal is uncovered so far; run CPAchecker to cover it
          ReachabilityAnalysisResult result =
              runReachabilityAnalysis(pGoalsToCover, goalsToBeProcessed, previousAutomaton, pInfeasibilityPropagation);
          if (result.equals(ReachabilityAnalysisResult.UNSOUND)) {
            logger.logf(Level.WARNING, "Analysis run was unsound!");
            wasSound = false;
          }
          //        previousAutomaton = currentAutomaton;

        }

        // reprocess timed-out goals
        if (testsuite.getTimedOutGoals().isEmpty()) {
          logger.logf(Level.INFO, "There were no timed out goals.");
          retry = false;
        } else {
          if (!timeoutStrategy.equals(TimeoutStrategy.RETRY_AFTER_TIMEOUT)) {
            logger.logf(Level.INFO, "There were timed out goals but retry after timeout strategy is disabled.");
          } else {
            retry = true;
          }
        }
      } while (retry);

      return wasSound;
    }
  }

  @Nullable private ARGState findStateAfterCriticalEdge(Goal pCriticalForGoal, ARGPath pPath) {
    PathIterator it = pPath.pathIterator();

    final CFAEdge criticalEdge = pCriticalForGoal.getCriticalEdge();

    while (it.hasNext()) {
      if (it.getOutgoingEdge().equals(criticalEdge)) {
        ARGState afterCritical = it.getNextAbstractState();
        while (it.hasNext() && AbstractStates.extractLocation(it.getNextAbstractState()) instanceof WeavingLocation) {
          it.advance();
          afterCritical = it.getNextAbstractState();
          Preconditions.checkState(afterCritical != null);
        }
        return afterCritical;
      }
      it.advance();
    }

    return null;
  }

  private Set<Goal> updateTestsuiteByCoverageOf(TestCase pTestcase, Set<Goal> pCheckCoverageOf)
      throws InterruptedException {

    try (StatCpuTimer t = tigerStats.updateTestsuiteByCoverageOfTime.start()) {
      Set<Goal> checkCoverageOf = new HashSet<>();
      checkCoverageOf.addAll(pCheckCoverageOf);

      Set<Goal> coveredGoals = Sets.newLinkedHashSet();
      Set<Goal> goalsCoveredByLastState = Sets.newLinkedHashSet();

      ARGState lastState = pTestcase.getArgPath().getLastState();

      if (printPathFormulasPerGoal) {
        try {
          List<BooleanFormula> formulas = getPathFormula(pTestcase.getArgPath());

          Set<Property> violatedProperties = lastState.getViolatedProperties();

          for (Property property : violatedProperties) {
            Preconditions.checkState(property instanceof Goal);
            Goal g = (Goal) property;
            List<List<BooleanFormula>> f = targetStateFormulas.get(g);
            if (f == null) {
              f = new ArrayList<>();
              targetStateFormulas.put(g, f);
            }

            f.add(formulas);
          }
        } catch (CPAException | InterruptedException e) {
        }

        return new HashSet<>();
      }

      for (Property p : lastState.getViolatedProperties()) {
        Preconditions.checkState(p instanceof Goal);
        goalsCoveredByLastState.add((Goal) p);
      }

      checkCoverageOf.removeAll(goalsCoveredByLastState);

      if (!allCoveredGoalsPerTestCase) {
        for (Goal goal : pCheckCoverageOf) {
          if (testsuite.isGoalCovered(goal)) {
            checkCoverageOf.remove(goal);
          }
        }
      }

      Map<NondeterministicFiniteAutomaton<GuardedEdgeLabel>, AcceptStatus> acceptStati =
          accepts(checkCoverageOf, pTestcase.getErrorPath());

      for (Goal goal : goalsCoveredByLastState) {
        AcceptStatus acceptStatus = new AcceptStatus(goal);
        acceptStatus.answer = ThreeValuedAnswer.ACCEPT;
        acceptStati.put(goal.getAutomaton(), acceptStatus);
      }

      for (NondeterministicFiniteAutomaton<GuardedEdgeLabel> automaton : acceptStati.keySet()) {
        AcceptStatus acceptStatus = acceptStati.get(automaton);
        Goal goal = acceptStatus.goal;

        if (acceptStatus.answer.equals(ThreeValuedAnswer.UNKNOWN)) {
          logger.logf(Level.WARNING, "Coverage check for goal %d could not be performed in a precise way!",
              goal.getIndex());
          continue;
        } else if (acceptStatus.answer.equals(ThreeValuedAnswer.REJECT)) {
          continue;
        }

        // test goal is already covered by an existing test case
        if (useTigerAlgorithm_with_pc) {

          final ARGState criticalState = findStateAfterCriticalEdge(goal, pTestcase.getArgPath());

          if (criticalState == null) {
            Path argFile = Paths.get("output",
                "ARG_goal_criticalIsNull_" + Integer.toString(goal.getIndex()) + ".dot");

            final Set<Pair<ARGState, ARGState>> allTargetPathEdges = Sets.newLinkedHashSet();
            allTargetPathEdges.addAll(pTestcase.getArgPath().getStatePairs());

            try (Writer w = MoreFiles.openOutputFile(argFile, Charset.defaultCharset())) {
              ARGToDotWriter.write(w, (ARGState) reachedSet.getFirstState(),
                  ARGState::getChildren,
                  Predicates.alwaysTrue(),
                  Predicates.in(allTargetPathEdges));
            } catch (IOException e) {
              logger.logUserException(Level.WARNING, e, "Could not write ARG to file");
            }

            throw new RuntimeException(
                "Each ARG path of a counterexample must be along a critical edge! None for edge "
                    + goal.getCriticalEdge());
          }

          Preconditions.checkState(criticalState != null,
              "Each ARG path of a counterexample must be along a critical edge!");

          PresenceCondition statePresenceCondition = PresenceConditions.extractPresenceCondition(criticalState);
          statePresenceCondition = pcm().removeMarkerVariables(statePresenceCondition);

          Preconditions.checkState(statePresenceCondition != null,
              "Each critical state must be annotated with a presence condition!");

          if (allCoveredGoalsPerTestCase
              || pcm().checkConjunction(testsuite.getRemainingPresenceCondition(goal), statePresenceCondition)) {

            // configurations in testGoalPCtoCover and testcase.pc have a non-empty intersection

            testsuite.addTestCase(pTestcase, goal, statePresenceCondition);

            logger.logf(Level.WARNING,
                "Covered some PCs for Goal %d (%s) for a PC by test case %d!",
                goal.getIndex(), testsuite.getTestGoalLabel(goal), pTestcase.getId());

            if (pcm().checkEqualsFalse(testsuite.getRemainingPresenceCondition(goal))) {
              coveredGoals.add(goal);
            }
          }

        } else {
          testsuite.addTestCase(pTestcase, goal, null);
          logger.logf(Level.WARNING, "Covered Goal %d (%s) by test case %d!",
              goal.getIndex(),
              testsuite.getTestGoalLabel(goal),
              pTestcase.getId());
          coveredGoals.add(goal);
        }
      }

      return coveredGoals;
    }
  }

  private List<BooleanFormula> getPathFormula(ARGPath pPath) throws CPAException, InterruptedException {
    List<BooleanFormula> formulas = null;

    Refiner refiner = this.refiner;

    if (refiner instanceof PredicateCPARefiner) {
      final List<ARGState> abstractionStatesTrace = PredicateCPARefiner.filterAbstractionStates(pPath);
      formulas = ((PredicateCPARefiner) refiner).createFormulasOnPath(pPath, abstractionStatesTrace);
    }

    return formulas;
  }

  private class AcceptStatus {

    private Goal goal;
    private NondeterministicFiniteAutomaton<GuardedEdgeLabel> automaton;
    private Set<NondeterministicFiniteAutomaton.State> currentStates;
    boolean hasPredicates;
    private ThreeValuedAnswer answer;

    public AcceptStatus(Goal pGoal) {
      goal = pGoal;
      automaton = pGoal.getAutomaton();
      currentStates = Sets.newLinkedHashSet();
      hasPredicates = false;

      currentStates.add(automaton.getInitialState());
    }

    @Override
    public String toString() {
      return goal.getName() + ": " + answer;
    }

  }

  private Map<NondeterministicFiniteAutomaton<GuardedEdgeLabel>, AcceptStatus> accepts(
      Collection<Goal> pGoals, List<CFAEdge> pErrorPath) {
    try (StatCpuTimer t = tigerStats.acceptsTime.start()) {

      Map<NondeterministicFiniteAutomaton<GuardedEdgeLabel>, AcceptStatus> map = new HashMap<>();
      Set<NondeterministicFiniteAutomaton.State> lNextStates = Sets.newLinkedHashSet();

      Set<NondeterministicFiniteAutomaton<GuardedEdgeLabel>> automataWithResult = new HashSet<>();

      for (Goal goal : pGoals) {
        AcceptStatus acceptStatus = new AcceptStatus(goal);
        map.put(goal.getAutomaton(), acceptStatus);
        if (acceptStatus.automaton.getFinalStates().contains(acceptStatus.automaton.getInitialState())) {
          acceptStatus.answer = ThreeValuedAnswer.ACCEPT;
          automataWithResult.add(acceptStatus.automaton);
        }
      }

      for (CFAEdge lCFAEdge : pErrorPath) {
        List<NondeterministicFiniteAutomaton<GuardedEdgeLabel>> automata = edgeToTgaMapping.get(lCFAEdge);
        if (automata == null) {
          continue;
        }

        for (NondeterministicFiniteAutomaton<GuardedEdgeLabel> automaton : automata) {
          if (automataWithResult.contains(automaton)) {
            continue;
          }

          AcceptStatus acceptStatus = map.get(automaton);
          if (acceptStatus == null) {
            continue;
          }
          for (NondeterministicFiniteAutomaton.State lCurrentState : acceptStatus.currentStates) {
            for (NondeterministicFiniteAutomaton<GuardedEdgeLabel>.Edge lOutgoingEdge : automaton
                .getOutgoingEdges(lCurrentState)) {
              GuardedEdgeLabel lLabel = lOutgoingEdge.getLabel();

              if (lLabel.hasGuards()) {
                acceptStatus.hasPredicates = true;
              } else {
                if (lLabel.contains(lCFAEdge)) {
                  lNextStates.add(lOutgoingEdge.getTarget());
                  lNextStates.addAll(getSuccsessorsOfEmptyTransitions(automaton, lOutgoingEdge.getTarget()));

                  for (State nextState : lNextStates) {
                    // Automaton accepts as soon as it sees a final state (implicit self-loop)
                    if (automaton.getFinalStates().contains(nextState)) {
                      acceptStatus.answer = ThreeValuedAnswer.ACCEPT;
                      automataWithResult.add(automaton);
                    }
                  }
                }
              }
            }
          }

          acceptStatus.currentStates.addAll(lNextStates);
          lNextStates.clear();
        }
      }

      for (NondeterministicFiniteAutomaton<GuardedEdgeLabel> autom : map.keySet()) {
        if (automataWithResult.contains(autom)) {
          continue;
        }

        AcceptStatus accepts = map.get(autom);
        if (accepts.hasPredicates) {
          accepts.answer = ThreeValuedAnswer.UNKNOWN;
        } else {
          accepts.answer = ThreeValuedAnswer.REJECT;
        }
      }

      return map;
    }
  }

  private static Collection<? extends State> getSuccsessorsOfEmptyTransitions(NondeterministicFiniteAutomaton<GuardedEdgeLabel> pAutomaton, State pState) {
    Set<State> states = new HashSet<>();
    for (NondeterministicFiniteAutomaton<GuardedEdgeLabel>.Edge edge : pAutomaton.getOutgoingEdges(pState)) {
      GuardedEdgeLabel label = edge.getLabel();
      if (Pattern.matches("E\\d+ \\[\\]", label.toString())) {
        states.add(edge.getTarget());
      }
    }
    return states;
  }

  enum ReachabilityAnalysisResult {
    SOUND,
    UNSOUND,
    TIMEOUT
  }

  private ReachabilityAnalysisResult runReachabilityAnalysis(
      Set<Goal> pUncoveredGoals,
      Set<Goal> pTestGoalsToBeProcessed,
      NondeterministicFiniteAutomaton<GuardedEdgeLabel> pPreviousGoalAutomaton,
      Pair<Boolean, LinkedList<Edges>> pInfeasibilityPropagation)
      throws CPAException, InterruptedException, InvalidConfigurationException {

    ARGCPA cpa = composeCPA(pTestGoalsToBeProcessed);
    GlobalInfo.getInstance().setUpInfoFromCPA(cpa);

    Preconditions.checkState(cpa.getWrappedCPAs().get(0) instanceof CompositeCPA,
        "CPAcheckers automata should be used! The assumption is that the first component is the automata for the current goal!");

    // TODO: enable tiger techniques for multi-goal generation in one run
    //    if (reuseARG && (reachedSet != null)) {
    //      reuseARG(pTestGoalsToBeProcessed, pPreviousGoalAutomaton, lARTCPA);
    //    } else {
    initializeReachedSet(cpa);
    //    }

    PresenceCondition presenceConditionToCover = PresenceConditions.composeRemainingPresenceConditions(
        pTestGoalsToBeProcessed, testsuite);

    ShutdownManager shutdownManager =
        ShutdownManager.createWithParent(mainShutdownManager.getNotifier());
    Algorithm algorithm = initializeAlgorithm(presenceConditionToCover, cpa, shutdownManager);

    ReachabilityAnalysisResult algorithmStatus =
        runAlgorithm(pUncoveredGoals, pTestGoalsToBeProcessed, cpa, pInfeasibilityPropagation,
            presenceConditionToCover, shutdownManager, algorithm);

    return algorithmStatus;
  }

  private ReachabilityAnalysisResult runAlgorithm(
      Set<Goal> pUncoveredGoals,
      final Set<Goal> pTestGoalsToBeProcessed,
      final ARGCPA pARTCPA, Pair<Boolean, LinkedList<Edges>> pInfeasibilityPropagation,
      final PresenceCondition pRemainingPresenceCondition,
      final ShutdownManager pShutdownNotifier,
      final Algorithm pAlgorithm)
          throws CPAException, InterruptedException, CPAEnabledAnalysisPropertyViolationException {
    try (StatCpuTimer t = tigerStats.runAlgorithmTime.start()) {

      ReachabilityAnalysisResult algorithmStatus;

      do {
        // The wrapped algorithm (pAlgorithm) is (typically)
        // either the CEGAR algorithm,
        // or another algorithm that wraps the CEGAR algorithm.
        Preconditions.checkState(pAlgorithm instanceof CEGARAlgorithm
            || pAlgorithm instanceof CounterexampleCheckAlgorithm);

        algorithmStatus = runAlgorithmWithLimit(pShutdownNotifier, pAlgorithm);

        // Cases where runAlgorithm terminates:
        //  A) TIMEOUT
        //  B) Feasible counterexample
        //  C) No feasible counterexample (fixpoint)

        if (algorithmStatus != ReachabilityAnalysisResult.TIMEOUT) {

          boolean newTargetFound = (reachedSet.getLastState() != null)
              && ((ARGState) reachedSet.getLastState()).isTarget();

          if (reachedSet.hasWaitingState() && newTargetFound) {
            Preconditions.checkState(reachedSet.getLastState() instanceof ARGState);
            ARGState lastState = (ARGState) reachedSet.getLastState();
            Preconditions.checkState(lastState.isTarget());

            if (lastState.getCounterexampleInformation().isPresent()) {
              CounterexampleInfo cexi = lastState.getCounterexampleInformation().get();
            }

            if (!lastState.getCounterexampleInformation().isPresent()) {
              // No feasible counterexample!
              logger.logf(Level.WARNING,
                  "Analysis returned a target state (%d) without a feasible counterexample for: "
                      + lastState.getViolatedProperties(),
                  lastState.getStateId());
            } else {
              CounterexampleInfo cexi = lastState.getCounterexampleInformation().get();
              dumpArgForCex(cexi);

              Set<Goal> fullyCoveredGoals = null;
              if (allCoveredGoalsPerTestCase) {
                fullyCoveredGoals = addTestToSuite(testsuite.getGoals(), cexi, pInfeasibilityPropagation);
              } else if (checkCoverage) {
                fullyCoveredGoals =
                    addTestToSuite(Sets.union(pUncoveredGoals, pTestGoalsToBeProcessed), cexi, pInfeasibilityPropagation);
              } else {
                fullyCoveredGoals = addTestToSuite(pTestGoalsToBeProcessed, cexi, pInfeasibilityPropagation);
              }
              pUncoveredGoals.removeAll(fullyCoveredGoals);
            }
          }

          if (reachedSet.hasWaitingState()) {

            if (useTigerAlgorithm_with_pc) {
              PresenceCondition remainingPC =
                  PresenceConditions.composeRemainingPresenceConditions(pTestGoalsToBeProcessed, testsuite);
              restrictBdd(remainingPC);
            }
            // Exclude covered goals from further exploration
            Map<Property, Optional<PresenceCondition>> toBlacklist = Maps.newHashMap();
            for (Goal goal : pTestGoalsToBeProcessed) {

              if (testsuite.isGoalCoveredOrInfeasible(goal)) {
                toBlacklist.put(goal, Optional.of(pcm().makeTrue()));
              } else if (useTigerAlgorithm_with_pc) {
                PresenceCondition remainingPc = testsuite.getRemainingPresenceCondition(goal);
                PresenceCondition coveredFor = pcm().makeNegation(remainingPc);
                toBlacklist.put(goal, Optional.of(coveredFor));
              }
            }

            PropertyStats.INSTANCE.singnalPropertyFinishedFor(toBlacklist, pcm());
            Precisions.disablePropertiesForWaitlist(reachedSet, toBlacklist);
          }
        }

      } while ((reachedSet.hasWaitingState()
          && !testsuite.areGoalsCoveredOrInfeasible(pTestGoalsToBeProcessed))
          && (algorithmStatus != ReachabilityAnalysisResult.TIMEOUT));

      if (algorithmStatus == ReachabilityAnalysisResult.TIMEOUT) {
        logger.logf(Level.INFO, "Test goal timed out!");
        testsuite.addTimedOutGoals(pTestGoalsToBeProcessed);
      } else {
        // set test goals infeasible
        for (Goal goal : pTestGoalsToBeProcessed) {
          if (!testsuite.isGoalCovered(goal)) {
            handleInfeasibleTestGoal(goal, pInfeasibilityPropagation);
          }
        }
      }

      return algorithmStatus;
    }
  }

  private void dumpArgForCex(CounterexampleInfo cexi) {
//    Path argFile = Paths.get("output", "ARG_goal_" + Integer.toString(partitionId)  + ".dot");
//    try (Writer w = Files.openOutputFile(argFile)) {
//      final Set<Pair<ARGState, ARGState>> allTargetPathEdges = new HashSet<>();
//      allTargetPathEdges.addAll(cexi.getTargetPath().getStatePairs());
//
//      ARGToDotWriter.write(w, AbstractStates.extractStateByType(reachedSet.getFirstState(), ARGState.class),
//          ARGUtils.CHILDREN_OF_STATE,
//          Predicates.alwaysTrue(),
//          Predicates.in(allTargetPathEdges));
//    } catch (IOException e) {
//      logger.logUserException(Level.WARNING, e, "Could not write ARG to file");
//    }
  }

  private ReachabilityAnalysisResult runAlgorithmWithLimit(
      final ShutdownManager algNotifier,
      final Algorithm algorithm)
          throws CPAException, InterruptedException, CPAEnabledAnalysisPropertyViolationException {
    try (StatCpuTimer t = tigerStats.runAlgorithmWithLimitTime.start()) {

      ReachabilityAnalysisResult algorithmStatus;
      if (cpuTimelimitPerGoal < 0) {
        // run algorithm without time limit
        if (algorithm.run(reachedSet).isSound()) {
          algorithmStatus = ReachabilityAnalysisResult.SOUND;
        } else {
          algorithmStatus = ReachabilityAnalysisResult.UNSOUND;
        }
      } else {
        // run algorithm with time limit
        WorkerRunnable workerRunnable = new WorkerRunnable(algorithm, reachedSet, cpuTimelimitPerGoal, algNotifier);

        Thread workerThread = new Thread(workerRunnable);

        workerThread.start();
        workerThread.join();

        if (workerRunnable.throwableWasCaught()) {
          // TODO: handle exception
          algorithmStatus = ReachabilityAnalysisResult.UNSOUND;
          //        throw new RuntimeException(workerRunnable.getCaughtThrowable());
        } else {
          if (workerRunnable.analysisWasSound()) {
            algorithmStatus = ReachabilityAnalysisResult.SOUND;
          } else {
            algorithmStatus = ReachabilityAnalysisResult.UNSOUND;
          }

          if (workerRunnable.hasTimeout()) {
            algorithmStatus = ReachabilityAnalysisResult.TIMEOUT;
          }
        }
      }
      return algorithmStatus;
    }
  }

  private void restrictBdd(PresenceCondition pRemainingPresenceCondition) {
    try (StatCpuTimer t = tigerStats.restrictBddTime.start()) {
      // inject goal Presence Condition in BDDCPA
      BDDCPA bddcpa = null;
      if (cpa instanceof WrapperCPA) {
        // must be non-null, otherwise Exception in constructor of this class
        bddcpa = ((WrapperCPA) cpa).retrieveWrappedCpa(BDDCPA.class);
      } else if (cpa instanceof BDDCPA) {
        bddcpa = (BDDCPA) cpa;
      }
      if (bddcpa.getTransferRelation() instanceof BDDTransferRelation) {
        // FIXME. Set the constraint!
        logger.logf(Level.INFO, "Restrict global BDD. FIXME!!!");
//        logger.logf(Level.INFO, "Restrict BDD to %s.",
//            bddCpaNamedRegionManager.dumpRegion(pRemainingPresenceCondition));
      }
    }
  }

  private Algorithm initializeAlgorithm(PresenceCondition pRemainingPresenceCondition, ARGCPA lARTCPA,
      ShutdownManager algNotifier) throws CPAException {
    try (StatCpuTimer t = tigerStats.initializeAlgorithmTime.start()) {

      Algorithm algorithm;
      try {
        Configuration internalConfiguration = Configuration.builder().loadFromFile(algorithmConfigurationFile).build();

        CoreComponentsFactory coreFactory = new CoreComponentsFactory(internalConfiguration, logger, algNotifier.getNotifier());

        algorithm = coreFactory.createAlgorithm(lARTCPA, programDenotation, cfa, mainStats);

        if (algorithm instanceof CEGARAlgorithm) {
          CEGARAlgorithm cegarAlg = (CEGARAlgorithm) algorithm;

          this.refiner = cegarAlg.getRefiner();

          ARGStatistics lARTStatistics;
          try {
            lARTStatistics =
                new ARGStatistics(internalConfiguration, logger,
                    null, null, lARTCPA.getCexSummary());
          } catch (InvalidConfigurationException e) {
            throw new RuntimeException(e);
          }
          Set<Statistics> lStatistics = Sets.newLinkedHashSet();
          lStatistics.add(lARTStatistics);
          cegarAlg.collectStatistics(lStatistics);
        }

        if (useTigerAlgorithm_with_pc) {
          restrictBdd(pRemainingPresenceCondition);
        }
      } catch (IOException | InvalidConfigurationException e) {
        throw new RuntimeException(e);
      }
      return algorithm;
    }
  }

  private void initializeReachedSet(ARGCPA pArgCPA) {
    try (StatCpuTimer t = tigerStats.initializeReachedSetTime.start()) {
      // Create a new set 'reached' using the responsible factory.
      if (reachedSet != null) {
        reachedSet.clear();
      }
      reachedSet = reachedSetFactory.create();

      AbstractState initialState = pArgCPA.getInitialState(cfa.getMainFunction(), StateSpacePartition.getDefaultPartition());
      Precision initialPrec = pArgCPA.getInitialPrecision(cfa.getMainFunction(), StateSpacePartition.getDefaultPartition());

      reachedSet.add(initialState, initialPrec);
      outsideReachedSet.add(initialState, initialPrec);
    }
  }

  /**
   * Context:
   *  The analysis has identified a feasible counterexample, i.e., a test case.
   *
   * Add the test case to the test suite. This includes:
   *  * Register the test case for the goals that it reached on its last abstract state.
   *  * Add the test case for the goals that it would also cover;
   *    this gets checked by running all (uncovered) goal automata on the ARG path of the test case.
   *
   * @param pRemainingGoals
   * @param pCex
   * @param pInfeasibilityPropagation
   * @throws InterruptedException
   * @throws SolverException
   */
  private Set<Goal> addTestToSuite(Set<Goal> pRemainingGoals,
      CounterexampleInfo pCex, Pair<Boolean, LinkedList<Edges>> pInfeasibilityPropagation)
          throws InterruptedException {
    try (StatCpuTimer t = tigerStats.addTestToSuiteTime.start()) {

      Preconditions.checkNotNull(pInfeasibilityPropagation);
      Preconditions.checkNotNull(pRemainingGoals);
      Preconditions.checkNotNull(pCex);

      ARGState lastState = pCex.getTargetPath().getLastState();

      // TODO check whether a last state might remain from an earlier run and a reuse of the ARG

      PresenceCondition testCasePresenceCondition = null;
      if (useTigerAlgorithm_with_pc) {
        testCasePresenceCondition = PresenceConditions.extractPresenceCondition(lastState);
        testCasePresenceCondition = pcm().removeMarkerVariables(testCasePresenceCondition);
      }

      TestCase testcase = createTestcase(pCex, testCasePresenceCondition);
      Set<Goal> fullyCoveredGoals = updateTestsuiteByCoverageOf(testcase, pRemainingGoals);

      //        if (lGoalPrediction != null) {
      //          lGoalPrediction[pGoal.getIndex() - 1] = Prediction.FEASIBLE;
      //        }
      return fullyCoveredGoals;
    }
  }

  private TestCase createTestcase(final CounterexampleInfo pCex, final PresenceCondition pPresenceCondition) {
    try (StatCpuTimer t = tigerStats.createTestcaseTime.start()) {

      CFAPathWithAssumptions model = pCex.getCFAPathWithAssignments();
      final List<TestStep> testSteps = calculateTestSteps(model);

      TestCase testcase = new TestCase(testCaseId++,
          testSteps,
          pCex.getTargetPath(),
          pCex.getTargetPath().getInnerEdges(),
          pPresenceCondition,
          pcm(),
          getCpuTime());

      if (useTigerAlgorithm_with_pc) {
        logger.logf(Level.INFO, "Generated new test case %d with a PC in the last state.", testcase.getId());
      } else {
        logger.logf(Level.INFO, "Generated new test case %d.", testcase.getId());
      }

      return testcase;
    }
  }

  private List<TestStep> calculateTestSteps(CFAPathWithAssumptions path) {
    List<TestStep> testSteps = new ArrayList<>();

    boolean lastValueWasOuput = true;
    TestStep curStep = null;

    for (CFAEdgeWithAssumptions edge : path) {
      Collection<AExpressionStatement> expStmts = edge.getExpStmts();
      for (AExpressionStatement expStmt : expStmts) {
        if (expStmt.getExpression() instanceof CBinaryExpression) {
          CBinaryExpression exp = (CBinaryExpression) expStmt.getExpression();

          if (inputVariables.contains(exp.getOperand1().toString())) {
            if (lastValueWasOuput) {
              if (curStep != null) {
                testSteps.add(curStep);
              }
              curStep = new TestStep();
            }

            String variableName = exp.getOperand1().toString();
            BigInteger value = new BigInteger(exp.getOperand2().toString());
            VariableAssignment input = new VariableAssignment(variableName, value, AssignmentType.INPUT);
            curStep.addAssignment(input);

            lastValueWasOuput = false;
          } else if (outputVariables.contains(exp.getOperand1().toString())) {
            if (curStep == null) {
              curStep = new TestStep();
            }

            String variableName = exp.getOperand1().toString();
            BigInteger value = new BigInteger(exp.getOperand2().toString());
            VariableAssignment input = new VariableAssignment(variableName, value, AssignmentType.OUTPUT);
            curStep.addAssignment(input);

            lastValueWasOuput = true;
          }
        }
      }
    }

    if (curStep != null) {
      testSteps.add(curStep);
    }

    return testSteps;
  }

  private void handleInfeasibleTestGoal(Goal pGoal, Pair<Boolean, LinkedList<Edges>> pInfeasibilityPropagation) {
    try (StatCpuTimer t = tigerStats.handleInfeasibleTestGoalTime.start()) {
      if (lGoalPrediction != null) {
        lGoalPrediction[pGoal.getIndex() - 1] = Prediction.INFEASIBLE;
      }

      if (useTigerAlgorithm_with_pc) {
        testsuite.addInfeasibleGoal(pGoal, testsuite.getRemainingPresenceCondition(pGoal), lGoalPrediction);
        logger.logf(Level.WARNING, "Goal %d is infeasible for remaining PC!", pGoal.getIndex());
      } else {
        logger.logf(Level.WARNING, "Goal %d is infeasible!", pGoal.getIndex());
        testsuite.addInfeasibleGoal(pGoal, null, lGoalPrediction);
      }

      // TODO add missing soundness checks!
      if (pInfeasibilityPropagation.getFirst()) {
        logger.logf(Level.INFO, "Do infeasibility propagation!");
        Set<CFAEdge> lTargetEdges = Sets.newLinkedHashSet();
        ClusteredElementaryCoveragePattern lClusteredPattern =
            (ClusteredElementaryCoveragePattern) pGoal.getPattern();
        ListIterator<ClusteredElementaryCoveragePattern> lRemainingPatterns =
            lClusteredPattern.getRemainingElementsInCluster();
        int lTmpIndex = pGoal.getIndex() - 1; // caution lIndex starts at 0
        while (lRemainingPatterns.hasNext()) {
          Prediction lPrediction = lGoalPrediction[lTmpIndex];
          ClusteredElementaryCoveragePattern lRemainingPattern = lRemainingPatterns.next();
          if (lPrediction.equals(Prediction.UNKNOWN)) {
            lTargetEdges.add(lRemainingPattern.getLastSingletonCFAEdge());
          }

          lTmpIndex++;
        }
        Collection<CFAEdge> lFoundEdges =
            InfeasibilityPropagation.dfs2(lClusteredPattern.getCFANode(),
                lClusteredPattern.getLastSingletonCFAEdge(), lTargetEdges);
        lRemainingPatterns = lClusteredPattern.getRemainingElementsInCluster();
        lTmpIndex = pGoal.getIndex() - 1;
        while (lRemainingPatterns.hasNext()) {
          Prediction lPrediction = lGoalPrediction[lTmpIndex];
          ClusteredElementaryCoveragePattern lRemainingPattern = lRemainingPatterns.next();
          if (lPrediction.equals(Prediction.UNKNOWN)) {
            if (!lFoundEdges.contains(lRemainingPattern.getLastSingletonCFAEdge())) {
              //mFeasibilityInformation.setStatus(lTmpIndex+1, FeasibilityInformation.FeasibilityStatus.INFEASIBLE);
              // TODO remove ???
              lGoalPrediction[lTmpIndex] = Prediction.INFEASIBLE;
            }
          }
          lTmpIndex++;
        }
      }
    }
  }

  private void dumpAutomaton(Automaton pA) {
    if (dumpGoalAutomataTo == null) {
      return;
    }

    try (Writer w = MoreFiles.openOutputFile(dumpGoalAutomataTo.getPath(pA.getName()), Charset.defaultCharset())) {

      pA.writeDotFile(w);

    } catch (IOException e) {
      logger.logUserException(Level.WARNING, e, "Could not write the automaton to DOT file");
    }
  }

  private ARGCPA composeCPA(Set<Goal> pGoalsToBeProcessed) throws CPAException, InvalidConfigurationException {
    try (StatCpuTimer t = tigerStats.composeCPATime.start()) {

      Preconditions.checkArgument(cpa instanceof ARGCPA,
          "Tiger: Only support for ARGCPA implemented for CPA composition!");
      ARGCPA oldArgCPA = (ARGCPA) cpa;

      List<Automaton> componentAutomata = Lists.newArrayList();
      {
        List<Automaton> goalAutomata = Lists.newArrayList();

        for (Goal goal : pGoalsToBeProcessed) {
          Automaton a = goal.createControlAutomaton();
          if (useMarkingAutomata) {
            a = MarkingAutomatonBuilder.build(a);
          }

          goalAutomata.add(a);
          dumpAutomaton(a);
        }

        if (useAutomataCrossProduct) {
          final Automaton productAutomaton;
          try {
            logger.logf(Level.INFO, "Computing the cross product of %d automata.", pGoalsToBeProcessed.size());
            productAutomaton = ReducedAutomatonProduct.productOf(goalAutomata, "GOAL_PRODUCT");
            logger.logf(Level.INFO, "Cross product with %d states.", productAutomaton.getStates().size());
          } catch (InvalidAutomatonException e) {
            throw new CPAException("One of the automata is invalid!", e);
          }

          dumpAutomaton(productAutomaton);
          componentAutomata.add(productAutomaton);
        } else {
          componentAutomata.addAll(goalAutomata);
        }
      }

      logger.logf(Level.INFO, "Analyzing %d test goals with %d observer automata.", pGoalsToBeProcessed.size(),
          componentAutomata.size());

      List<ConfigurableProgramAnalysis> automataCPAs = Lists.newArrayList();

      for (Automaton componentAutomaton : componentAutomata) {

        final CPAFactory automataFactory = usePowerset
            ? PowersetAutomatonCPA.factory()
            : ControlAutomatonCPA.factory();

        automataFactory.setConfiguration(Configuration.copyWithNewPrefix(config, componentAutomaton.getName()));
        automataFactory.setLogger(logger.withComponentName(componentAutomaton.getName()));
        automataFactory.set(cfa, CFA.class);
        automataFactory.set(componentAutomaton, Automaton.class);

        automataCPAs.add(automataFactory.createInstance());
      }

      // Add one automata CPA for each goal
      LinkedList<ConfigurableProgramAnalysis> lComponentAnalyses = new LinkedList<>();
      if (useComposite) {
        ConfigurationBuilder compConfigBuilder = Configuration.builder();
        compConfigBuilder.setOption("cpa.composite.separateTargetStates", "true");
        Configuration compositeConfig = compConfigBuilder.build();

        CPAFactory compositeCpaFactory = CompositeCPA.factory();
        compositeCpaFactory.setChildren(automataCPAs);
        compositeCpaFactory.setConfiguration(compositeConfig);
        compositeCpaFactory.setLogger(logger);
        compositeCpaFactory.set(cfa, CFA.class);

        ConfigurableProgramAnalysis compositeAutomatonCPA = compositeCpaFactory.createInstance();
        lComponentAnalyses.add(compositeAutomatonCPA);
      } else {
        lComponentAnalyses.addAll(automataCPAs);
      }

      // Add the old composite components
      Preconditions.checkState(oldArgCPA.getWrappedCPAs().iterator().next() instanceof CompositeCPA);
      CompositeCPA argCompositeCpa = (CompositeCPA) oldArgCPA.getWrappedCPAs().iterator().next();
      lComponentAnalyses.addAll(argCompositeCpa.getWrappedCPAs());

      final ARGCPA result;

      try {
        // create composite CPA
        CPAFactory compositeCpaFactory = CompositeCPA.factory();
        compositeCpaFactory.setChildren(lComponentAnalyses);
        compositeCpaFactory.setConfiguration(config);
        compositeCpaFactory.setLogger(logger);
        compositeCpaFactory.set(cfa, CFA.class);

        ConfigurableProgramAnalysis lCPA = compositeCpaFactory.createInstance();

        // create ARG CPA
        CPAFactory lARTCPAFactory = ARGCPA.factory();
        lARTCPAFactory.set(cfa, CFA.class);
        lARTCPAFactory.setChild(lCPA);
        lARTCPAFactory.setConfiguration(config);
        lARTCPAFactory.setLogger(logger);

        result = (ARGCPA) lARTCPAFactory.createInstance();

      } catch (InvalidConfigurationException | CPAException e) {
        throw new RuntimeException(e);
      }

      return result;
    }
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    pStatsCollection.add(this);
  }

  @Override
  public void printStatistics(PrintStream pOut, Result pResult, ReachedSet pReached) {

    pOut.println("Number of test cases:                              " + testsuite.getNumberOfTestCases());
    pOut.println("Number of test goals:                              " + statistics_numberOfTestGoals);
    pOut.println("Number of processed test goals:                    " + statistics_numberOfProcessedTestGoals);

   if (useTigerAlgorithm_with_pc) {
      pOut.println("Number of feasible test goals:                     " + testsuite.getNumberOfFeasibleTestGoals());
      pOut.println("Number of partially feasible test goals:           " + testsuite.getNumberOfPartiallyFeasibleTestGoals());
      pOut.println("Number of infeasible test goals:                   " + testsuite.getNumberOfInfeasibleTestGoals());
      pOut.println("Number of partially infeasible test goals:         " + testsuite.getNumberOfPartiallyInfeasibleTestGoals());
      pOut.println("Number of timedout test goals:                     " + testsuite.getNumberOfTimedoutTestGoals());
      pOut.println("Number of partially timedout test goals:           " + testsuite.getNumberOfPartiallyTimedOutTestGoals());

      if (testsuite.getNumberOfTimedoutTestGoals() > 0 || testsuite.getNumberOfPartiallyTimedOutTestGoals() > 0) {
        pOut.println("Timeout occured during processing of a test goal!");
      }
    } else {
      pOut.println("Number of feasible test goals:                     " + testsuite.getNumberOfFeasibleTestGoals());
      pOut.println("Number of infeasible test goals:                   " + testsuite.getNumberOfInfeasibleTestGoals());
      pOut.println("Number of timedout test goals:                     " + testsuite.getNumberOfTimedoutTestGoals());

      if (testsuite.getNumberOfTimedoutTestGoals() > 0) {
        pOut.println("Timeout occured during processing of a test goal!");
      }
    }

    tigerStats.printStatistics(pOut, pResult, pReached);

    dumpTestSuite();

    if (printPathFormulasPerGoal) {
      dumpPathFormulas();
    }

    // write test case generation times to file system
    if (testcaseGenerationTimesFile != null) {
      try (Writer writer = MoreFiles.openOutputFile(testcaseGenerationTimesFile, Charset.defaultCharset())) {

        List<TestCase> testcases = new ArrayList<>(testsuite.getTestCases());
        Collections.sort(testcases, new Comparator<TestCase>() {

          @Override
          public int compare(TestCase pTestCase1, TestCase pTestCase2) {
            if (pTestCase1.getGenerationTime() > pTestCase2.getGenerationTime()) {
              return 1;
            } else if (pTestCase1.getGenerationTime() < pTestCase2.getGenerationTime()) { return -1; }
            return 0;
          }
        });

        if (useTigerAlgorithm_with_pc) {
          Set<Goal> feasible = Sets.newLinkedHashSet();
          feasible.addAll(testsuite.getFeasibleGoals());
          feasible.addAll(testsuite.getPartiallyFeasibleGoals());
          feasible.removeAll(testsuite.getPartiallyTimedOutGoals());
          for (Goal goal : feasible) {
            List<TestCase> tests = testsuite.getCoveringTestCases(goal);
            TestCase lastTestCase = getLastTestCase(tests);
            lastTestCase.incrementNumberOfNewlyCoveredGoals();
          }
          Set<Goal> partially = Sets.newLinkedHashSet();
          partially.addAll(testsuite.getFeasibleGoals());
          partially.addAll(testsuite.getPartiallyFeasibleGoals());
          partially.removeAll(testsuite.getPartiallyTimedOutGoals());
          for (Goal goal : partially) {
            List<TestCase> tests = testsuite.getCoveringTestCases(goal);
            TestCase lastTestCase = getLastTestCase(tests);
            lastTestCase.incrementNumberOfNewlyPartiallyCoveredGoals();
          }

          writer.write(
              "Test Case;Generation Time;Covered Goals After Generation;Completely Covered Goals After Generation;Partially Covered Goals After Generation\n");
          int completelyCoveredGoals = 0;
          int partiallyCoveredGoals = 0;
          for (TestCase testCase : testcases) {
            int newCoveredGoals = testCase.getNumberOfNewlyCoveredGoals();
            int newPartiallyCoveredGoals = testCase.getNumberOfNewlyPartiallyCoveredGoals();
            completelyCoveredGoals += newCoveredGoals;
            partiallyCoveredGoals += newPartiallyCoveredGoals;

            writer.write(testCase.getId() + ";" + testCase.getGenerationTime() + ";"
                + (completelyCoveredGoals + partiallyCoveredGoals) + ";" + completelyCoveredGoals + ";"
                + partiallyCoveredGoals + "\n");
          }
        } else {
          Set<Goal> coveredGoals = Sets.newLinkedHashSet();
          writer.write("Test Case;Generation Time;Covered Goals After Generation\n");
          for (TestCase testCase : testcases) {
            coveredGoals.addAll(testsuite.getTestGoalsCoveredByTestCase(testCase));
            writer.write(testCase.getId() + ";" + testCase.getGenerationTime() + ";" + coveredGoals.size() + "\n");
          }
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private void dumpPathFormulas() {
    if (pathFormulaFile != null) {
      StringBuffer buffer = new StringBuffer();
      for (Goal goal : targetStateFormulas.keySet()) {
        buffer.append("GOAL " + goal + "\n");
        for (List<BooleanFormula> formulas : targetStateFormulas.get(goal)) {
          buffer.append("FORMULA\n");
          for (BooleanFormula formula : formulas) {
            buffer.append(formula + "\n");
          }
        }
      }

      try (Writer writer = MoreFiles.openOutputFile(pathFormulaFile, Charset.defaultCharset())) {
        writer.write(buffer.toString());
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private TestCase getLastTestCase(List<TestCase> pTests) {
    TestCase lastTestCase = null;
    for (TestCase testCase : pTests) {
      if (lastTestCase == null || testCase.getGenerationTime() < lastTestCase.getGenerationTime()) {
        lastTestCase = testCase;
      }
    }
    return lastTestCase;
  }

}
