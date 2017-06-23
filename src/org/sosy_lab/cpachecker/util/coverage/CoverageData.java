/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2015  Dirk Beyer
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
package org.sosy_lab.cpachecker.util.coverage;

import java.util.HashMap;
import java.util.Map;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.model.ADeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;


public class CoverageData {

  private final Map<String, FileCoverageInformation> infosPerFile;

  public CoverageData() {
    this.infosPerFile = new HashMap<>();
  }

  private FileCoverageInformation getFileInfoTarget(final FileLocation pLoc) {

    assert pLoc.getStartingLineNumber() != 0; // Cannot produce coverage info for dummy file location

    String file = pLoc.getFileName();
    FileCoverageInformation fileInfos = infosPerFile.get(file);

    if (fileInfos == null) {
      fileInfos = new FileCoverageInformation();
      infosPerFile.put(file, fileInfos);
    }

    return fileInfos;
  }

  public boolean putExistingFunction(FunctionEntryNode pNode) {
    final String functionName = pNode.getFunctionName();
    final FileLocation loc = pNode.getFileLocation();

    if (loc.getStartingLineNumber() == 0) {
      // dummy location
      return false;
    }

    final FileCoverageInformation infos = getFileInfoTarget(loc);

    final int startingLine = loc.getStartingLineInOrigin();
    final int endingLine = loc.getEndingLineInOrigin();

    infos.addExistingFunction(functionName, startingLine, endingLine);

    return true;
  }

  public void handleEdgeCoverage(
      final CFAEdge pEdge,
      final boolean pVisited) {

    final FileLocation loc = pEdge.getFileLocation();
    if (loc.getStartingLineNumber() == 0) {
      // dummy location
      return;
    }
    if (pEdge instanceof ADeclarationEdge
        && (((ADeclarationEdge)pEdge).getDeclaration() instanceof AFunctionDeclaration)) {
      // Function declarations span the complete body, this is not desired.
      return;
    }

    final FileCoverageInformation collector = getFileInfoTarget(loc);

    final int startingLine = loc.getStartingLineInOrigin();
    final int endingLine = loc.getEndingLineInOrigin();

    for (int line = startingLine; line <= endingLine; line++) {
      collector.addExistingLine(line);
    }

    if (pEdge instanceof AssumeEdge) {
      collector.addExistingAssume((AssumeEdge) pEdge);
      if (pVisited) {
        collector.addVisitedAssume((AssumeEdge) pEdge);
      }
    }

    if (pVisited) {
      for (int line = startingLine; line <= endingLine; line++) {
        collector.addVisitedLine(line);
      }
    }
  }

  public void addVisitedFunction(FunctionEntryNode pEntryNode) {
    addVisitedFunction(pEntryNode, 1);
  }

  public void addVisitedFunction(FunctionEntryNode pEntryNode, int count) {
    FileCoverageInformation infos = getFileInfoTarget(pEntryNode.getFileLocation());
    infos.addVisitedFunction(pEntryNode.getFunctionName(), count);
  }

  public Map<String, FileCoverageInformation> getInfosPerFile() {
    return infosPerFile;
  }

}
