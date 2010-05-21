package org.sosy_lab.cpachecker.fllesh.ecp.translators;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.fllesh.ecp.ECPConcatenation;
import org.sosy_lab.cpachecker.fllesh.ecp.ECPEdgeSet;
import org.sosy_lab.cpachecker.fllesh.ecp.ECPGuard;
import org.sosy_lab.cpachecker.fllesh.ecp.ECPNodeSet;
import org.sosy_lab.cpachecker.fllesh.ecp.ECPPredicate;
import org.sosy_lab.cpachecker.fllesh.ecp.ECPRepetition;
import org.sosy_lab.cpachecker.fllesh.ecp.ECPUnion;
import org.sosy_lab.cpachecker.fllesh.ecp.ECPVisitor;
import org.sosy_lab.cpachecker.fllesh.ecp.ElementaryCoveragePattern;
import org.sosy_lab.cpachecker.fllesh.ecp.reduced.Automaton;

public class Translator {
  
  public static Automaton<GuardedLabel> translate(ElementaryCoveragePattern pPattern) {
    Visitor lVisitor = new Visitor();
    
    pPattern.accept(lVisitor);
    
    lVisitor.getAutomaton().addToFinalStates(lVisitor.getFinalState());
    
    return lVisitor.getAutomaton();
  }
  
  public static Automaton<GuardedLabel> removeLambdaEdges(Automaton<GuardedLabel> pAutomaton, CFAEdge pAlphaEdge, CFAEdge pOmegaEdge) {
    /** first we augment the given automaton with the alpha and omega edge */
    ECPEdgeSet lAlphaSet = new ECPEdgeSet(pAlphaEdge);
    ECPEdgeSet lOmegaSet = new ECPEdgeSet(pOmegaEdge);
    
    Automaton<GuardedLabel>.State lNewInitialState = pAutomaton.createState();
    Automaton<GuardedLabel>.Edge lInitialEdge = pAutomaton.createEdge(lNewInitialState, pAutomaton.getInitialState(), new GuardedEdgeLabel(lAlphaSet));
    pAutomaton.setInitialState(lNewInitialState);
    
    Automaton<GuardedLabel>.State lNewFinalState = pAutomaton.createState();
    GuardedEdgeLabel lOmegaLabel = new GuardedEdgeLabel(lOmegaSet);
    for (Automaton<GuardedLabel>.State lFinalState : pAutomaton.getFinalStates()) {
      pAutomaton.createEdge(lFinalState, lNewFinalState, lOmegaLabel);
    }
    
    pAutomaton.setFinalStates(Collections.singleton(lNewFinalState));
    
    /** now we remove guarded lambda edges */
    
    Automaton<GuardedLabel> lAutomaton = new Automaton<GuardedLabel>();
    Map<Automaton<GuardedLabel>.State, Automaton<GuardedLabel>.State> lStateMap = new HashMap<Automaton<GuardedLabel>.State, Automaton<GuardedLabel>.State>();
    lStateMap.put(lNewInitialState, lAutomaton.getInitialState());
    
    List<Automaton<GuardedLabel>.Edge> lWorklist = new LinkedList<Automaton<GuardedLabel>.Edge>();
    lWorklist.add(lInitialEdge);
    
    Set<Automaton<GuardedLabel>.Edge> lReachedEdges = new HashSet<Automaton<GuardedLabel>.Edge>();
    
    while (!lWorklist.isEmpty()) {
      Automaton<GuardedLabel>.Edge lCurrentEdge = lWorklist.remove(0);
    
      if (lReachedEdges.contains(lCurrentEdge)) {
        continue;
      }
      
      lReachedEdges.add(lCurrentEdge);
      
      GuardedState lInitialGuardedState = new GuardedState(lCurrentEdge.getTarget(), lCurrentEdge.getLabel().getGuards());
      
      /** determine the lambda successors */
      List<GuardedState> lStatesWorklist = new LinkedList<GuardedState>();
      lStatesWorklist.add(lInitialGuardedState);
      
      Set<GuardedState> lReachedStates = new HashSet<GuardedState>();
      
      while (!lStatesWorklist.isEmpty()) {
        GuardedState lCurrentState = lStatesWorklist.remove(0);
        
        boolean lIsCovered = false;
        
        for (GuardedState lGuardedState : lReachedStates) {
          if (lGuardedState.covers(lCurrentState)) {
            lIsCovered = true;
          }
        }
        
        if (lIsCovered) {
          continue;
        }
        
        lReachedStates.add(lCurrentState);
        
        for (Automaton<GuardedLabel>.Edge lOutgoingEdge : pAutomaton.getOutgoingEdges(lCurrentState.getState())) {
          if (lOutgoingEdge.getLabel() instanceof GuardedLambdaLabel) {
            GuardedState lNewState = new GuardedState(lOutgoingEdge.getTarget(), lCurrentState, lOutgoingEdge.getLabel().getGuards());
            lStatesWorklist.add(lNewState);
          }
        }
      }
      
      Automaton<GuardedLabel>.State lOldSource = lCurrentEdge.getSource();
      
      if (!lStateMap.containsKey(lOldSource)) {
        lStateMap.put(lOldSource, lAutomaton.createState());
      }
      
      Automaton<GuardedLabel>.State lSource = lStateMap.get(lOldSource);
      
      GuardedLabel lCurrentLabel = lCurrentEdge.getLabel();
      
      GuardedEdgeLabel lEdgeLabel = (GuardedEdgeLabel)lCurrentLabel;
      
      ECPEdgeSet lCurrentEdgeSet = lEdgeLabel.getEdgeSet();
      
      for (GuardedState lReachedState : lReachedStates) {
        boolean lHasNonLambdaEdge = false;
        
        for (Automaton<GuardedLabel>.Edge lOutgoingEdge : pAutomaton.getOutgoingEdges(lReachedState.getState())) {
          if (!(lOutgoingEdge.getLabel() instanceof GuardedLambdaLabel)) {
            lHasNonLambdaEdge = true;
            
            lWorklist.add(lOutgoingEdge);
          }
        }
        
        // final state has no outgoing edges
        if (pAutomaton.getOutgoingEdges(lReachedState.getState()).isEmpty()) {
          lHasNonLambdaEdge = true;
        }
        
        if (lHasNonLambdaEdge) {
          Automaton<GuardedLabel>.State lOldTarget = lReachedState.getState();
          
          if (!lStateMap.containsKey(lOldTarget)) {
            lStateMap.put(lOldTarget, lAutomaton.createState());
          }
          
          Automaton<GuardedLabel>.State lTarget = lStateMap.get(lOldTarget);
                    
          lAutomaton.createEdge(lSource, lTarget, new GuardedEdgeLabel(lCurrentEdgeSet, lReachedState.getGuards()));
        }
      }
    }
    
    for (Automaton<GuardedLabel>.State lFinalState : pAutomaton.getFinalStates()) {
      lAutomaton.addToFinalStates(lStateMap.get(lFinalState));
    }
    
    return lAutomaton;
  }
  
  public static Automaton<GuardedLabel> removeNodeSetGuards(Automaton<GuardedLabel> pAutomaton) {
    Automaton<GuardedLabel> lAutomaton = new Automaton<GuardedLabel>();
    
    Map<Automaton<GuardedLabel>.State, Automaton<GuardedLabel>.State> lStateMap = new HashMap<Automaton<GuardedLabel>.State, Automaton<GuardedLabel>.State>();
    lStateMap.put(pAutomaton.getInitialState(), lAutomaton.getInitialState());
    
    List<Automaton<GuardedLabel>.Edge> lWorklist = new LinkedList<Automaton<GuardedLabel>.Edge>();
    lWorklist.addAll(pAutomaton.getOutgoingEdges(pAutomaton.getInitialState()));
    
    Set<Automaton<GuardedLabel>.Edge> lReachedEdges = new HashSet<Automaton<GuardedLabel>.Edge>();
    
    while (!lWorklist.isEmpty()) {
      Automaton<GuardedLabel>.Edge lCurrentEdge = lWorklist.remove(0);
    
      if (lReachedEdges.contains(lCurrentEdge)) {
        continue;
      }
      
      lReachedEdges.add(lCurrentEdge);
      
      GuardedLabel lLabel = lCurrentEdge.getLabel();
      
      if (lLabel.hasGuards()) {
        ECPNodeSet lNodeSet = null;
        
        Set<ECPGuard> lRemainingGuards = new HashSet<ECPGuard>();
        
        for (ECPGuard lGuard : lLabel.getGuards()) {
          if (lGuard instanceof ECPNodeSet) {
            if (lNodeSet == null) {
              lNodeSet = (ECPNodeSet)lGuard;
            }
            else {
              lNodeSet = lNodeSet.intersect((ECPNodeSet)lGuard);
            }
          }
          else {
            lRemainingGuards.add(lGuard);
          }
        }
        
        if (lNodeSet != null) {
          if (!lNodeSet.isEmpty()) {
            assert(lLabel instanceof GuardedEdgeLabel);
            
            GuardedEdgeLabel lEdgeLabel = (GuardedEdgeLabel)lLabel;
            
            ECPEdgeSet lCurrentEdgeSet = lEdgeLabel.getEdgeSet();
            
            Set<CFAEdge> lRemainingCFAEdges = new HashSet<CFAEdge>();
            
            for (CFAEdge lCFAEdge : lCurrentEdgeSet) {
              if (lNodeSet.contains(lCFAEdge.getSuccessor())) {
                lRemainingCFAEdges.add(lCFAEdge);
              }
            }
            
            if (!lRemainingCFAEdges.isEmpty()) {
              ECPEdgeSet lNewEdgeSet = new ECPEdgeSet(lRemainingCFAEdges);
              
              GuardedEdgeLabel lNewGuard = new GuardedEdgeLabel(lNewEdgeSet, lRemainingGuards);
              
              // add edge
              
              Automaton<GuardedLabel>.State lCurrentSource = lCurrentEdge.getSource();
              Automaton<GuardedLabel>.State lCurrentTarget = lCurrentEdge.getTarget();
              
              if (!lStateMap.containsKey(lCurrentSource)) {
                lStateMap.put(lCurrentSource, lAutomaton.createState());
              }
              
              if (!lStateMap.containsKey(lCurrentTarget)) {
                lStateMap.put(lCurrentTarget, lAutomaton.createState());
              }
              
              Automaton<GuardedLabel>.State lSourceState = lStateMap.get(lCurrentSource);
              Automaton<GuardedLabel>.State lTargetState = lStateMap.get(lCurrentTarget);
              
              lAutomaton.createEdge(lSourceState, lTargetState, lNewGuard);
            }
          }
        }
        else {
          assert(lLabel instanceof GuardedEdgeLabel);
          
          GuardedEdgeLabel lEdgeLabel = (GuardedEdgeLabel)lLabel;
          
          if (!lEdgeLabel.getEdgeSet().isEmpty()) {
            // add edge
            Automaton<GuardedLabel>.State lCurrentSource = lCurrentEdge.getSource();
            Automaton<GuardedLabel>.State lCurrentTarget = lCurrentEdge.getTarget();
            
            if (!lStateMap.containsKey(lCurrentSource)) {
              lStateMap.put(lCurrentSource, lAutomaton.createState());
            }
            
            if (!lStateMap.containsKey(lCurrentTarget)) {
              lStateMap.put(lCurrentTarget, lAutomaton.createState());
            }
            
            Automaton<GuardedLabel>.State lSourceState = lStateMap.get(lCurrentSource);
            Automaton<GuardedLabel>.State lTargetState = lStateMap.get(lCurrentTarget);
            
            lAutomaton.createEdge(lSourceState, lTargetState, lEdgeLabel);
          }
        }
      }
      else {
        assert(lLabel instanceof GuardedEdgeLabel);
        
        GuardedEdgeLabel lEdgeLabel = (GuardedEdgeLabel)lLabel;
        
        if (!lEdgeLabel.getEdgeSet().isEmpty()) {
          // add edge
          Automaton<GuardedLabel>.State lCurrentSource = lCurrentEdge.getSource();
          Automaton<GuardedLabel>.State lCurrentTarget = lCurrentEdge.getTarget();
          
          if (!lStateMap.containsKey(lCurrentSource)) {
            lStateMap.put(lCurrentSource, lAutomaton.createState());
          }
          
          if (!lStateMap.containsKey(lCurrentTarget)) {
            lStateMap.put(lCurrentTarget, lAutomaton.createState());
          }
          
          Automaton<GuardedLabel>.State lSourceState = lStateMap.get(lCurrentSource);
          Automaton<GuardedLabel>.State lTargetState = lStateMap.get(lCurrentTarget);
          
          lAutomaton.createEdge(lSourceState, lTargetState, lEdgeLabel);
        }
      }
      
      lWorklist.addAll(pAutomaton.getOutgoingEdges(lCurrentEdge.getTarget()));
    }
    
    for (Automaton<GuardedLabel>.State lFinalState : pAutomaton.getFinalStates()) {
      if (lStateMap.containsKey(lFinalState)) {
        lAutomaton.addToFinalStates(lStateMap.get(lFinalState));
      }
    }
    
    return lAutomaton;
  }
  
  private static class Visitor implements ECPVisitor<Void> {

    private Automaton<GuardedLabel> mAutomaton;

    private Automaton<GuardedLabel>.State mInitialState;
    private Automaton<GuardedLabel>.State mFinalState;
    
    public Visitor() {
      mAutomaton = new Automaton<GuardedLabel>();
      setInitialState(mAutomaton.getInitialState());
      setFinalState(mAutomaton.createState());
    }
    
    public Automaton<GuardedLabel> getAutomaton() {
      return mAutomaton;
    }
    
    public Automaton<GuardedLabel>.State getInitialState() {
      return mInitialState;
    }
    
    public Automaton<GuardedLabel>.State getFinalState() {
      return mFinalState;
    }
    
    public void setInitialState(Automaton<GuardedLabel>.State pInitialState) {
      mInitialState = pInitialState;
    }
    
    public void setFinalState(Automaton<GuardedLabel>.State pFinalState) {
      mFinalState = pFinalState;
    }
    
    @Override
    public Void visit(ECPEdgeSet pEdgeSet) {
      mAutomaton.createEdge(getInitialState(), getFinalState(), new GuardedEdgeLabel(pEdgeSet));
      
      return null;
    }

    @Override
    public Void visit(ECPNodeSet pNodeSet) {
      mAutomaton.createEdge(getInitialState(), getFinalState(), new GuardedLambdaLabel(pNodeSet));
      
      return null;
    }

    @Override
    public Void visit(ECPPredicate pPredicate) {
      mAutomaton.createEdge(getInitialState(), getFinalState(), new GuardedLambdaLabel(pPredicate));
      
      return null;
    }

    @Override
    public Void visit(ECPConcatenation pConcatenation) {
      if (pConcatenation.isEmpty()) {
        mAutomaton.createEdge(getInitialState(), getFinalState(), new GuardedLambdaLabel());
      }
      else {
        Automaton<GuardedLabel>.State lTmpInitialState = getInitialState();
        Automaton<GuardedLabel>.State lTmpFinalState = getFinalState();
        
        for (int i = 0; i < pConcatenation.size(); i++) {
          ElementaryCoveragePattern lSubpattern = pConcatenation.get(i);
          
          if (i > 0) {
            // use final state from before
            setInitialState(getFinalState());
          }
          
          if (i == pConcatenation.size() - 1) {
            // use lTmpFinalState
            setFinalState(lTmpFinalState);
          }
          else {
            // create new final state
            setFinalState(mAutomaton.createState());
          }
          
          lSubpattern.accept(this);
        }
        
        setInitialState(lTmpInitialState);
      }
      
      return null;
    }

    @Override
    public Void visit(ECPUnion pUnion) {
      if (pUnion.isEmpty()) {
        mAutomaton.createEdge(getInitialState(), getFinalState(), new GuardedLambdaLabel());
      }
      else if (pUnion.size() == 1) {
        pUnion.get(0).accept(this);
      }
      else {
        Automaton<GuardedLabel>.State lTmpInitialState = getInitialState();
        
        for (ElementaryCoveragePattern lSubpattern : pUnion) {
          setInitialState(mAutomaton.createState());
          
          mAutomaton.createEdge(lTmpInitialState, getInitialState(), new GuardedLambdaLabel());
          
          lSubpattern.accept(this);
        }
        
        setInitialState(lTmpInitialState);
      }
      
      return null;
    }

    @Override
    public Void visit(ECPRepetition pRepetition) {
      Automaton<GuardedLabel>.State lTmpInitialState = getInitialState();
      Automaton<GuardedLabel>.State lTmpFinalState = getFinalState();
      
      mAutomaton.createEdge(lTmpInitialState, lTmpFinalState, new GuardedLambdaLabel());
      
      setInitialState(mAutomaton.createState());
      setFinalState(lTmpInitialState);
      
      mAutomaton.createEdge(lTmpInitialState, getInitialState(), new GuardedLambdaLabel());
      
      pRepetition.getSubpattern().accept(this);
      
      setInitialState(lTmpInitialState);
      setFinalState(lTmpFinalState);
      
      return null;
    }
    
  }
  
}
