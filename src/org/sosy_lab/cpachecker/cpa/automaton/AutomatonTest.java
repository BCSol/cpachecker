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
package org.sosy_lab.cpachecker.cpa.automaton;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;

import org.junit.Ignore;
import org.junit.Test;
import org.sosy_lab.common.io.Files;
import org.sosy_lab.common.io.Path;
import org.sosy_lab.common.io.Paths;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.util.test.CPATestRunner;
import org.sosy_lab.cpachecker.util.test.TestResults;

import java.util.Map;

public class AutomatonTest {
  private static final String CPAS_UNINITVARS = "cpa.location.LocationCPA, cpa.uninitvars.UninitializedVariablesCPA";
  private static final String OUTPUT_FILE = "output/AutomatonExport.dot";

  // Specification Tests
  @Test
  public void cyclicInclusionTest() throws Exception {
    Map<String, String> prop = ImmutableMap.of(
        "CompositeCPA.cpas",       CPAS_UNINITVARS,
        "specification",           "test/config/automata/tmpSpecification.spc",
        "log.consoleLevel",        "INFO",
        "analysis.stopAfterError", "FALSE"
      );

    Path tmpSpc = Paths.get("test/config/automata/tmpSpecification.spc");
    String content =
        "#include UninitializedVariablesTestAutomaton.txt \n" + "#include tmpSpecification.spc \n";
    Files.writeFile(tmpSpc, content);
    TestResults results = CPATestRunner.run(prop, "test/programs/simple/UninitVarsErrors.c");
    results.assertIsSafe();
    assertThat(results.getLog())
        .contains("test/config/automata/tmpSpecification.spc\" was referenced multiple times.");
    assertThat(tmpSpc.delete()).named("deletion of temporary specification successful").isTrue();
  }

  @Test
  public void includeSpecificationTest() throws Exception {
    Map<String, String> prop =
        ImmutableMap.of(
            "CompositeCPA.cpas", CPAS_UNINITVARS,
            "specification", "test/config/automata/defaultSpecificationForTesting.spc",
            "log.consoleLevel", "INFO",
            "cpa.automaton.adjustAutomatonTransitions", "false",
            "analysis.stopAfterError", "FALSE");

    TestResults results = CPATestRunner.run(prop, "test/programs/simple/UninitVarsErrors.c");
    assertThat(results.getLog()).contains("Automaton: Uninitialized return value");
    assertThat(results.getLog()).contains("Automaton: Uninitialized variable used");
  }

  @Test
  public void specificationAndNoCompositeTest() throws Exception {
    Map<String, String> prop = ImmutableMap.of(
        "cpa",              "cpa.location.LocationCPA",
        "log.consoleLevel", "INFO",
        "specification",    "test/config/automata/LockingAutomatonAll.txt");

    TestResults results = CPATestRunner.run(prop, "test/programs/simple/modificationExample.c");
    assertThat(results.getLog())
        .contains("Option specification gave specification automata, but no CompositeCPA was used");
    assertThat(results.getCheckerResult().getResult()).isEqualTo(Result.NOT_YET_STARTED);
  }

  @Ignore // Does not work until we can encode more than one property in an automaton
          // {@see Automaton#getIsRelevantForProperties(AutomatonTransition)}
  @Test
  public void modificationTestWithSpecification() throws Exception {
    Map<String, String> prop = ImmutableMap.of(
        "CompositeCPA.cpas",   "cpa.location.LocationCPA, cpa.value.ValueAnalysisCPA",
        "specification",       "test/config/automata/modifyingAutomaton.txt",
        "log.consoleLevel",    "INFO",
        "cpa.value.threshold", "10");

    TestResults results = CPATestRunner.run(prop, "test/programs/simple/modificationExample.c");
    assertThat(results.getLog()).contains("MODIFIED");
    assertThat(results.getLog()).contains("Modification successful");
    results.assertIsSafe();
  }

  //Automaton Tests
  @Test
  public void syntaxErrorTest() throws Exception {
    Map<String, String> prop = ImmutableMap.of(
        "specification",           "config/predicateAnalysis.properties",
        "log.consoleLevel",        "INFO"
      );

    TestResults results = CPATestRunner.run(prop, "test/programs/simple/UninitVarsErrors.c");
    assertThat(results.getCheckerResult().getResult()).isEqualTo(Result.NOT_YET_STARTED);
    assertThat(results.getLog()).contains("Illegal character");
  }

  @Test
  public void matchEndOfProgramTest() throws Exception {
    Map<String, String> prop =
        ImmutableMap.of(
            "CompositeCPA.cpas", "cpa.location.LocationCPA",
            "specification", "test/config/automata/PrintLastStatementAutomaton.spc",
            "log.consoleLevel", "INFO",
            "cpa.automaton.adjustAutomatonTransitions", "false",
            "analysis.stopAfterError", "TRUE");

    TestResults results = CPATestRunner.run(prop, "test/programs/simple/loop1.c");
    assertThat(results.getLog()).contains("Last statement is \"return (0);\"");
    assertThat(results.getLog()).contains("Last statement is \"return (-1);\"");
    results.assertIsSafe();
  }

  @Test
  public void failIfNoAutomatonGiven() throws Exception {
    Map<String, String> prop = ImmutableMap.of(
        "CompositeCPA.cpas",   "cpa.location.LocationCPA, cpa.value.ValueAnalysisCPA, cpa.automaton.ControlAutomatonCPA",
        "log.consoleLevel",    "INFO",
        "cpa.value.threshold", "10");

    TestResults results = CPATestRunner.run(prop, "test/programs/simple/modificationExample.c");
    assertThat(results.getCheckerResult().getResult()).isEqualTo(Result.NOT_YET_STARTED);
    assertThat(results.getLog())
        .contains("Explicitly specified automaton CPA needs option cpa.automaton.inputFile!");
  }

  @Ignore // Does not work until we can encode more than one property in an automaton
          // {@see Automaton#getIsRelevantForProperties(AutomatonTransition)}
  @Test
  public void modificationTest() throws Exception {
    Map<String, String> prop = ImmutableMap.of(
        "CompositeCPA.cpas",       "cpa.location.LocationCPA, cpa.value.ValueAnalysisCPA, cpa.automaton.ControlAutomatonCPA",
        "cpa.automaton.inputFile", "test/config/automata/modifyingAutomaton.txt",
        "log.consoleLevel",        "INFO",
        "cpa.value.threshold",     "10");

    TestResults results = CPATestRunner.run(prop, "test/programs/simple/modificationExample.c");
    assertThat(results.getLog()).contains("MODIFIED");
    assertThat(results.getLog()).contains("Modification successful");
    results.assertIsSafe();
  }

  @Test
  public void modification_in_Observer_throws_Test() throws Exception {
    Map<String, String> prop = ImmutableMap.of(
        "CompositeCPA.cpas",       "cpa.location.LocationCPA, cpa.value.ValueAnalysisCPA, cpa.automaton.ObserverAutomatonCPA",
        "cpa.automaton.inputFile", "test/config/automata/modifyingAutomaton.txt",
        "log.consoleLevel",        "SEVERE",
        "cpa.value.threshold",     "10"
      );

    TestResults results = CPATestRunner.run(prop, "test/programs/simple/modificationExample.c");
    // check for stack trace
    assertThat(results.getLog()).contains("Error: Invalid configuration (The transition \"MATCH ");
  }

  @Test
  public void setuidTest() throws Exception {
    Map<String, String> prop = ImmutableMap.of(
        "CompositeCPA.cpas",       "cpa.location.LocationCPA, cpa.automaton.ObserverAutomatonCPA",
        "cpa.automaton.inputFile", "test/config/automata/simple_setuid.txt",
        "log.consoleLevel",        "INFO",
        "analysis.stopAfterError", "FALSE"
      );

    TestResults results = CPATestRunner.run(prop, "test/programs/simple/simple_setuid_test.c");
    assertThat(results.getLog()).contains("Systemcall in line 14 with userid 2");
    assertThat(results.getLog()).contains("going to ErrorState on edge \"system(40);\"");
    results.assertIsUnsafe();
  }

  @Test
  public void uninitVarsTest() throws Exception {
    Map<String, String> prop =
        ImmutableMap.<String, String>builder()
            .put(
                "CompositeCPA.cpas",
                "cpa.location.LocationCPA, cpa.automaton.ObserverAutomatonCPA, "
                    + "cpa.uninitvars.UninitializedVariablesCPA")
            .put(
                "cpa.automaton.inputFile",
                "test/config/automata/UninitializedVariablesTestAutomaton.txt")
            .put("log.consoleLevel", "FINER")
            .put("cpa.automaton.dotExportFile", OUTPUT_FILE)
            .put("cpa.automaton.adjustAutomatonTransitions", "false")
            .put("analysis.stopAfterError", "FALSE")
            .build();

    TestResults results = CPATestRunner.run(prop, "test/programs/simple/UninitVarsErrors.c");
    assertThat(results.getLog()).contains("Automaton: Uninitialized return value");
    assertThat(results.getLog()).contains("Automaton: Uninitialized variable used");
    results.assertIsSafe();
  }

  @Test
  public void locking_correct() throws Exception {
    Map<String, String> prop = ImmutableMap.of(
        "CompositeCPA.cpas",           "cpa.location.LocationCPA, cpa.automaton.ObserverAutomatonCPA",
        "cpa.automaton.inputFile",     "test/config/automata/LockingAutomatonAll.txt",
        "log.consoleLevel",            "INFO",
        "cpa.automaton.dotExportFile", OUTPUT_FILE
      );

    TestResults results = CPATestRunner.run(prop, "test/programs/simple/locking_correct.c");
    results.assertIsSafe();
  }

  @Test
  public void locking_incorrect() throws Exception {
    Map<String, String> prop = ImmutableMap.of(
        "CompositeCPA.cpas",       "cpa.location.LocationCPA, cpa.automaton.ObserverAutomatonCPA",
        "cpa.automaton.inputFile", "test/config/automata/LockingAutomatonAll.txt",
        "log.consoleLevel",        "INFO"
      );

    TestResults results = CPATestRunner.run(prop, "test/programs/simple/locking_incorrect.c");
    results.assertIsUnsafe();
  }

  @Test
  public void valueAnalysis_observing() throws Exception {
    Map<String, String> prop =
        ImmutableMap.of(
            "CompositeCPA.cpas",
                "cpa.location.LocationCPA, cpa.automaton.ObserverAutomatonCPA, cpa.value.ValueAnalysisCPA",
            "cpa.automaton.inputFile",
                "test/config/automata/ExplicitAnalysisObservingAutomaton.txt",
            "log.consoleLevel", "INFO",
            "cpa.automaton.adjustAutomatonTransitions", "false",
            "cpa.value.threshold", "2000");

    TestResults results = CPATestRunner.run(prop, "test/programs/simple/ex2.cil.c");
    assertThat(results.getLog()).contains("st==3 after Edge st = 3;");
    assertThat(results.getLog()).contains("st==1 after Edge st = 1;");
    assertThat(results.getLog()).contains("st==2 after Edge st = 2;");
    assertThat(results.getLog()).contains("st==4 after Edge st = 4;");
    results.assertIsSafe();
  }

  @Test
  public void functionIdentifying() throws Exception {
    Map<String, String> prop =
        ImmutableMap.of(
            "CompositeCPA.cpas", "cpa.location.LocationCPA, cpa.automaton.ObserverAutomatonCPA",
            "cpa.automaton.inputFile", "test/config/automata/FunctionIdentifyingAutomaton.txt",
            "cpa.automaton.adjustAutomatonTransitions", "false",
            "log.consoleLevel", "FINER");

    TestResults results = CPATestRunner.run(prop, "test/programs/simple/functionCall.c");
    assertThat(results.getLog()).contains("i'm in Main after Edge");
    assertThat(results.getLog()).contains("i'm in Main after Edge int y;");
    assertThat(results.getLog()).contains("i'm in f after Edge y = f()");
    assertThat(results.getLog()).contains("i'm in f after Edge int x;");
    assertThat(results.getLog()).contains("i'm in Main after Edge return");
    assertThat(results.getLog()).contains("i'm in Main after Edge ERROR:");
  }

  @Test
  public void interacting_Automata() throws Exception {
    Map<String, String> prop =
        ImmutableMap.<String, String>builder()
            .put(
                "CompositeCPA.cpas",
                "cpa.location.LocationCPA, "
                    + "cpa.automaton.ObserverAutomatonCPA automatonA, "
                    + "cpa.automaton.ObserverAutomatonCPA automatonB, cpa.value.ValueAnalysisCPA")
            .put(
                "automatonA.cpa.automaton.inputFile",
                "test/config/automata/InteractionAutomatonA.txt")
            .put(
                "automatonB.cpa.automaton.inputFile",
                "test/config/automata/InteractionAutomatonB.txt")
            .put("log.consoleLevel", "INFO")
            .put("cpa.automaton.adjustAutomatonTransitions", "false")
            .put("cpa.value.threshold", "2000")
            .build();

    TestResults results = CPATestRunner.run(prop, "test/programs/simple/loop1.c");
    assertThat(results.getLog()).contains("A: Matched i in line 13 x=2");
    assertThat(results.getLog()).contains("B: A increased to 2 And i followed ");
    results.assertIsSafe();
  }

  /* Automaton tests with SPLIT keyword */
  @Test
  public void splitAutomaton08() throws Exception {
    Map<String, String> prop = ImmutableMap.of(
        "CompositeCPA.cpas", "cpa.location.LocationCPA, "
            + "cpa.functionpointer.FunctionPointerCPA, "
            + "cpa.predicate.PredicateCPA, cpa.coverage.CoverageCPA",
        "log.consoleLevel", "FINER",
        "cpa.automaton.inputFile", "test/config/automata/ldv_08_1a_A.spc"
    );

    TestResults results = CPATestRunner.run(prop,
        "test/programs/automata/ldv_test_08_true.c");

    results.assertIsSafe();
  }

  @Test
  public void splitAutomaton32() throws Exception {
    Map<String, String> prop = ImmutableMap.of(
        "CompositeCPA.cpas", "cpa.location.LocationCPA, "
            + "cpa.automaton.ObserverAutomatonCPA, cpa.value.ValueAnalysisCPA",
        "log.consoleLevel", "FINER",
        "cpa.automaton.inputFile", "test/config/automata/ldv_32_1a_split.spc"
    );

    TestResults resultsTrue = CPATestRunner.run(prop,
        "test/programs/automata/ldv_test_32.c");
    TestResults resultsFalse = CPATestRunner.run(prop,
        "test/programs/automata/ldv_test_32_false.c");

    resultsTrue.assertIsSafe();
    resultsFalse.assertIsUnsafe();
  }

  /* Automaton tests with ASSUME keyword */
  @Test
  public void assumeAutomaton10() throws Exception {
    Map<String, String> propA = ImmutableMap.of(
        "CompositeCPA.cpas", "cpa.location.LocationCPA, "
            + "cpa.functionpointer.FunctionPointerCPA, "
            + "cpa.predicate.PredicateCPA, cpa.coverage.CoverageCPA",
        "log.consoleLevel", "FINER",
        "cpa.automaton.inputFile", "test/config/automata/ldv_10_1a_A.spc"
    );
    Map<String, String> propB = ImmutableMap.of(
        "CompositeCPA.cpas", "cpa.location.LocationCPA, "
            + "cpa.functionpointer.FunctionPointerCPA, "
            + "cpa.predicate.PredicateCPA, cpa.coverage.CoverageCPA",
        "log.consoleLevel", "FINER",
        "cpa.automaton.inputFile", "test/config/automata/ldv_10_1a_B.spc"
    );

    TestResults resultsA = CPATestRunner.run(propA,
        "test/programs/automata/ldv_test_10_true.c");
    TestResults resultsB = CPATestRunner.run(propB,
        "test/programs/automata/ldv_test_10_true.c");

    resultsA.assertIsSafe();
    resultsB.assertIsSafe();
  }

  @Test
  public void assumeAutomaton147() throws Exception {
    Map<String, String> prop = ImmutableMap.of(
        "CompositeCPA.cpas", "cpa.location.LocationCPA, "
            + "cpa.automaton.ObserverAutomatonCPA, cpa.value.ValueAnalysisCPA",
        "log.consoleLevel", "FINER",
        "cpa.automaton.inputFile", "test/config/automata/ldv_147.spc"
    );

    TestResults resultsFalse = CPATestRunner.run(prop,
        "test/programs/automata/ldv_test_147_false.c");
    TestResults resultsTrue = CPATestRunner.run(prop,
        "test/programs/automata/ldv_test_147_true.c");

    resultsFalse.assertIsUnsafe();
    resultsTrue.assertIsSafe();
  }

  @Test
  public void assumeAutomaton32() throws Exception {
    Map<String, String> prop = ImmutableMap.of(
        "CompositeCPA.cpas", "cpa.location.LocationCPA, "
            + "cpa.automaton.ObserverAutomatonCPA, cpa.value.ValueAnalysisCPA",
        "log.consoleLevel", "FINER",
        "cpa.automaton.inputFile", "test/config/automata/ldv_32_1a_fixed.spc"
    );

    TestResults resultsTrue = CPATestRunner.run(prop,
        "test/programs/automata/ldv_test_32.c");
    TestResults resultsFalse = CPATestRunner.run(prop,
        "test/programs/automata/ldv_test_32_false.c");

    resultsTrue.assertIsSafe();
    resultsFalse.assertIsUnsafe();
  }

  @Test
  public void assumeAutomaton43() throws Exception {
    Map<String, String> propA = ImmutableMap.of(
        "CompositeCPA.cpas", "cpa.location.LocationCPA, "
            + "cpa.automaton.ObserverAutomatonCPA, cpa.value.ValueAnalysisCPA",
        "log.consoleLevel", "FINER",
        "cpa.automaton.inputFile", "test/config/automata/ldv_43_1a_fixed.spc"
    );
    Map<String, String> propB = ImmutableMap.of(
        "CompositeCPA.cpas", "cpa.location.LocationCPA, "
            + "cpa.functionpointer.FunctionPointerCPA, "
            + "cpa.predicate.PredicateCPA, cpa.coverage.CoverageCPA",
        "log.consoleLevel", "FINER",
        "cfa.useMultiEdges", "false",
        "cpa.automaton.inputFile", "test/config/automata/ldv_43_1a.spc"
    );

    TestResults resultsATrue = CPATestRunner.run(propA,
        "test/programs/automata/ldv_test_43_true.c");
    TestResults resultsBTrue = CPATestRunner.run(propB,
        "test/programs/automata/ldv_test_43_true.c");
    TestResults resultsAFalse = CPATestRunner.run(propA,
        "test/programs/automata/ldv_test_43_false.c");

    resultsATrue.assertIsSafe();
    resultsBTrue.assertIsSafe();
    resultsAFalse.assertIsUnsafe();
  }

  @Test
  public void assumeAutomaton2() throws Exception {
    Map<String, String> prop = ImmutableMap.of(
        "CompositeCPA.cpas", "cpa.location.LocationCPA,"
            + "cpa.callstack.CallstackCPA,"
            + "cpa.functionpointer.FunctionPointerCPA,"
            + "cpa.predicate.PredicateCPA, cpa.coverage.CoverageCPA",
        "log.consoleLevel", "ALL",
        "cpa.automaton.inputFile", "test/config/automata/ldv_test2.spc"
    );

    TestResults resultsTrue = CPATestRunner.run(prop,
        "test/programs/automata/ldv_test2.c");
    TestResults resultsFalse = CPATestRunner.run(prop,
        "test/programs/automata/ldv_test2_false.c");

    resultsTrue.assertIsSafe();
    resultsFalse.assertIsSafe();
  }

  /* Other automaton test files */
  @Ignore // Does not work until we can encode more than one property in an automaton
          // {@see Automaton#getIsRelevantForProperties(AutomatonTransition)}
  @Test
  public void assertAutomaton147() throws Exception {
    Map<String, String> prop = ImmutableMap.of(
        "CompositeCPA.cpas", "cpa.location.LocationCPA, "
            + "cpa.automaton.ObserverAutomatonCPA, cpa.value.ValueAnalysisCPA",
        "log.consoleLevel", "FINER",
        "cpa.automaton.inputFile", "test/config/automata/ldv_147_assert.spc"
    );

    TestResults resultsTrue = CPATestRunner.run(prop,
        "test/programs/automata/ldv_test_147_true.c");
    TestResults resultsFalse = CPATestRunner.run(prop,
        "test/programs/automata/ldv_test_147_false.c");

    resultsTrue.assertIsUnsafe();
    resultsFalse.assertIsUnsafe();
  }

}
