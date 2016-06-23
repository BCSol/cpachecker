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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.sosy_lab.cpachecker.cfa.ast.AStatement;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractStateWithAssumptions;
import org.sosy_lab.cpachecker.core.interfaces.AbstractWrapperState;
import org.sosy_lab.cpachecker.core.interfaces.Graphable;
import org.sosy_lab.cpachecker.core.interfaces.IntermediateTargetable;
import org.sosy_lab.cpachecker.core.interfaces.Property;
import org.sosy_lab.cpachecker.core.interfaces.Targetable;
import org.sosy_lab.cpachecker.util.Pair;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class PowersetAutomatonState implements AbstractWrapperState,
    Targetable, IntermediateTargetable, AbstractStateWithAssumptions,
    Serializable, Graphable, Iterable<AutomatonState> {

  private static class TopPowersetAutomatonState extends PowersetAutomatonState {

    private static final long serialVersionUID = -3468579071210217351L;

    public TopPowersetAutomatonState() {
      super(ImmutableSet.<AutomatonState>of());
    }

    @Override
    public int hashCode() {
      return super.hashCode();
    }

    @Override
    public boolean equals(Object pObj) {
      return (pObj instanceof TopPowersetAutomatonState);
    }

    @Override
    public boolean containsAtLeast(int pNumberOfStates) {
      return true;
    }
  }

  final static TopPowersetAutomatonState TOP = new TopPowersetAutomatonState();

  private static final long serialVersionUID = -8033111447137153782L;
  private final Set<AutomatonState> states;

  public PowersetAutomatonState(Collection<AutomatonState> elements) {
    Preconditions.checkNotNull(elements);
    this.states = ImmutableSet.copyOf(elements);
  }

  public boolean containsAtLeast(int pNumberOfStates) {
    return states.size() >= pNumberOfStates ;
  }

  @Override
  public boolean isTarget() {
    for (AutomatonState e : states) {
      if (e.isTarget()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Set<Property> getViolatedProperties() throws IllegalStateException {
    checkState(isTarget());
    Set<Property> properties = Sets.newHashSetWithExpectedSize(states.size());
    for (AutomatonState e : states) {
      if (e.isTarget()) {
        properties.addAll(e.getViolatedProperties());
      }
    }
    return properties;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append('{');
    if (states.size() > 10) {
      builder.append(String.format("%d different automata states!", states.size()));
    } else {
      for (AbstractState element : states) {
        builder.append(element.toString());
        builder.append("\n ");
      }
    }
    builder.replace(builder.length() - 1, builder.length(), "}");

    return builder.toString();
  }

  @Override
  public String toDOTLabel() {
    StringBuilder builder = new StringBuilder();

    for (AbstractState element : states) {
      if (element instanceof Graphable) {
        String label = ((Graphable)element).toDOTLabel();
        if (!label.isEmpty()) {
          builder.append(element.getClass().getSimpleName());
          builder.append(": ");
          builder.append(label);
          builder.append("\\n ");
        }
      }
    }

    return builder.toString();
  }

  @Override
  public boolean shouldBeHighlighted() {
    for (AbstractState element : states) {
      if (element instanceof Graphable) {
        if (((Graphable)element).shouldBeHighlighted()) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public List<AbstractState> getWrappedStates() {
    return ImmutableList.<AbstractState>copyOf(states);
  }

  public Set<AutomatonState> getAutomataStates() {
    return states;
  }

  @Override
  public Iterator<AutomatonState> iterator() {
    return states.iterator();
  }

  @Override
  public boolean isIntermediateTarget() {
    for (AutomatonState e : states) {
      if (e.isIntermediateTarget()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + states.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) { return true; }
    if (obj == null) { return false; }
    if (!(obj instanceof PowersetAutomatonState)) { return false; }

    PowersetAutomatonState other = (PowersetAutomatonState) obj;
    return this.states.equals(other.states);
  }

  @Override
  public ImmutableList<Pair<AStatement, Boolean>> getAssumptions() {
    Builder<Pair<AStatement, Boolean>> builder = ImmutableList.builder();
    for (AbstractStateWithAssumptions e : states) {
      builder.addAll(e.getAssumptions());
    }
    return builder.build();
  }

  @Override
  public List<AssumeEdge> getAsAssumeEdges(String pFunctionName) {
    Builder<AssumeEdge> builder = ImmutableList.builder();
    for (AbstractStateWithAssumptions e : states) {
      builder.addAll(e.getAsAssumeEdges(pFunctionName));
    }
    return builder.build();
  }

}
