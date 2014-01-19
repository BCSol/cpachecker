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
package org.sosy_lab.cpachecker.tiger.fql.translators.ecp;

import java.io.File;
import java.util.Collection;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.BasicLogManager;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.tiger.CPATiger;
import org.sosy_lab.cpachecker.tiger.cilly.Cilly;
import org.sosy_lab.cpachecker.tiger.fql.ast.FQLSpecification;
import org.sosy_lab.cpachecker.tiger.fql.ecp.ECPPrettyPrinter;
import org.sosy_lab.cpachecker.tiger.fql.ecp.ElementaryCoveragePattern;
import org.sosy_lab.cpachecker.tiger.targetgraph.TargetGraph;
import org.sosy_lab.cpachecker.tiger.targetgraph.TargetGraphUtil;

public class CoverageSpecificationTranslatorTest {

  private Cilly lCilly;

  @Before
  public void setup() throws InvalidConfigurationException {
    Configuration config = Configuration.defaultConfiguration();
    LogManager logger = new BasicLogManager(config);
    lCilly = new Cilly(logger);
  }

  @Test
  public void testMain001() throws Exception {
    /** process FQL query */
    String lSpecificationString = "COVER \"EDGES(ID)*\".EDGES(@CALL(f)).\"EDGES(ID)*\"";
    FQLSpecification lSpecification = FQLSpecification.parse(lSpecificationString);
    System.out.println(lSpecification);

    String lSourceFileName = "test/programs/simple/functionCall.c";

    if (!lCilly.isCillyInvariant(lSourceFileName)) {
      File lCillyProcessedFile = lCilly.cillyfy(lSourceFileName);
      lCillyProcessedFile.deleteOnExit();

      lSourceFileName = lCillyProcessedFile.getAbsolutePath();

      System.err.println("WARNING: Given source file is not CIL invariant ... did preprocessing!");
    }

    String lEntryFunction = "main";

    Configuration lConfiguration = CPATiger.createConfiguration(lSourceFileName, lEntryFunction);

    LogManager lLogManager = new BasicLogManager(lConfiguration);

    FunctionEntryNode lMainFunction = CPATiger.getCFAMap(lSourceFileName, lConfiguration, lLogManager).get(lEntryFunction);

    TargetGraph lTargetGraph = TargetGraphUtil.cfa(lMainFunction);

    Set<CFAEdge> lBasicBlockEntries = TargetGraphUtil.getBasicBlockEntries(lMainFunction);

    /** do translation */
    PathPatternTranslator lPatternTranslator = new PathPatternTranslator(lTargetGraph, lBasicBlockEntries);
    CoverageSpecificationTranslator lSpecificationTranslator = new CoverageSpecificationTranslator(lPatternTranslator);
    Collection<ElementaryCoveragePattern> lGoals = lSpecificationTranslator.translate(lSpecification.getCoverageSpecification());
    ElementaryCoveragePattern lPassing = lPatternTranslator.translate(lSpecification.getPathPattern());

    ECPPrettyPrinter lPrettyPrinter = new ECPPrettyPrinter();

    System.out.println("TEST GOALS:");

    int lIndex = 0;

    for (ElementaryCoveragePattern lGoal : lGoals) {
      System.out.println("Goal #" + (++lIndex));
      System.out.println(lPrettyPrinter.printPretty(lGoal));
    }

    System.out.println("PASSING:");
    System.out.println(lPrettyPrinter.printPretty(lPassing));
  }

  @Test
  public void testMain002() throws Exception {
    /** process FQL query */
    String lSpecificationString = "COVER \"EDGES(ID)*\".(EDGES(@CALL(f)) + NODES(@CALL(f))).\"EDGES(ID)*\"";
    FQLSpecification lSpecification = FQLSpecification.parse(lSpecificationString);
    System.out.println(lSpecification);

    String lSourceFileName = "test/programs/simple/functionCall.c";

    if (!lCilly.isCillyInvariant(lSourceFileName)) {
      File lCillyProcessedFile = lCilly.cillyfy(lSourceFileName);
      lCillyProcessedFile.deleteOnExit();

      lSourceFileName = lCillyProcessedFile.getAbsolutePath();

      System.err.println("WARNING: Given source file is not CIL invariant ... did preprocessing!");
    }

    String lEntryFunction = "main";

    Configuration lConfiguration = CPATiger.createConfiguration(lSourceFileName, lEntryFunction);

    LogManager lLogManager = new BasicLogManager(lConfiguration);

    FunctionEntryNode lMainFunction = CPATiger.getCFAMap(lSourceFileName, lConfiguration, lLogManager).get(lEntryFunction);

    TargetGraph lTargetGraph = TargetGraphUtil.cfa(lMainFunction);

    Set<CFAEdge> lBasicBlockEntries = TargetGraphUtil.getBasicBlockEntries(lMainFunction);

    /** do translation */
    PathPatternTranslator lPatternTranslator = new PathPatternTranslator(lTargetGraph, lBasicBlockEntries);
    CoverageSpecificationTranslator lSpecificationTranslator = new CoverageSpecificationTranslator(lPatternTranslator);
    Collection<ElementaryCoveragePattern> lGoals = lSpecificationTranslator.translate(lSpecification.getCoverageSpecification());
    ElementaryCoveragePattern lPassing = lPatternTranslator.translate(lSpecification.getPathPattern());

    ECPPrettyPrinter lPrettyPrinter = new ECPPrettyPrinter();

    System.out.println("TEST GOALS:");

    int lIndex = 0;

    for (ElementaryCoveragePattern lGoal : lGoals) {
      System.out.println("Goal #" + (++lIndex));
      System.out.println(lPrettyPrinter.printPretty(lGoal));
    }

    System.out.println("PASSING:");
    System.out.println(lPrettyPrinter.printPretty(lPassing));
  }

  @Test
  public void testMain003() throws Exception {
    /** process FQL query */
    String lSpecificationString = "COVER \"EDGES(ID)*\".{ x > 100 }.(EDGES(@CALL(f)) + NODES(@CALL(f))).\"EDGES(ID)*\"";
    FQLSpecification lSpecification = FQLSpecification.parse(lSpecificationString);
    System.out.println(lSpecification);

    String lSourceFileName = "test/programs/simple/functionCall.c";

    if (!lCilly.isCillyInvariant(lSourceFileName)) {
      File lCillyProcessedFile = lCilly.cillyfy(lSourceFileName);
      lCillyProcessedFile.deleteOnExit();

      lSourceFileName = lCillyProcessedFile.getAbsolutePath();

      System.err.println("WARNING: Given source file is not CIL invariant ... did preprocessing!");
    }

    String lEntryFunction = "main";

    Configuration lConfiguration = CPATiger.createConfiguration(lSourceFileName, lEntryFunction);

    LogManager lLogManager = new BasicLogManager(lConfiguration);

    FunctionEntryNode lMainFunction = CPATiger.getCFAMap(lSourceFileName, lConfiguration, lLogManager).get(lEntryFunction);

    TargetGraph lTargetGraph = TargetGraphUtil.cfa(lMainFunction);

    Set<CFAEdge> lBasicBlockEntries = TargetGraphUtil.getBasicBlockEntries(lMainFunction);

    /** do translation */
    PathPatternTranslator lPatternTranslator = new PathPatternTranslator(lTargetGraph, lBasicBlockEntries);
    CoverageSpecificationTranslator lSpecificationTranslator = new CoverageSpecificationTranslator(lPatternTranslator);
    Collection<ElementaryCoveragePattern> lGoals = lSpecificationTranslator.translate(lSpecification.getCoverageSpecification());
    ElementaryCoveragePattern lPassing = lPatternTranslator.translate(lSpecification.getPathPattern());

    ECPPrettyPrinter lPrettyPrinter = new ECPPrettyPrinter();

    System.out.println("TEST GOALS:");

    int lIndex = 0;

    for (ElementaryCoveragePattern lGoal : lGoals) {
      System.out.println("Goal #" + (++lIndex));
      System.out.println(lPrettyPrinter.printPretty(lGoal));
    }

    System.out.println("PASSING:");
    System.out.println(lPrettyPrinter.printPretty(lPassing));
  }

  @Test
  public void testMain004() throws Exception {
    /** process FQL query */
    String lSpecificationString = "COVER \"EDGES(ID)*\".{ x > 100 }.PATHS(ID, 1).\"EDGES(ID)*\"";
    FQLSpecification lSpecification = FQLSpecification.parse(lSpecificationString);
    System.out.println(lSpecification);

    String lSourceFileName = "test/programs/simple/functionCall.c";

    if (!lCilly.isCillyInvariant(lSourceFileName)) {
      File lCillyProcessedFile = lCilly.cillyfy(lSourceFileName);
      lCillyProcessedFile.deleteOnExit();

      lSourceFileName = lCillyProcessedFile.getAbsolutePath();

      System.err.println("WARNING: Given source file is not CIL invariant ... did preprocessing!");
    }

    String lEntryFunction = "main";

    Configuration lConfiguration = CPATiger.createConfiguration(lSourceFileName, lEntryFunction);

    LogManager lLogManager = new BasicLogManager(lConfiguration);

    FunctionEntryNode lMainFunction = CPATiger.getCFAMap(lSourceFileName, lConfiguration, lLogManager).get(lEntryFunction);

    TargetGraph lTargetGraph = TargetGraphUtil.cfa(lMainFunction);

    Set<CFAEdge> lBasicBlockEntries = TargetGraphUtil.getBasicBlockEntries(lMainFunction);

    /** do translation */
    PathPatternTranslator lPatternTranslator = new PathPatternTranslator(lTargetGraph, lBasicBlockEntries);
    CoverageSpecificationTranslator lSpecificationTranslator = new CoverageSpecificationTranslator(lPatternTranslator);
    Collection<ElementaryCoveragePattern> lGoals = lSpecificationTranslator.translate(lSpecification.getCoverageSpecification());
    ElementaryCoveragePattern lPassing = lPatternTranslator.translate(lSpecification.getPathPattern());

    ECPPrettyPrinter lPrettyPrinter = new ECPPrettyPrinter();

    System.out.println("TEST GOALS:");

    int lIndex = 0;

    for (ElementaryCoveragePattern lGoal : lGoals) {
      System.out.println("Goal #" + (++lIndex));
      System.out.println(lPrettyPrinter.printPretty(lGoal));
    }

    System.out.println("PASSING:");
    System.out.println(lPrettyPrinter.printPretty(lPassing));
  }

}
