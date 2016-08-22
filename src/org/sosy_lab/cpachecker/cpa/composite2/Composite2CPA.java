/*
 * CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2016  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.composite2;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AbstractCPAFactory;
import org.sosy_lab.cpachecker.core.defaults.MergeSepOperator;
import org.sosy_lab.cpachecker.core.defaults.SimplePrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysisWithBAM;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.WrapperCPA;
import org.sosy_lab.cpachecker.core.interfaces.pcc.ProofChecker;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateAbstractionManager;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Composite2CPA implements ConfigurableProgramAnalysis, StatisticsProvider, WrapperCPA, ConfigurableProgramAnalysisWithBAM, ProofChecker {

  @Options(prefix="cpa.composite")
  private static class CompositeOptions {
    @Option(secure=true, toUppercase=true, values={"PLAIN", "AGREE"},
        description="which composite merge operator to use (plain or agree)\n"
          + "Both delegate to the component cpas, but agree only allows "
          + "merging if all cpas agree on this. This is probably what you want.")
    private String merge = "AGREE";

    @Option(secure=true,
    description="inform Composite CPA if it is run in a CPA enabled analysis because then it must "
      + "behave differently during merge.")
    private boolean inCPAEnabledAnalysis = false;

    @Option(secure=true, description="Separate the target states when building the product.")
    private boolean separateTargetStates = false;
  }

  private static class CompositeCPAFactory extends AbstractCPAFactory {

    private CFA cfa = null;
    private ImmutableList<ConfigurableProgramAnalysis> cpas = null;

    @Override
    public ConfigurableProgramAnalysis createInstance() throws InvalidConfigurationException {
      Preconditions.checkState(cpas != null, "Composite2CPA needs wrapped CPAs!");
      Preconditions.checkState(cfa != null, "Composite2CPA needs CFA information!");

      CompositeOptions options = new CompositeOptions();
      getConfiguration().inject(options);

      Builder<AbstractDomain> domains = ImmutableList.builder();
      Builder<TransferRelation> transferRelations = ImmutableList.builder();
      Builder<MergeOperator> mergeOperators = ImmutableList.builder();
      Builder<StopOperator> stopOperators = ImmutableList.builder();
      Builder<PrecisionAdjustment> precisionAdjustments = ImmutableList.builder();
      Builder<SimplePrecisionAdjustment> simplePrecisionAdjustments = ImmutableList.builder();

      boolean mergeSep = true;
      boolean simplePrec = true;

      PredicateAbstractionManager abmgr = null;

      for (ConfigurableProgramAnalysis sp : cpas) {
        if (sp instanceof org.sosy_lab.cpachecker.cpa.predicate.PredicateCPA) {
          abmgr = ((org.sosy_lab.cpachecker.cpa.predicate.PredicateCPA)sp).getPredicateManager();
        }

        domains.add(sp.getAbstractDomain());
        transferRelations.add(sp.getTransferRelation());
        stopOperators.add(sp.getStopOperator());

        PrecisionAdjustment prec = sp.getPrecisionAdjustment();
        if (prec instanceof SimplePrecisionAdjustment) {
          simplePrecisionAdjustments.add((SimplePrecisionAdjustment) prec);
        } else {
          simplePrec = false;
        }
        precisionAdjustments.add(prec);

        MergeOperator merge = sp.getMergeOperator();
        if (merge != MergeSepOperator.getInstance()) {
          mergeSep = false;
        }
        mergeOperators.add(merge);
      }

      ImmutableList<StopOperator> stopOps = stopOperators.build();

      MergeOperator compositeMerge;
      if (mergeSep) {
        compositeMerge = MergeSepOperator.getInstance();
      } else {
        if (options.inCPAEnabledAnalysis) {
          if (options.merge.equals("AGREE")) {
            compositeMerge = new Composite2MergeAgreeCPAEnabledAnalysisOperator(mergeOperators.build(), stopOps, abmgr);
          } else {
            throw new InvalidConfigurationException("Merge PLAIN is currently not supported in predicated analysis");
          }
        } else {
          if (options.merge.equals("AGREE")) {
            compositeMerge = new Composite2MergeAgreeOperator(mergeOperators.build(), stopOps);
          } else if (options.merge.equals("PLAIN")) {
            compositeMerge = new Composite2MergePlainOperator(mergeOperators.build());
          } else {
            throw new AssertionError();
          }
        }
      }

      Composite2Domain compositeDomain = new Composite2Domain(domains.build());
      Composite2StopOperator compositeStop = new Composite2StopOperator(stopOps);

      final TransferRelation compositeTransfer;
      if (options.separateTargetStates) {
        compositeTransfer = new Composite2TransferRelationNoFullCrossProd(transferRelations.build(), getConfiguration(), cfa);
      } else {
        compositeTransfer = new Composite2TransferRelation(transferRelations.build(), getConfiguration(), cfa);
      }



      PrecisionAdjustment compositePrecisionAdjustment;
      if (simplePrec) {
        compositePrecisionAdjustment = new Composite2SimplePrecisionAdjustment(simplePrecisionAdjustments.build());
      } else {
        compositePrecisionAdjustment =
            new Composite2PrecisionAdjustment(precisionAdjustments.build(), getLogger());
      }

      return new Composite2CPA(
          compositeDomain, compositeTransfer, compositeMerge, compositeStop,
          compositePrecisionAdjustment, cpas);
    }

    @Override
    public CPAFactory setChild(ConfigurableProgramAnalysis pChild)
        throws UnsupportedOperationException {
      throw new UnsupportedOperationException("Use Composite2CPA to wrap several CPAs!");
    }

    @Override
    public CPAFactory setChildren(List<ConfigurableProgramAnalysis> pChildren) {
      Preconditions.checkNotNull(pChildren);
      Preconditions.checkArgument(!pChildren.isEmpty());
      Preconditions.checkState(cpas == null);

      cpas = ImmutableList.copyOf(pChildren);
      return this;
    }

    @Override
    public <T> CPAFactory set(T pObject, Class<T> pClass) throws UnsupportedOperationException {
      if (pClass.equals(CFA.class)) {
        cfa = (CFA)pObject;
      }
      return super.set(pObject, pClass);
    }
  }

  public static CPAFactory factory() {
    return new CompositeCPAFactory();
  }

  private final AbstractDomain abstractDomain;
  private final TransferRelation transferRelation;
  private final MergeOperator mergeOperator;
  private final Composite2StopOperator stopOperator;
  private final PrecisionAdjustment precisionAdjustment;
  private final Reducer reducer;

  private final ImmutableList<ConfigurableProgramAnalysis> cpas;

  protected Composite2CPA(
      AbstractDomain abstractDomain,
      TransferRelation transferRelation,
      MergeOperator mergeOperator,
      Composite2StopOperator stopOperator,
      PrecisionAdjustment precisionAdjustment,
      ImmutableList<ConfigurableProgramAnalysis> cpas) {
    this.abstractDomain = abstractDomain;
    this.transferRelation = transferRelation;
    this.mergeOperator = mergeOperator;
    this.stopOperator = stopOperator;
    this.precisionAdjustment = precisionAdjustment;
    this.cpas = cpas;

    List<Reducer> wrappedReducers = new ArrayList<>();
    for (ConfigurableProgramAnalysis cpa : cpas) {
      if (cpa instanceof ConfigurableProgramAnalysisWithBAM) {
        wrappedReducers.add(((ConfigurableProgramAnalysisWithBAM) cpa).getReducer());
      } else {
        wrappedReducers.clear();
        break;
      }
    }
    if (!wrappedReducers.isEmpty()) {
      reducer = new Composite2Reducer(wrappedReducers);
    } else {
      reducer = null;
    }
  }

  @Override
  public AbstractDomain getAbstractDomain() {
    return abstractDomain;
  }

  @Override
  public TransferRelation getTransferRelation() {
    return transferRelation;
  }

  @Override
  public MergeOperator getMergeOperator() {
    return mergeOperator;
  }

  @Override
  public StopOperator getStopOperator() {
    return stopOperator;
  }

  @Override
  public PrecisionAdjustment getPrecisionAdjustment() {
    return precisionAdjustment;
  }

  @Override
  public Reducer getReducer() {
    return reducer;
  }

  @Override
  public AbstractState getInitialState(CFANode pNode, StateSpacePartition pPartition) {
    Preconditions.checkNotNull(pNode);

    Builder<AbstractState> initialStates = ImmutableList.builder();
    for (ConfigurableProgramAnalysis sp : cpas) {
      initialStates.add(sp.getInitialState(pNode, pPartition));
    }

    return new Composite2State(initialStates.build());
  }

  @Override
  public Precision getInitialPrecision(CFANode pNode, StateSpacePartition partition) {
    Preconditions.checkNotNull(pNode);

    Builder<Precision> initialPrecisions = ImmutableList.builder();
    for (ConfigurableProgramAnalysis sp : cpas) {
      initialPrecisions.add(sp.getInitialPrecision(pNode, partition));
    }
    return new Composite2Precision(initialPrecisions.build());
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    for (ConfigurableProgramAnalysis cpa: cpas) {
      if (cpa instanceof StatisticsProvider) {
        ((StatisticsProvider)cpa).collectStatistics(pStatsCollection);
      }
    }

    if (precisionAdjustment instanceof StatisticsProvider) {
      ((StatisticsProvider)precisionAdjustment).collectStatistics(pStatsCollection);
    }
  }

  @Override
  public <T extends ConfigurableProgramAnalysis> T retrieveWrappedCpa(Class<T> pType) {
    if (pType.isAssignableFrom(getClass())) {
      return pType.cast(this);
    }
    for (ConfigurableProgramAnalysis cpa : cpas) {
      if (pType.isAssignableFrom(cpa.getClass())) {
        return pType.cast(cpa);
      } else if (cpa instanceof WrapperCPA) {
        T result = ((WrapperCPA)cpa).retrieveWrappedCpa(pType);
        if (result != null) {
          return result;
        }
      }
    }
    return null;
  }

  @Override
  public <T extends ConfigurableProgramAnalysis> Collection<T> retrieveWrappedCpas(Class<T> pType) {
    Builder<T> result = ImmutableList.builder();

    if (pType.isAssignableFrom(getClass())) {
      result.add(pType.cast(this));
    }

    for (ConfigurableProgramAnalysis cpa : cpas) {
      if (pType.isAssignableFrom(cpa.getClass())) {
        result.add(pType.cast(cpa));
      } else if (cpa instanceof WrapperCPA) {
        Collection<T> c = ((WrapperCPA)cpa).retrieveWrappedCpas(pType);
        if (c != null) {
          result.addAll(c);
        }
      }
    }

    return result.build();
  }

  @Override
  public ImmutableList<ConfigurableProgramAnalysis> getWrappedCPAs() {
    return cpas;
  }

  @Override
  public boolean areAbstractSuccessors(AbstractState pElement, CFAEdge pCfaEdge, Collection<? extends AbstractState> pSuccessors) throws CPATransferException, InterruptedException {
    if (transferRelation instanceof Composite2TransferRelation) {
      return ((Composite2TransferRelation) transferRelation).areAbstractSuccessors(pElement, pCfaEdge, pSuccessors, cpas);
    } else {
      throw new RuntimeException("Not yet implemented.");
    }
  }

  @Override
  public boolean isCoveredBy(AbstractState pElement, AbstractState pOtherElement) throws CPAException, InterruptedException {
    return stopOperator.isCoveredBy(pElement, pOtherElement, cpas);
  }
}
