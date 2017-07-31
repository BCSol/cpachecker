/*
 * CPAchecker is a tool for configurable software verification.
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
package org.sosy_lab.cpachecker.cfa.parser.eclipse.js;

import static org.mockito.Mockito.*;

import com.google.common.truth.Truth;
import org.eclipse.wst.jsdt.core.dom.Expression;
import org.eclipse.wst.jsdt.core.dom.IfStatement;
import org.eclipse.wst.jsdt.core.dom.JavaScriptUnit;
import org.junit.Before;
import org.junit.Test;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.ast.js.JSExpression;
import org.sosy_lab.cpachecker.cfa.ast.js.JSIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.js.JSSimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.js.JSAssumeEdge;
import org.sosy_lab.cpachecker.cfa.types.js.JSAnyType;
import org.sosy_lab.cpachecker.exceptions.ParserException;

public class IfStatementCFABuilderTest {

  private EclipseJavaScriptParser parser;
  private ConfigurableJavaScriptCFABuilder builder;
  private CFANode entryNode;

  @Before
  public void init() throws InvalidConfigurationException {
    builder = JavaScriptCFABuilderFactory.createTestJavaScriptCFABuilder();
    parser = new EclipseJavaScriptParser(builder.getLogger());
    entryNode = builder.getExitNode();
  }

  private JavaScriptUnit createAST(final String pCode) {
    return (JavaScriptUnit) parser.createAST(builder.getBuilder().getFilename(), pCode);
  }

  @SuppressWarnings("unchecked")
  private IfStatement parseStatement(final String pCode) {
    return (IfStatement) createAST(pCode).statements().get(0);
  }

  @Test
  public final void testIfWithoutElse() throws ParserException {
    final IfStatement ifStatement = parseStatement("if (condition) { doSomething() }");
    // expected CFA: <entryNode> --[condition]--> () -{doSomething()}-> () --\
    //                    \                                                   }--> ()
    //                     \------[!condition]-------------------------------/

    final JSExpression condition =
        new JSIdExpression(
            FileLocation.DUMMY, JSAnyType.ANY, "condition", mock(JSSimpleDeclaration.class));
    final ASTConverter astConverter = builder.getAstConverter();
    doReturn(condition).when(astConverter).convert(any(Expression.class));
    final StatementAppendable statementAppendable =
        (builder, pStatement) ->
            builder.appendEdge(DummyEdge.withDescription("dummy statement edge"));
    builder.setStatementAppendable(statementAppendable);

    new IfStatementCFABuilder().append(builder, ifStatement);

    Truth.assertThat(entryNode.getNumLeavingEdges()).isEqualTo(2);
    final JSAssumeEdge firstEdge = (JSAssumeEdge) entryNode.getLeavingEdge(0);
    final JSAssumeEdge secondEdge = (JSAssumeEdge) entryNode.getLeavingEdge(1);
    final JSAssumeEdge thenEdge = firstEdge.getTruthAssumption() ? firstEdge : secondEdge;
    final JSAssumeEdge elseEdge = firstEdge.getTruthAssumption() ? secondEdge : firstEdge;

    Truth.assertThat(thenEdge.getTruthAssumption()).isTrue();
    Truth.assertThat(elseEdge.getTruthAssumption()).isFalse();
    Truth.assertThat(thenEdge.getExpression()).isEqualTo(condition);
    Truth.assertThat(elseEdge.getExpression()).isEqualTo(condition);
  }

  // TODO add further tests
}
