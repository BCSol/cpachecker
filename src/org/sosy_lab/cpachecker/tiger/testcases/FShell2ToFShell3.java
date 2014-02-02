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
package org.sosy_lab.cpachecker.tiger.testcases;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

public class FShell2ToFShell3 {

  private final static String USAGE_STRING = "Usage: java " + FShell2ToFShell3.class.getCanonicalName() + " [--out=output-file] <FShell2 result files>";

  public static void translateTestsuite(String pSourceFile, String pTargetFile) throws NumberFormatException, FileNotFoundException, IOException {
    translateTestsuite(new PrintStream(new FileOutputStream(pTargetFile)), pSourceFile);
  }

  public static void translateTestsuite(PrintStream pOutputStream, String pFile) throws NumberFormatException, IOException {
    // TODO implement
    throw new RuntimeException("Implement!");

    /*
    BufferedReader lReader = new BufferedReader(new FileReader(pFile));

    String lLine = null;

    boolean lNextInput = false;

    Set<TestCase> lTestCases = new LinkedHashSet<>();

    List<Integer> lValues = new LinkedList<>();

    while ((lLine = lReader.readLine()) != null) {
      lLine = lLine.trim();

      if (lLine.equals("")) {
        if (lNextInput) {
          lNextInput = false;
          TestCase lTestCase = new PreciseInputsTestCase(lValues);
          lValues.clear();
          lTestCases.add(lTestCase);
        }

        continue;
      }

      if (lNextInput) {
        if (lLine.startsWith("input()=")) {
          String lValue = lLine.substring("input()=".length());
          lValues.add(Integer.valueOf(lValue));
        }
      }
      else {
        if (lLine.equals("IN:")) {
          lNextInput = true;
        }
      }
    }

    for (TestCase lTestCase : lTestCases) {
      pOutputStream.println(lTestCase);
    }

    lReader.close();*/
  }

  public static void main(String[] args) throws IOException {
    if (args.length > 0) {
      PrintStream lOutputStream;

      int lStartIndex;

      if (args[0].startsWith("--out=")) {
        if (args.length == 1) {
          System.out.println(USAGE_STRING);
          return;
        }

        String lFileName = args[0].substring("--out=".length());

        lOutputStream = new PrintStream(new FileOutputStream(lFileName));

        lStartIndex = 1;
      }
      else {
        lOutputStream = System.out;
        lStartIndex = 0;
      }

      for (int lIndex = lStartIndex; lIndex < args.length; lIndex++) {
        String lFileName = args[lIndex];

        translateTestsuite(lOutputStream, lFileName);

        /*BufferedReader lReader = new BufferedReader(new FileReader(lFileName));

        String lLine = null;

        boolean lNextInput = false;

        Set<TestCase> lTestCases = new LinkedHashSet<TestCase>();

        List<Integer> lValues = new LinkedList<Integer>();

        while ((lLine = lReader.readLine()) != null) {
          lLine = lLine.trim();

          if (lLine.equals("")) {
            if (lNextInput) {
              lNextInput = false;
              TestCase lTestCase = new PreciseInputsTestCase(lValues);
              lValues.clear();
              lTestCases.add(lTestCase);
            }

            continue;
          }

          if (lNextInput) {
            if (lLine.startsWith("input()=")) {
              String lValue = lLine.substring("input()=".length());
              lValues.add(Integer.valueOf(lValue));
            }
          }
          else {
            if (lLine.equals("IN:")) {
              lNextInput = true;
            }
          }
        }

        for (TestCase lTestCase : lTestCases) {
          lOutputStream.println(lTestCase);
        }*/
      }
    }
    else {
      System.out.println(USAGE_STRING);
    }
  }

}
