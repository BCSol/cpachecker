/*
 *  CPAchecker is a tool for configurable software verification.
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
package org.sosy_lab.cpachecker.core.algorithm.tiger;

import com.google.common.collect.Lists;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.Nullable;
import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.core.CoreComponentsFactory;
import org.sosy_lab.cpachecker.core.Specification;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm;
import org.sosy_lab.cpachecker.core.algorithm.CEGARAlgorithm;
import org.sosy_lab.cpachecker.core.algorithm.testgen.util.StartupConfig;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.FQLSpecificationUtil;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.PredefinedCoverageCriteria;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ast.FQLSpecification;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.ElementaryCoveragePattern;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.SingletonECPEdgeSet;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.translators.GuardedEdgeLabel;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.translators.InverseGuardedEdgeLabel;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.translators.ToGuardedAutomatonTranslator;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.translators.ecp.CoverageSpecificationTranslator;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.translators.ecp.IncrementalCoverageSpecificationTranslator;
import org.sosy_lab.cpachecker.core.algorithm.tiger.goals.Goal;
import org.sosy_lab.cpachecker.core.algorithm.tiger.util.TestSuite;
import org.sosy_lab.cpachecker.core.algorithm.tiger.util.WorkerRunnable;
import org.sosy_lab.cpachecker.core.algorithm.tiger.util.Wrapper;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Refiner;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.AggregatedReachedSets;
import org.sosy_lab.cpachecker.core.reachedset.LocationMappedReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.core.waitlist.Waitlist;
import org.sosy_lab.cpachecker.cpa.arg.ARGCPA;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGStatistics;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.cpa.automaton.Automaton;
import org.sosy_lab.cpachecker.cpa.automaton.ControlAutomatonCPA;
import org.sosy_lab.cpachecker.cpa.composite.CompositeCPA;
import org.sosy_lab.cpachecker.cpa.guardededgeautomaton.productautomaton.ProductAutomatonCPA;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateCPARefiner;
import org.sosy_lab.cpachecker.exceptions.CPAEnabledAnalysisPropertyViolationException;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.automaton.NondeterministicFiniteAutomaton;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Region;

@Options(prefix = "tiger")
public class TigerAlgorithm implements Algorithm {

  @Option(
      secure = true,
      name = "fqlQuery",
      description = "Coverage criterion given as an FQL query")
  private String fqlQuery = PredefinedCoverageCriteria.BASIC_BLOCK_COVERAGE; // default is basic block coverage

  @Option(
      secure = true,
      name = "optimizeGoalAutomata",
      description = "Optimize the test goal automata")
  private boolean optimizeGoalAutomata = true;

  @Option(
      secure = true,
      name = "limitsPerGoal.time.cpu",
      description = "Time limit per test goal in seconds (-1 for infinity).")
  private long cpuTimelimitPerGoal = -1;

  @Option(
      secure = true,
      name = "algorithmConfigurationFile",
      description = "Configuration file for internal cpa algorithm.")
  @FileOption(FileOption.Type.REQUIRED_INPUT_FILE)
  private Path algorithmConfigurationFile = Paths.get("config/tiger-internal-algorithm.properties");

  @Option(secure = true, name = "reuseARG", description = "Reuse ARG across test goals")
  private boolean reuseARG = true;

  private FQLSpecification fqlSpecification;
  private final LogManager logger;
  private final CFA cfa;
  private ConfigurableProgramAnalysis cpa;
  private CoverageSpecificationTranslator mCoverageSpecificationTranslator;
  public static String originalMainFunction = null;
  private int statistics_numberOfTestGoals;
  private Wrapper wrapper;
  private GuardedEdgeLabel mAlphaLabel;
  private GuardedEdgeLabel mOmegaLabel;
  private InverseGuardedEdgeLabel mInverseAlphaLabel;
  private final Configuration config;
  private ReachedSet outsideReachedSet = null;
  private ReachedSet reachedSet = null;
  private StartupConfig startupConfig;
  private String programDenotation;
  private Specification stats;
  private TestSuite testsuite;

  enum ReachabilityAnalysisResult {
    SOUND,
    UNSOUND,
    TIMEDOUT
  }

  public TigerAlgorithm(LogManager pLogger, CFA pCfa, Configuration pConfig,
      ConfigurableProgramAnalysis pCpa, ShutdownNotifier pShutdownNotifier,
      String programDenotation, @Nullable final Specification stats)
      throws InvalidConfigurationException {
    cfa = pCfa;
    cpa = pCpa;
    startupConfig = new StartupConfig(pConfig, pLogger, pShutdownNotifier);
    startupConfig.getConfig().inject(this);
    logger = pLogger;
    assert TigerAlgorithm.originalMainFunction != null;
    mCoverageSpecificationTranslator =
        new CoverageSpecificationTranslator(
            pCfa.getFunctionHead(TigerAlgorithm.originalMainFunction));
    wrapper = new Wrapper(pCfa, TigerAlgorithm.originalMainFunction);
    mAlphaLabel = new GuardedEdgeLabel(new SingletonECPEdgeSet(wrapper.getAlphaEdge()));
    mInverseAlphaLabel = new InverseGuardedEdgeLabel(mAlphaLabel);
    mOmegaLabel = new GuardedEdgeLabel(new SingletonECPEdgeSet(wrapper.getOmegaEdge()));
    config = pConfig;
    config.inject(this);
    logger.logf(Level.INFO, "FQL query string: %s", fqlQuery);
    fqlSpecification = FQLSpecificationUtil.getFQLSpecification(fqlQuery);
    logger.logf(Level.INFO, "FQL query: %s", fqlSpecification.toString());
    this.programDenotation = programDenotation;
    this.stats = stats;
    testsuite = new TestSuite(null);
  }

  @Override
  public AlgorithmStatus run(ReachedSet pReachedSet)
      throws CPAException, InterruptedException, CPAEnabledAnalysisPropertyViolationException {
    LinkedList<ElementaryCoveragePattern> goalPatterns;
    LinkedList<Pair<ElementaryCoveragePattern, Region>> pTestGoalPatterns = new LinkedList<>();
    logger.logf(Level.INFO,
        "We will not use the provided reached set since it violates the internal structure of Tiger's CPAs");
    logger.logf(Level.INFO, "We empty pReachedSet to stop complaints of an incomplete analysis");
    outsideReachedSet = pReachedSet;
    outsideReachedSet.clear();

    goalPatterns = extractTestGoalPatterns(fqlSpecification);

    for (int i = 0; i < goalPatterns.size(); i++) {
      pTestGoalPatterns.add(Pair.of(goalPatterns.get(i), (Region) null));
    }

    int goalIndex = 1;
    LinkedList<Goal> pGoalsToCover = new LinkedList<>();
    for (Pair<ElementaryCoveragePattern, Region> pair : pTestGoalPatterns) {
      Goal lGoal =
          constructGoal(goalIndex, pair.getFirst(), mAlphaLabel, mInverseAlphaLabel, mOmegaLabel,
              optimizeGoalAutomata,
              pair.getSecond());
      logger.log(Level.INFO, lGoal.getName());
      pGoalsToCover.add(lGoal);
      goalIndex++;
    }

    for (Goal goal : pGoalsToCover) {
      try {
        runReachabilityAnalysis(goal, goal.getIndex());
      } catch (InvalidConfigurationException e) {
        logger.log(Level.SEVERE, "Failed to run reachability analysis!");
      }
    }

    return AlgorithmStatus.SOUND_AND_PRECISE;
  }

  private Goal constructGoal(int pIndex, ElementaryCoveragePattern pGoalPattern,
      GuardedEdgeLabel pAlphaLabel, InverseGuardedEdgeLabel pInverseAlphaLabel,
      GuardedEdgeLabel pOmegaLabel, boolean pUseAutomatonOptimization, Region pPresenceCondition) {
    NondeterministicFiniteAutomaton<GuardedEdgeLabel> automaton =
        ToGuardedAutomatonTranslator.toAutomaton(pGoalPattern, pAlphaLabel, pInverseAlphaLabel,
            pOmegaLabel);
    automaton = FQLSpecificationUtil.optimizeAutomaton(automaton, pUseAutomatonOptimization);

    Goal lGoal = new Goal(pIndex, pGoalPattern, automaton, pPresenceCondition);

    return lGoal;
  }

  private LinkedList<ElementaryCoveragePattern> extractTestGoalPatterns(
      FQLSpecification pFqlSpecification) {
    logger.logf(Level.INFO, "Extracting test goals.");


    // TODO check for (temporarily) unsupported features

    // TODO enable use of infeasibility propagation


    IncrementalCoverageSpecificationTranslator lTranslator =
        new IncrementalCoverageSpecificationTranslator(
            mCoverageSpecificationTranslator.mPathPatternTranslator);

    statistics_numberOfTestGoals =
        lTranslator.getNumberOfTestGoals(pFqlSpecification.getCoverageSpecification());
    logger.logf(Level.INFO, "Number of test goals: %d", statistics_numberOfTestGoals);

    Iterator<ElementaryCoveragePattern> lGoalIterator =
        lTranslator.translate(pFqlSpecification.getCoverageSpecification());
    LinkedList<ElementaryCoveragePattern> lGoalPatterns = new LinkedList<>();

    for (int lGoalIndex = 0; lGoalIndex < statistics_numberOfTestGoals; lGoalIndex++) {
      lGoalPatterns.add(lGoalIterator.next());
    }

    return lGoalPatterns;
  }

  private ReachabilityAnalysisResult runReachabilityAnalysis(Goal pGoal, int goalIndex)
      throws CPAException, InterruptedException, InvalidConfigurationException {
    Automaton goalAutomaton = pGoal.createControlAutomaton();
    Specification goalAutomatonSpecification = Specification.fromAutomata(Lists.newArrayList(goalAutomaton));

    CPAFactory automataFactory = ControlAutomatonCPA.factory();
    automataFactory
        .setConfiguration(Configuration.copyWithNewPrefix(config, goalAutomaton.getName()));
    automataFactory.setLogger(logger.withComponentName(goalAutomaton.getName()));
    automataFactory.set(cfa, CFA.class);
    automataFactory.set(goalAutomaton, Automaton.class);

    List<ConfigurableProgramAnalysis> lAutomatonCPAs = new ArrayList<>(1);//(2);
    try {
      lAutomatonCPAs.add(automataFactory.createInstance());
    } catch (InvalidConfigurationException e1) {
      throw new CPAException("Invalid automata!", e1);
    }

    LinkedList<ConfigurableProgramAnalysis> lComponentAnalyses = new LinkedList<>();
    int lProductAutomatonIndex = lComponentAnalyses.size();
    lComponentAnalyses.add(ProductAutomatonCPA.create(lAutomatonCPAs, false, config));

    if (cpa instanceof CompositeCPA) {
      CompositeCPA compositeCPA = (CompositeCPA) cpa;
      lComponentAnalyses.addAll(compositeCPA.getWrappedCPAs());
    } else if (cpa instanceof ARGCPA) {
      lComponentAnalyses.addAll(((ARGCPA) cpa).getWrappedCPAs());
    } else {
      lComponentAnalyses.add(cpa);
    }

    ARGCPA lARTCPA;
    try {
      // create composite CPA
      CPAFactory lCPAFactory = CompositeCPA.factory();
      lCPAFactory.setChildren(lComponentAnalyses);
      lCPAFactory.setConfiguration(startupConfig.getConfig());
      lCPAFactory.setLogger(logger);
      lCPAFactory.set(cfa, CFA.class);

      ConfigurableProgramAnalysis lCPA = lCPAFactory.createInstance();

      // create ART CPA
      CPAFactory lARTCPAFactory = ARGCPA.factory();
      lARTCPAFactory.set(cfa, CFA.class);
      lARTCPAFactory.setChild(lCPA);
      lARTCPAFactory.setConfiguration(startupConfig.getConfig());
      lARTCPAFactory.setLogger(logger);
      lARTCPAFactory.set(goalAutomatonSpecification, Specification.class);

      lARTCPA = (ARGCPA) lARTCPAFactory.createInstance();
    } catch (InvalidConfigurationException | CPAException e) {
      throw new RuntimeException(e);
    }

    reachedSet = new LocationMappedReachedSet(Waitlist.TraversalMethod.BFS); // TODO why does TOPSORT not exist anymore?
    AbstractState lInitialElement =
        lARTCPA.getInitialState(cfa.getMainFunction(), StateSpacePartition.getDefaultPartition());
    Precision lInitialPrecision = lARTCPA.getInitialPrecision(cfa.getMainFunction(),
        StateSpacePartition.getDefaultPartition());

    reachedSet.add(lInitialElement, lInitialPrecision);

    outsideReachedSet.add(lInitialElement, lInitialPrecision);

    ShutdownManager algNotifier =
        ShutdownManager.createWithParent(startupConfig.getShutdownNotifier());

    startupConfig.getConfig();

    Algorithm algorithm;

    try {
      Configuration internalConfiguration =
          Configuration.builder().loadFromFile(algorithmConfigurationFile).build();

      Set<UnmodifiableReachedSet> unmodifiableReachedSets = new HashSet<>();

      unmodifiableReachedSets.add(reachedSet);

      AggregatedReachedSets aggregatedReachedSets =
          new AggregatedReachedSets(unmodifiableReachedSets);

      CoreComponentsFactory coreFactory = new CoreComponentsFactory(internalConfiguration, logger,
          algNotifier.getNotifier(), aggregatedReachedSets);

      algorithm = coreFactory.createAlgorithm(lARTCPA, programDenotation, cfa, stats);

      if (algorithm instanceof CEGARAlgorithm) {
        CEGARAlgorithm cegarAlg = (CEGARAlgorithm) algorithm;

        Refiner refiner = cegarAlg.getmRefiner();
        if (refiner instanceof PredicateCPARefiner) {
          PredicateCPARefiner predicateRefiner = (PredicateCPARefiner) refiner;
        }

        ARGStatistics lARTStatistics;
        try {
          lARTStatistics = new ARGStatistics(internalConfiguration, logger, lARTCPA,
              stats, cfa);
        } catch (InvalidConfigurationException e) {
          throw new RuntimeException(e);
        }
        Set<Statistics> lStatistics = new HashSet<>();
        lStatistics.add(lARTStatistics);
        cegarAlg.collectStatistics(lStatistics);
      }
    } catch (IOException | InvalidConfigurationException e) {
      throw new RuntimeException(e);
    }

    boolean analysisWasSound = false;
    boolean hasTimedOut = false;

    if (cpuTimelimitPerGoal < 0) {
      // run algorithm without time limit
      analysisWasSound = algorithm.run(reachedSet).isSound();
    } else {
      // run algorithm with time limit
      WorkerRunnable workerRunnable =
          new WorkerRunnable(algorithm, reachedSet, cpuTimelimitPerGoal, algNotifier);

      Thread workerThread = new Thread(workerRunnable);

      workerThread.start();
      workerThread.join();

      if (workerRunnable.throwableWasCaught()) {
        // TODO: handle exception
        analysisWasSound = false;
        //        throw new RuntimeException(workerRunnable.getCaughtThrowable());
      } else {
        analysisWasSound = workerRunnable.analysisWasSound();

        if (workerRunnable.hasTimeout()) {
          logger.logf(Level.INFO, "Test goal timed out!");
          hasTimedOut = true;
        }
      }
    }
    Path argFile = Paths.get("output", "ARG_goal_" + goalIndex + ".dot");

    try (FileWriter w = new FileWriter(argFile.toString())) {
      ARGUtils.writeARGAsDot(w, (ARGState) reachedSet.getFirstState());
    } catch (IOException e) {
      logger.logUserException(Level.WARNING, e, "Could not write ARG to file");
    }

    if (hasTimedOut) {
      return ReachabilityAnalysisResult.TIMEDOUT;
    } /*else {

      AbstractState lastState = reachedSet.getLastState();

      if (lastState != null) {

        if (AbstractStates.isTargetState(lastState)) {

          logger.logf(Level.INFO, "Test goal is feasible.");

          CFAEdge criticalEdge = pGoal.getCriticalEdge();

          Map<ARGState, CounterexampleInfo> counterexamples = lARTCPA.getCounterexamples();

          if (counterexamples.isEmpty()) {

            logger.logf(Level.INFO, "Counterexample is not available.");

            LinkedList<CFAEdge> trace = new LinkedList<>();

            // Try to reconstruct a trace in the ARG and shrink it
            ARGState argState = AbstractStates.extractStateByType(lastState, ARGState.class);
            ARGPath path = ARGUtils.getOnePathTo(argState);
            List<CFAEdge> shrinkedErrorPath = null;
            if (path != null) {
              shrinkedErrorPath = new ErrorPathShrinker().shrinkErrorPath(path);
            }

            Collection<ARGState> parents;
            parents = argState.getParents();

            while (!parents.isEmpty()) {

              ARGState parent = null;

              for (ARGState tmp_parent : parents) {
                parent = tmp_parent;
                break; // we just choose some parent
              }

              CFAEdge edge = parent.getEdgeToChild(argState);
              trace.addFirst(edge);

              argState = parent;
              parents = argState.getParents();
            }

            List<BigInteger> inputValues = new ArrayList<>();

            TestCase testcase =
                new TestCase(inputValues, trace, shrinkedErrorPath, null, null);
            testsuite.addTestCase(testcase, pGoal);

          } else {

            logger.logf(Level.INFO, "Counterexample is available.");

            assert counterexamples.size() == 1;

            for (Map.Entry<ARGState, CounterexampleInfo> lEntry : counterexamples.entrySet()) {

              CounterexampleInfo cex = lEntry.getValue();

              if (cex.isSpurious()) {
                logger.logf(Level.WARNING, "Counterexample is spurious!");
              } else {

                RichModel model = cex.getTargetPathModel();

                Comparator<Map.Entry<AssignableTerm, Object>> comp =
                    new Comparator<Map.Entry<AssignableTerm, Object>>() {

                      @Override
                      public int compare(Entry<AssignableTerm, Object> pArg0,
                          Entry<AssignableTerm, Object> pArg1) {
                        assert pArg0.getKey().getName().equals(pArg1.getKey().getName());
                        assert pArg0.getKey() instanceof Model.Variable;
                        assert pArg1.getKey() instanceof Model.Variable;

                        Model.Variable v0 = (Model.Variable) pArg0.getKey();
                        Model.Variable v1 = (Model.Variable) pArg1.getKey();

                        return (v0.getSSAIndex() - v1.getSSAIndex());
                      }

                    };

                TreeSet<Map.Entry<AssignableTerm, Object>> inputs = new TreeSet<>(comp);

                for (Entry<AssignableTerm, Object> e : model.entrySet()) {
                  if (e.getKey() instanceof Model.Variable) {
                    Model.Variable v = (Model.Variable) e.getKey();

                    if (v.getName().equals(WrapperUtil.CPAtiger_INPUT + "::__retval__")) {
                      inputs.add(e);
                    }
                  }
                }

                List<BigInteger> inputValues = new ArrayList<>(inputs.size());

                for (Entry<AssignableTerm, Object> e : inputs) {
                  //assert e.getValue() instanceof BigInteger;
                  //inputValues.add((BigInteger)e.getValue());
                  inputValues.add(new BigInteger(e.getValue().toString()));
                }

                List<CFAEdge> shrinkedErrorPath =
                    new ErrorPathShrinker().shrinkErrorPath(cex.getTargetPath());

                TestCase testcase =
                    new TestCase(inputValues, cex.getTargetPath().asEdgesList(), shrinkedErrorPath,
                        null, null);
                testsuite.addTestCase(testcase, pGoal);

                //for (Pair<ARGState, CFAEdge> stateEdgePair : cex.getTargetPath()) {
                for (CFAEdge lCFAEdge : cex.getTargetPath().asEdgesList()) {
                  //if (stateEdgePair.getSecond().equals(criticalEdge)) {
                  if (lCFAEdge.equals(criticalEdge)) {
                    logger.logf(Level.INFO,
                        "*********************** extract abstract state ***********************");
                  }
                }
              }
            }
          }
        } else {

          // we consider the test goals is infeasible
          logger.logf(Level.INFO, "Test goal infeasible.");

          testsuite.addInfeasibleGoal(pGoal, null);
        }
      } else {
        throw new RuntimeException(
            "We need a last state to determine the feasibility of the test goal!");
      }
      if (analysisWasSound) {
        return ReachabilityAnalysisResult.SOUND;
      } else {
        return ReachabilityAnalysisResult.UNSOUND;
      }


    }*/


    return ReachabilityAnalysisResult.SOUND;
  }

}
