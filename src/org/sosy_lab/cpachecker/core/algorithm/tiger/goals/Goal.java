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
package org.sosy_lab.cpachecker.core.algorithm.tiger.goals;

import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.ECPConcatenation;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.ECPEdgeSet;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.ECPNodeSet;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.ECPPredicate;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.ECPRepetition;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.ECPUnion;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.ECPVisitor;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.ElementaryCoveragePattern;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.translators.GuardedEdgeLabel;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.translators.GuardedLabel;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.translators.ToGuardedAutomatonTranslator;
import org.sosy_lab.cpachecker.util.automaton.NondeterministicFiniteAutomaton;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Region;

public class Goal {

  private ElementaryCoveragePattern mPattern;
  private NondeterministicFiniteAutomaton<GuardedEdgeLabel> mAutomaton;
  private int mIndex;
  private Region mPresenceCondition;

  public Goal(int pIndex, ElementaryCoveragePattern pPattern, GuardedEdgeLabel pAlphaLabel, GuardedEdgeLabel pInverseAlphaLabel, GuardedLabel pOmegaLabel,
      Region pPresenceCondition) {
    assert pPresenceCondition != null;
    mIndex = pIndex;
    mPattern = pPattern;
    mAutomaton = ToGuardedAutomatonTranslator.toAutomaton(mPattern, pAlphaLabel, pInverseAlphaLabel, pOmegaLabel);
    mPresenceCondition = pPresenceCondition;
  }

  public Goal(int pIndex, ElementaryCoveragePattern pPattern, NondeterministicFiniteAutomaton<GuardedEdgeLabel> pAutomaton, Region pPresenceCondition) {
    assert pPresenceCondition != null;
    mIndex = pIndex;
    mPattern = pPattern;
    mAutomaton = pAutomaton;
    mPresenceCondition = pPresenceCondition;
  }

  public int getIndex() {
    return mIndex;
  }
  public Region getPresenceCondition() {
    return mPresenceCondition;
  }

  public ElementaryCoveragePattern getPattern() {
    return mPattern;
  }

  public NondeterministicFiniteAutomaton<GuardedEdgeLabel> getAutomaton() {
    return mAutomaton;
  }

  public CFAEdge getCriticalEdge() {
    final ECPVisitor<CFAEdge> visitor = new ECPVisitor<CFAEdge>() {

      @Override
      public CFAEdge visit(ECPEdgeSet pEdgeSet) {
        if (pEdgeSet.size() == 1) {
          return pEdgeSet.iterator().next();
        }
        else {
          return null;
        }
      }

      @Override
      public CFAEdge visit(ECPNodeSet pNodeSet) {
        return null;
      }

      @Override
      public CFAEdge visit(ECPPredicate pPredicate) {
        return null;
      }

      @Override
      public CFAEdge visit(ECPConcatenation pConcatenation) {
        CFAEdge edge = null;

        for (int i = 0; i < pConcatenation.size(); i++) {
          ElementaryCoveragePattern ecp = pConcatenation.get(i);

          CFAEdge tmpEdge = ecp.accept(this);

          if (tmpEdge != null) {
            edge = tmpEdge;
          }
        }

        return edge;
      }

      @Override
      public CFAEdge visit(ECPUnion pUnion) {
        return null;
      }

      @Override
      public CFAEdge visit(ECPRepetition pRepetition) {
        return null;
      }

    };

    return getPattern().accept(visitor);
  }

  public String toSkeleton() {
    final ECPVisitor<Boolean> booleanVisitor = new ECPVisitor<Boolean>() {

      @Override
      public Boolean visit(ECPEdgeSet pEdgeSet) {
        if (pEdgeSet.size() <= 1) {
          return true;
        }

        return false;
      }

      @Override
      public Boolean visit(ECPNodeSet pNodeSet) {
        throw new RuntimeException("Unsupported Function");
      }

      @Override
      public Boolean visit(ECPPredicate pPredicate) {
        throw new RuntimeException("Unsupported Function");
      }

      @Override
      public Boolean visit(ECPConcatenation pConcatenation) {
        for (int i = 0; i < pConcatenation.size(); i++) {
          ElementaryCoveragePattern ecp = pConcatenation.get(i);

          if (ecp.accept(this)) {
            return true;
          }
        }

        return false;
      }

      @Override
      public Boolean visit(ECPUnion pUnion) {
        for (int i = 0; i < pUnion.size(); i++) {
          ElementaryCoveragePattern ecp = pUnion.get(i);

          if (ecp.accept(this)) {
            return true;
          }
        }

        return false;
      }

      @Override
      public Boolean visit(ECPRepetition pRepetition) {
        if (pRepetition.getSubpattern().accept(this)) {
          return true;
        }

        return false;
      }

    };

    ECPVisitor<String> visitor = new ECPVisitor<String>() {

      @Override
      public String visit(ECPEdgeSet pEdgeSet) {
        if (pEdgeSet.size() == 1) {
          return "[" + pEdgeSet.toString() + "]";
        }
        else if (pEdgeSet.size() == 0) {
          return "{}";
        }
        else {
          return "E";
        }
      }

      @Override
      public String visit(ECPNodeSet pNodeSet) {
        throw new RuntimeException("Unsupported Function");
      }

      @Override
      public String visit(ECPPredicate pPredicate) {
        throw new RuntimeException("Unsupported Function");
      }

      @Override
      public String visit(ECPConcatenation pConcatenation) {
        boolean b = false;
        StringBuffer str = new StringBuffer();

        boolean a = false;
        for (int i = 0; i < pConcatenation.size(); i++) {
          ElementaryCoveragePattern ecp = pConcatenation.get(i);

          if (ecp.accept(booleanVisitor)) {
            b = true;

            if (a) {
              str.append(".");
            }
            else if (i > 0) {
              str.append("E.");
            }
            str.append(ecp.accept(this));

            a = true;
          }
          else if (b) {
            b = false;

            if (a) {
              str.append(".");
            }
            str.append("E");

            a = true;
          }
        }

        return str.toString();
      }

      @Override
      public String visit(ECPUnion pUnion) {
        boolean b = false;
        StringBuffer str = new StringBuffer();

        boolean a = false;
        for (int i = 0; i < pUnion.size(); i++) {
          ElementaryCoveragePattern ecp = pUnion.get(i);

          if (ecp.accept(booleanVisitor)) {
            b = true;

            if (a) {
              str.append("+");
            }
            else if (i > 0) {
              str.append("E+");
            }
            str.append(ecp.accept(this));

            a = true;
          }
          else if (b) {
            b = false;

            if (a) {
              str.append("+");
            }
            str.append("E");

            a = true;
          }
        }

        return str.toString();
      }

      @Override
      public String visit(ECPRepetition pRepetition) {
        if (pRepetition.getSubpattern().accept(booleanVisitor)) {
          return "(" + pRepetition.getSubpattern().accept(this) + "*)";
        }
        else {
          return "E";
        }
      }

    };

    return getPattern().accept(visitor);
  }
}
