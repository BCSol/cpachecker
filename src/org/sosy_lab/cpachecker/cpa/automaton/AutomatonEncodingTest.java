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

import java.util.Map;

import org.junit.Ignore;
import org.junit.Test;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.cpachecker.util.test.CPATestRunner;
import org.sosy_lab.cpachecker.util.test.TestDataTools;
import org.sosy_lab.cpachecker.util.test.TestResults;
import org.sosy_lab.cpachecker.util.test.TestRunStatisticsParser;

import com.google.common.collect.ImmutableMap;

public class AutomatonEncodingTest {

  @Ignore
  @Test
  public void testEncodingOfLdvRule100_Safe() throws Exception {
    final String specFile = "test/config/automata/encode/LDV_100_1a.spc";
    final String programFile = "test/config/automata/encode/true_100_1a_1.c";

    TestResults results = runWithAutomataEncoding(specFile, programFile);

    results.assertIsSafe();
  }

  @Test
  public void testEncodingOfLdvRule8_Safe() throws Exception {
    final String specFile = "test/config/automata/encode/LDV_08_1a_encode.spc";
    final String programFile = "test/config/automata/encode/true_08_1a_2.c";

    TestResults results = runWithAutomataEncoding(specFile, programFile);

    results.assertIsSafe();
  }

  @Test
  public void testEncodingOfLdvRule118_Safe() throws Exception {
    final String specFile = "test/config/automata/encode/LDV_118_1a_encode.spc";
    final String programFile = "test/config/automata/encode/ldv_118_test.c";

    TestResults results = runWithAutomataEncoding(specFile, programFile);

    results.assertIsSafe();

    TestRunStatisticsParser stat = new TestRunStatisticsParser();
    results.getCheckerResult().printStatistics(stat.getPrintStream());

    stat.assertThatNumber("Number of times merged").isAtLeast(2);
    stat.assertThatNumber("Number of refinements").isAtMost(3);
  }

  @Test
  public void testEncodingOfLdvRule118_Unsafe() throws Exception {
    final String specFile = "test/config/automata/encode/LDV_118_1a_encode.spc";
    final String programFile = "test/config/automata/encode/ldv_118_test_false.c";

    TestResults results = runWithAutomataEncoding(specFile, programFile);

    results.assertIsUnsafe();

    TestRunStatisticsParser stat = new TestRunStatisticsParser();
    results.getCheckerResult().printStatistics(stat.getPrintStream());

    stat.assertThatNumber("Number of times merged").isAtLeast(0);
    stat.assertThatNumber("Number of successful refinements").isAtMost(3);
    stat.assertThatNumber("Max states per location").isAtMost(2);
  }

  @Test
  public void testAssumeOnNamedArgument_Safe() throws Exception {
    final String floatSpecFile = "test/config/automata/encode/SPEC_sqrt.spc";
    final String floatProgramFile = "test/config/automata/encode/SPEC_sqrt_true.c";
    final String intSpecFile = "test/config/automata/encode/SPEC_sqrt_int.spc";
    final String intProgramFile = "test/config/automata/encode/SPEC_sqrt_int_true.c";

    TestResults floatResults = runWithAutomataEncoding(floatSpecFile, floatProgramFile);
    TestResults intResults = runWithAutomataEncoding(intSpecFile, intProgramFile);

    floatResults.assertIsSafe();
    intResults.assertIsSafe();
  }

  @Test
  public void testAssumeOnNamedArgument_Unsafe() throws Exception {
    final String floatSpecFile = "test/config/automata/encode/SPEC_sqrt.spc";
    final String floatProgramFile = "test/config/automata/encode/SPEC_sqrt_false.c";
    final String intSpecFile = "test/config/automata/encode/SPEC_sqrt_int.spc";
    final String intProgramFile = "test/config/automata/encode/SPEC_sqrt_int_false.c";

    TestResults floatResults = runWithAutomataEncoding(floatSpecFile, floatProgramFile);
    TestResults intResults = runWithAutomataEncoding(intSpecFile, intProgramFile);

    floatResults.assertIsUnsafe();
    intResults.assertIsUnsafe();
  }

  @Test
  public void testAssumeOnNamenArgument_Unmatch() throws Exception {
    final String unmatchSpecFile = "test/config/automata/encode/SPEC_sqrt_unmatch.spc";
    final String specFile = "test/config/automata/encode/SPEC_sqrt.spc";
    final String programFile = "test/config/automata/encode/SPEC_sqrt_false.c";
    final String unmatchProgramFile = "test/config/automata/encode/SPEC_sqrt_unmatch_false.c";

    TestResults unmatchSpecResults = runWithAutomataEncoding(unmatchSpecFile, programFile);
    TestResults unmatchProgramResults = runWithAutomataEncoding(specFile, unmatchProgramFile);

    // we expect `safe' as result instead of `unsafe' because our specification and program files
    // do not match, hence the specification cannot be applied to the program in order to check
    // for the bug.
    unmatchSpecResults.assertIsSafe();
    unmatchProgramResults.assertIsSafe();
  }

  private TestResults runWithAutomataEncoding(final String pSpecFile, final String pProgramFile)
      throws Exception {

    Map<String, String> prop = ImmutableMap.<String, String>builder()
        .put("specification", pSpecFile)
        .put("cpa.predicate.ignoreIrrelevantVariables", "true")
        .put("cpa.predicate.strengthenWithTargetConditions", "false")
        .put("cpa.composite.aggregateBasicBlocks", "false")
        .put("automata.properties.granularity", "BASENAME")
        .put("analysis.checkCounterexamples", "false")
        .build();

    Configuration cfg = TestDataTools.configurationForTest()
        .loadFromFile("config/predicateAnalysis-PredAbsRefiner-ABEl-bitprecise.properties")
        .setOptions(prop)
        .build();

    return CPATestRunner.run(cfg, pProgramFile, false);
  }

}
