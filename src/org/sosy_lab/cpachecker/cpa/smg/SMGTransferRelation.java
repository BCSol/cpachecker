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
package org.sosy_lab.cpachecker.cpa.smg;

import static com.google.common.collect.FluentIterable.from;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.log.LogManagerWithoutDuplicates;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression.BinaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CDesignatedInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldDesignator;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerList;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.cfa.types.c.CArrayType;
import org.sosy_lab.cpachecker.cfa.types.c.CBitFieldType;
import org.sosy_lab.cpachecker.cfa.types.c.CComplexType.ComplexTypeKind;
import org.sosy_lab.cpachecker.cfa.types.c.CCompositeType;
import org.sosy_lab.cpachecker.cfa.types.c.CCompositeType.CCompositeTypeMemberDeclaration;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.cpa.automaton.AutomatonState;
import org.sosy_lab.cpachecker.cpa.smg.SMGCPA.SMGExportLevel;
import org.sosy_lab.cpachecker.cpa.smg.evaluator.SMGExpressionEvaluator;
import org.sosy_lab.cpachecker.cpa.smg.evaluator.SMGExpressionEvaluator.AssumeVisitor;
import org.sosy_lab.cpachecker.cpa.smg.evaluator.SMGExpressionEvaluator.LValueAssignmentVisitor;
import org.sosy_lab.cpachecker.cpa.smg.evaluator.SMGExpressionEvaluator.SMGAddressAndState;
import org.sosy_lab.cpachecker.cpa.smg.evaluator.SMGExpressionEvaluator.SMGAddressValueAndStateList;
import org.sosy_lab.cpachecker.cpa.smg.evaluator.SMGExpressionEvaluator.SMGExplicitValueAndState;
import org.sosy_lab.cpachecker.cpa.smg.evaluator.SMGExpressionEvaluator.SMGValueAndState;
import org.sosy_lab.cpachecker.cpa.smg.evaluator.SMGExpressionEvaluator.SMGValueAndStateList;
import org.sosy_lab.cpachecker.cpa.smg.evaluator.SMGRightHandSideEvaluator;
import org.sosy_lab.cpachecker.cpa.smg.graphs.PredRelation;
import org.sosy_lab.cpachecker.cpa.smg.objects.SMGObject;
import org.sosy_lab.cpachecker.cpa.smg.objects.SMGRegion;
import org.sosy_lab.cpachecker.cpa.smg.smgvalue.SMGAddress;
import org.sosy_lab.cpachecker.cpa.smg.smgvalue.SMGExplicitValue;
import org.sosy_lab.cpachecker.cpa.smg.smgvalue.SMGKnownAddVal;
import org.sosy_lab.cpachecker.cpa.smg.smgvalue.SMGKnownExpValue;
import org.sosy_lab.cpachecker.cpa.smg.smgvalue.SMGKnownSymValue;
import org.sosy_lab.cpachecker.cpa.smg.smgvalue.SMGSymbolicValue;
import org.sosy_lab.cpachecker.cpa.smg.smgvalue.SMGUnknownValue;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.predicates.BlockOperator;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.SolverException;

public class SMGTransferRelation extends SingleEdgeTransferRelation {

  final LogManagerWithoutDuplicates logger;
  final MachineModel machineModel;
  private final AtomicInteger id_counter;

  final SMGOptions options;
  final SMGExportDotOption exportSMGOptions;

  final SMGRightHandSideEvaluator expressionEvaluator;

  private final BlockOperator blockOperator;
  private final SMGPredicateManager smgPredicateManager;

  /**
   * Indicates whether the executed statement could result
   * in a failure of the malloc function.
   */
  public boolean possibleMallocFail;

  public SMGTransferRelationKind kind;

  /**
   * name for the special variable used as container for return values of functions
   */
  public static final String FUNCTION_RETURN_VAR = "___cpa_temp_result_var_";

  public final SMGBuiltins builtins;

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(
      AbstractState state, Precision precision, CFAEdge cfaEdge)
          throws CPATransferException, InterruptedException {
    return getAbstractSuccessorsForEdge((SMGState) state, cfaEdge);
  }

  private SMGTransferRelation(LogManager pLogger,
      MachineModel pMachineModel, SMGExportDotOption pExportOptions, SMGTransferRelationKind pKind,
      SMGPredicateManager pSMGPredicateManager, BlockOperator pBlockOperator, SMGOptions pOptions) {
    logger = new LogManagerWithoutDuplicates(pLogger);
    machineModel = pMachineModel;
    expressionEvaluator = new SMGRightHandSideEvaluator(this, logger, machineModel, pOptions);
    id_counter = new AtomicInteger(0);
    smgPredicateManager = pSMGPredicateManager;
    blockOperator = pBlockOperator;
    options = pOptions;
    exportSMGOptions = pExportOptions;
    kind = pKind;
    builtins = new SMGBuiltins(this);
  }

  public static SMGTransferRelation createTransferRelationForCEX(
      LogManager pLogger, MachineModel pMachineModel, SMGPredicateManager pSMGPredicateManager,
      BlockOperator pBlockOperator, SMGOptions pOptions) {
    return new SMGTransferRelation( pLogger, pMachineModel,
            SMGExportDotOption.getNoExportInstance(), SMGTransferRelationKind.STATIC,
            pSMGPredicateManager, pBlockOperator, pOptions);
  }

  public static SMGTransferRelation createTransferRelation(LogManager pLogger,
      MachineModel pMachineModel, SMGExportDotOption pExportOptions,
      SMGPredicateManager pSMGPredicateManager,
      BlockOperator pBlockOperator, SMGOptions pOptions) {
    return new SMGTransferRelation( pLogger, pMachineModel, pExportOptions,
        SMGTransferRelationKind.STATIC, pSMGPredicateManager, pBlockOperator, pOptions);
  }

  public static SMGTransferRelation createTransferRelationForInterpolation(
      LogManager pLogger,
      MachineModel pMachineModel, SMGPredicateManager pSMGPredicateManager,
      BlockOperator pBlockOperator, SMGOptions pOptions) {
    return new SMGTransferRelation(pLogger, pMachineModel,
            SMGExportDotOption.getNoExportInstance(), SMGTransferRelationKind.REFINEMENT,
            pSMGPredicateManager, pBlockOperator, pOptions);
  }

  private Collection<? extends AbstractState> getAbstractSuccessorsForEdge(
      SMGState state, CFAEdge cfaEdge)
          throws CPATransferException {
    logger.log(Level.ALL, "SMG GetSuccessor >>");
    logger.log(Level.ALL, "Edge:", cfaEdge.getEdgeType());
    logger.log(Level.ALL, "Code:", cfaEdge.getCode());

    List<SMGState> successors;

    SMGState smgState = state;

    switch (cfaEdge.getEdgeType()) {
    case DeclarationEdge:
      successors = handleDeclaration(smgState, (CDeclarationEdge) cfaEdge);
      break;

    case StatementEdge:
      successors = handleStatement(smgState, (CStatementEdge) cfaEdge);
      plotWhenConfigured(successors, cfaEdge.getDescription(), SMGExportLevel.INTERESTING);
      break;

      // this is an assumption, e.g. if (a == b)
    case AssumeEdge:
      CAssumeEdge assumeEdge = (CAssumeEdge) cfaEdge;
      successors = handleAssumption(smgState, assumeEdge.getExpression(),
          cfaEdge, assumeEdge.getTruthAssumption(), true);
      plotWhenConfigured(successors, cfaEdge.getDescription(), SMGExportLevel.INTERESTING);
      break;

    case FunctionCallEdge:
      CFunctionCallEdge functionCallEdge = (CFunctionCallEdge) cfaEdge;
      successors = handleFunctionCall(smgState, functionCallEdge);
      plotWhenConfigured(successors, cfaEdge.getDescription(), SMGExportLevel.INTERESTING);
      break;

    // this is a return edge from function, this is different from return statement
    // of the function. See case for statement edge for details
    case FunctionReturnEdge:
      CFunctionReturnEdge functionReturnEdge = (CFunctionReturnEdge) cfaEdge;
      successors = handleFunctionReturn(smgState, functionReturnEdge);
      if (options.isCheckForMemLeaksAtEveryFrameDrop()) {
        for (SMGState successor : successors) {
          String name = String.format("%03d-%03d-%03d", successor.getPredecessorId(), successor.getId(), id_counter.getAndIncrement());
          SMGUtils.plotWhenConfigured("beforePrune" + name, successor, cfaEdge.getDescription(), logger,
              SMGExportLevel.INTERESTING, exportSMGOptions);
          successor.pruneUnreachable();
        }
      }
      plotWhenConfigured(successors, cfaEdge.getDescription(), SMGExportLevel.INTERESTING);
      break;

    case ReturnStatementEdge:
      CReturnStatementEdge returnEdge = (CReturnStatementEdge) cfaEdge;
      // this statement is a function return, e.g. return (a);
      // note that this is different from return edge
      // this is a statement edge which leads the function to the
      // last node of its CFA, where return edge is from that last node
      // to the return site of the caller function
      successors = handleExitFromFunction(smgState, returnEdge);

      // if this is the entry function, there is no FunctionReturnEdge
      // so we have to check for memleaks here
      if (returnEdge.getSuccessor().getNumLeavingEdges() == 0) {
        // Ugly, but I do not know how to do better
        // TODO: Handle leaks at any program exit point (abort, etc.)

        for (SMGState successor : successors) {
          if (options.isHandleNonFreedMemoryInMainAsMemLeak()) {
            successor.dropStackFrame();
          }
          successor.pruneUnreachable();
        }
      }

      plotWhenConfigured(successors, cfaEdge.getDescription(), SMGExportLevel.INTERESTING);
      break;

    default:
      successors = ImmutableList.of(smgState);
    }

    for (SMGState smg : successors) {
      SMGUtils.plotWhenConfigured(getDotExportFileName(smg), smg, cfaEdge.toString(), logger,
          SMGExportLevel.EVERY, exportSMGOptions);
      logger.log(Level.ALL, "state id ", smg.getId(), " -> state id ", state.getId());
    }

    return successors;
  }

  private void plotWhenConfigured(List<SMGState> pStates, String pLocation, SMGExportLevel pLevel) {
    for (SMGState state : pStates) {
      SMGUtils.plotWhenConfigured(getDotExportFileName(state), state, pLocation, logger, pLevel,
          exportSMGOptions);
    }
  }

  private String getDotExportFileName(SMGState pState) {
    if (pState.getPredecessorId() == 0) {
      return String.format("initial-%03d", pState.getId());
    } else {
      return String.format("%03d-%03d-%03d", pState.getPredecessorId(), pState.getId(),
          id_counter.getAndIncrement());
    }
  }

  private List<SMGState> handleExitFromFunction(SMGState smgState,
      CReturnStatementEdge returnEdge) throws CPATransferException {

    CExpression returnExp = returnEdge.getExpression().or(CIntegerLiteralExpression.ZERO); // 0 is the default in C

    logger.log(Level.ALL, "Handling return Statement: ", returnExp);

    if (smgPredicateManager.isErrorPathFeasible(smgState)) {
      smgState = smgState.setInvalidRead();
    }
    smgState.resetErrorRelation();

    CType expType = expressionEvaluator.getRealExpressionType(returnExp);
    SMGObject tmpFieldMemory = smgState.getFunctionReturnObject();
    com.google.common.base.Optional<CAssignment> returnAssignment = returnEdge.asAssignment();
    if (returnAssignment.isPresent()) {
      expType = returnAssignment.get().getLeftHandSide().getExpressionType();
    }

    if (tmpFieldMemory != null) {
      return handleAssignmentToField(smgState, returnEdge, tmpFieldMemory, 0, expType, returnExp);
    }

    return ImmutableList.of(smgState);
  }

  private List<SMGState> handleFunctionReturn(SMGState smgState,
      CFunctionReturnEdge functionReturnEdge) throws CPATransferException {

    logger.log(Level.ALL, "Handling function return");

    CFunctionSummaryEdge summaryEdge = functionReturnEdge.getSummaryEdge();
    CFunctionCall exprOnSummary = summaryEdge.getExpression();

    SMGState newState = new SMGState(smgState, blockOperator, functionReturnEdge.getSuccessor());

    if (smgPredicateManager.isErrorPathFeasible(newState)) {
      newState = newState.setInvalidRead();
    }

    newState.resetErrorRelation();

    assert newState.getStackFrame().getFunctionDeclaration().equals(functionReturnEdge.getFunctionEntry().getFunctionDefinition());

    if (exprOnSummary instanceof CFunctionCallAssignmentStatement) {

      // Assign the return value to the lValue of the functionCallAssignment

      CExpression lValue = ((CFunctionCallAssignmentStatement) exprOnSummary).getLeftHandSide();

      CType rValueType = expressionEvaluator.getRealExpressionType(((CFunctionCallAssignmentStatement) exprOnSummary).getRightHandSide());

      SMGSymbolicValue rValue = getFunctionReturnValue(newState, rValueType, functionReturnEdge);

      SMGAddress address = null;

      // Lvalue is one frame above
      newState.dropStackFrame();

      LValueAssignmentVisitor visitor = expressionEvaluator.getLValueAssignmentVisitor(functionReturnEdge, newState);

      List<SMGAddressAndState> addressAndValues = lValue.accept(visitor);

      List<SMGState> result = new ArrayList<>(addressAndValues.size());

      for (SMGAddressAndState addressAndValue : addressAndValues) {
        address = addressAndValue.getObject();
        newState = addressAndValue.getSmgState();

        if (!address.isUnknown()) {

          if (rValue.isUnknown()) {
            rValue = SMGKnownSymValue.valueOf(SMGValueFactory.getNewValue());
          }

          SMGObject object = address.getObject();

          int offset = address.getOffset().getAsInt();

          //TODO cast value
          rValueType = expressionEvaluator.getRealExpressionType(lValue);

          SMGState resultState = assignFieldToState(newState, functionReturnEdge, object, offset, rValue, rValueType);
          result.add(resultState);
        } else {
          //TODO missingInformation, exception
          result.add(newState);
        }
      }

      return result;
    } else {
      newState.dropStackFrame();
      return ImmutableList.of(newState);
    }
  }

  private SMGSymbolicValue getFunctionReturnValue(SMGState smgState, CType type, CFAEdge pCFAEdge) throws SMGInconsistentException, UnrecognizedCCodeException {

    SMGObject tmpMemory = smgState.getFunctionReturnObject();

    return expressionEvaluator.readValue(smgState, tmpMemory, SMGKnownExpValue.ZERO, type, pCFAEdge).getObject();
  }

  private List<SMGState> handleFunctionCall(SMGState pSmgState, CFunctionCallEdge callEdge)
      throws CPATransferException, SMGInconsistentException  {

    CFunctionEntryNode functionEntryNode = callEdge.getSuccessor();

    logger.log(Level.ALL, "Handling function call: ", functionEntryNode.getFunctionName());

    SMGState initialNewState = new SMGState(pSmgState, blockOperator, callEdge.getSuccessor());

    CFunctionDeclaration functionDeclaration = functionEntryNode.getFunctionDefinition();

    List<CParameterDeclaration> paramDecl = functionEntryNode.getFunctionParameters();
    List<? extends CExpression> arguments = callEdge.getArguments();

    if (!callEdge.getSuccessor().getFunctionDefinition().getType().takesVarArgs()) {
      //TODO Parameter with varArgs
      assert (paramDecl.size() == arguments.size());
    }

    Map<SMGState, List<Pair<SMGRegion,SMGSymbolicValue>>> valuesMap = new HashMap<>();

    //TODO Refactor, ugly

    List<SMGState> newStates = new ArrayList<>(4);

    newStates.add(initialNewState);

    List<Pair<SMGRegion, SMGSymbolicValue>> initialValuesList = new ArrayList<>(paramDecl.size());
    valuesMap.put(initialNewState, initialValuesList);

    // get value of actual parameter in caller function context
    for (int i = 0; i < paramDecl.size(); i++) {

      CExpression exp = arguments.get(i);

      String varName = paramDecl.get(i).getName();
      CType cParamType = expressionEvaluator.getRealExpressionType(paramDecl.get(i));


      SMGRegion paramObj;
      // If parameter is a array, convert to pointer
      if (cParamType instanceof CArrayType) {
        int size = machineModel.getBitSizeofPtr();
        paramObj = new SMGRegion(size, varName);
      } else {
        int size = expressionEvaluator.getBitSizeof(callEdge, cParamType, initialNewState);
        paramObj = new SMGRegion(size, varName);
      }

      List<SMGState> result = new ArrayList<>(4);

      for(SMGState newState : newStates) {
        // We want to write a possible new Address in the new State, but
        // explore the old state for the parameters
        SMGValueAndStateList stateValues = readValueToBeAssiged(newState, callEdge, exp);

        for(SMGValueAndState stateValue : stateValues.getValueAndStateList()) {
          SMGState newStateWithReadSymbolicValue = stateValue.getSmgState();
          SMGSymbolicValue value = stateValue.getObject();

          List<Pair<SMGState, SMGKnownSymValue>> newStatesWithExpVal = assignExplicitValueToSymbolicValue(newStateWithReadSymbolicValue, callEdge, value, exp);

          for (Pair<SMGState, SMGKnownSymValue> newStateWithExpVal : newStatesWithExpVal) {

            SMGState curState = newStateWithExpVal.getFirst();
            if (!valuesMap.containsKey(curState)) {
              List<Pair<SMGRegion, SMGSymbolicValue>> newValues = new ArrayList<>(paramDecl.size());
              newValues.addAll(valuesMap.get(newState));
              valuesMap.put(curState, newValues);
            }

            Pair<SMGRegion, SMGSymbolicValue> lhsValuePair = Pair.of(paramObj, value);
            valuesMap.get(curState).add(i, lhsValuePair);
            result.add(curState);

            //Check that previous values are not merged with new one
            if (newStateWithExpVal.getSecond() != null) {
              for (int j = i - 1; j >= 0; j--) {
                Pair<SMGRegion, SMGSymbolicValue> lhsCheckValuePair = valuesMap.get(curState).get(j);
                SMGSymbolicValue symbolicValue = lhsCheckValuePair.getSecond();
                if (newStateWithExpVal.getSecond().equals(symbolicValue)) {
                  //Previous value was merged, replace with new value
                  Pair<SMGRegion, SMGSymbolicValue> newLhsValuePair = Pair.of(lhsCheckValuePair.getFirst(), value);
                  valuesMap.get(curState).remove(j);
                  valuesMap.get(curState).add(j, newLhsValuePair);
                }
              }
            }
          }
        }
      }

      newStates = result;
    }

    for(SMGState newState : newStates) {
      newState.addStackFrame(functionDeclaration);

      // get value of actual parameter in caller function context
      for (int i = 0; i < paramDecl.size(); i++) {

        CExpression exp = arguments.get(i);

        String varName = paramDecl.get(i).getName();
        CType cParamType = expressionEvaluator.getRealExpressionType(paramDecl.get(i));
        CType rValueType = expressionEvaluator.getRealExpressionType(exp.getExpressionType());
        // if function declaration is in form 'int foo(char b[32])' then omit array length
        if (rValueType instanceof CArrayType) {
          rValueType = new CPointerType(rValueType.isConst(), rValueType.isVolatile(), ((CArrayType)rValueType).getType());
        }

        if (cParamType instanceof CArrayType) {
          cParamType = new CPointerType(cParamType.isConst(), cParamType.isVolatile(), ((CArrayType) cParamType).getType());
        }

        List<Pair<SMGRegion, SMGSymbolicValue>> values = valuesMap.get(newState);
        SMGRegion newObject = values.get(i).getFirst();
        SMGSymbolicValue symbolicValue = values.get(i).getSecond();

        int typeSize = expressionEvaluator.getBitSizeof(callEdge, cParamType, newState);

        newState.addLocalVariable(typeSize, varName, newObject);

        //TODO (  cast expression)

        //6.5.16.1 right operand is converted to type of assignment expression
        // 6.5.26 The type of an assignment expression is the type the left operand would have after lvalue conversion.
        rValueType = cParamType;

        // We want to write a possible new Address in the new State, but
        // explore the old state for the parameters
        newState = assignFieldToState(newState, callEdge, newObject, 0, symbolicValue, rValueType);
      }
    }

    return newStates;
  }

  private List<SMGState> handleAssumption(SMGState pSmgState, CExpression expression, CFAEdge cfaEdge,
      boolean truthValue, boolean createNewStateIfNecessary) throws CPATransferException {

    SMGState smgState = new SMGState(pSmgState, blockOperator, cfaEdge.getSuccessor());

    if (smgPredicateManager.isErrorPathFeasible(smgState)) {
      smgState = smgState.setInvalidRead();
    }
    smgState.resetErrorRelation();

    // FIXME Quickfix, simplify expressions for sv-comp, later assumption handling has to be refactored to be able to handle complex expressions
    expression = eliminateOuterEquals(expression);

    // get the value of the expression (either true[-1], false[0], or unknown[null])
    AssumeVisitor visitor = expressionEvaluator.getAssumeVisitor(cfaEdge, smgState);
    SMGValueAndStateList valueAndStates = expression.accept(visitor);

    List<SMGState> result = new ArrayList<>();

    for(SMGValueAndState valueAndState : valueAndStates.getValueAndStateList()) {

      SMGSymbolicValue value = valueAndState.getObject();
      smgState = valueAndState.getSmgState();

      if (!value.isUnknown()) {
        if ((truthValue && value.equals(SMGKnownSymValue.TRUE)) ||
            (!truthValue && value.equals(SMGKnownSymValue.FALSE))) {
          result.add(smgState);
        } else {
          // This signals that there are no new States reachable from this State i. e. the
          // Assumption does not hold.
        }
      } else {
        result.addAll(
            deriveFurtherInformationFromAssumption(smgState, visitor, cfaEdge, truthValue, expression,
                createNewStateIfNecessary));
      }
    }

    return result;
  }

  private List<SMGState> deriveFurtherInformationFromAssumption(SMGState pSmgState, AssumeVisitor visitor,
      CFAEdge cfaEdge, boolean truthValue, CExpression expression, boolean createNewStateIfNecessary) throws CPATransferException {

    SMGState smgState = pSmgState;

    boolean impliesEqOn = visitor.impliesEqOn(truthValue, smgState);
    boolean impliesNeqOn = visitor.impliesNeqOn(truthValue, smgState);

    SMGSymbolicValue val1ImpliesOn;
    SMGSymbolicValue val2ImpliesOn;

    if(impliesEqOn || impliesNeqOn ) {
      val1ImpliesOn = visitor.impliesVal1(smgState);
      val2ImpliesOn = visitor.impliesVal2(smgState);
    } else {
      val1ImpliesOn = SMGUnknownValue.getInstance();
      val2ImpliesOn = SMGUnknownValue.getInstance();
    }

    List<SMGExplicitValueAndState> explicitValueAndStates = expressionEvaluator.evaluateExplicitValue(smgState, cfaEdge, expression);

    List<SMGState> result = new ArrayList<>(explicitValueAndStates.size());

    for (SMGExplicitValueAndState explicitValueAndState : explicitValueAndStates) {

      SMGExplicitValue explicitValue = explicitValueAndState.getObject();
      smgState = explicitValueAndState.getSmgState();

      if (explicitValue.isUnknown()) {

        SMGState newState;

        if (createNewStateIfNecessary) {
          newState = new SMGState(smgState, blockOperator, cfaEdge.getSuccessor());
        } else {
          // Don't continuously create new states when strengthening.
          newState = smgState;
        }

        if (!val1ImpliesOn.isUnknown() && !val2ImpliesOn.isUnknown()) {
          if (impliesEqOn) {
            newState.identifyEqualValues((SMGKnownSymValue) val1ImpliesOn, (SMGKnownSymValue) val2ImpliesOn);
          } else if (impliesNeqOn) {
            newState.identifyNonEqualValues((SMGKnownSymValue) val1ImpliesOn, (SMGKnownSymValue) val2ImpliesOn);
          }
        }

        newState = expressionEvaluator.deriveFurtherInformation(newState, truthValue, cfaEdge, expression);
        PredRelation pathPredicateRelation = newState.getPathPredicateRelation();
        BooleanFormula predicateFormula = smgPredicateManager.getPredicateFormula(pathPredicateRelation);
        try {
          if (newState.hasMemoryErrors() || !smgPredicateManager.isUnsat(predicateFormula)) {
            result.add(newState);
          }
        } catch (SolverException pE) {
          result.add(newState);
          logger.log(Level.WARNING, "Solver Exception: ", pE, " on predicate ", predicateFormula);
        } catch (InterruptedException pE) {
          result.add(newState);
          logger.log(Level.WARNING, "Solver Interrupted Exception: ", pE, " on predicate ", predicateFormula);
        }
      } else if ((truthValue && explicitValue.equals(SMGKnownExpValue.ONE))
          || (!truthValue && explicitValue.equals(SMGKnownExpValue.ZERO))) {
        result.add(smgState);
      } else {
        // This signals that there are no new States reachable from this State i. e. the
        // Assumption does not hold.
      }
    }

    return ImmutableList.copyOf(result);
  }

  private CExpression eliminateOuterEquals(CExpression pExpression) {

    if (!(pExpression instanceof CBinaryExpression)) {
      return pExpression;
    }

    CBinaryExpression binExp = (CBinaryExpression) pExpression;

    CExpression op1 = binExp.getOperand1();
    CExpression op2 = binExp.getOperand2();
    BinaryOperator op = binExp.getOperator();

    if (!(op1 instanceof CBinaryExpression && op2 instanceof CIntegerLiteralExpression && op == BinaryOperator.EQUALS)) {
      return pExpression;
    }

    CBinaryExpression binExpOp1 = (CBinaryExpression) op1;
    CIntegerLiteralExpression IntOp2 = (CIntegerLiteralExpression) op2;

    if(IntOp2.getValue().longValue() != 0) {
      return pExpression;
    }

    switch (binExpOp1.getOperator()) {
    case EQUALS:
      return new CBinaryExpression(binExpOp1.getFileLocation(), binExpOp1.getExpressionType(),
          binExpOp1.getCalculationType(), binExpOp1.getOperand1(), binExpOp1.getOperand2(), BinaryOperator.NOT_EQUALS);
    case NOT_EQUALS:
      return new CBinaryExpression(binExpOp1.getFileLocation(), binExpOp1.getExpressionType(),
          binExpOp1.getCalculationType(), binExpOp1.getOperand1(), binExpOp1.getOperand2(), BinaryOperator.EQUALS);
    default:
      return pExpression;
    }


  }

  private List<SMGState> handleStatement(SMGState pState, CStatementEdge pCfaEdge) throws CPATransferException {
    logger.log(Level.ALL, ">>> Handling statement");
    List<SMGState> newStates = null;

    CStatement cStmt = pCfaEdge.getStatement();

    if (cStmt instanceof CAssignment) {
      CAssignment cAssignment = (CAssignment) cStmt;
      CExpression lValue = cAssignment.getLeftHandSide();
      CRightHandSide rValue = cAssignment.getRightHandSide();

      newStates = handleAssignment(pState, pCfaEdge, lValue, rValue);
    } else if (cStmt instanceof CFunctionCallStatement) {

      CFunctionCallStatement cFCall = (CFunctionCallStatement) cStmt;
      CFunctionCallExpression cFCExpression = cFCall.getFunctionCallExpression();
      CExpression fileNameExpression = cFCExpression.getFunctionNameExpression();
      String functionName = fileNameExpression.toASTString();

      if (builtins.isABuiltIn(functionName)) {
        SMGState newState = new SMGState(pState, blockOperator, pCfaEdge.getSuccessor());
        if (builtins.isConfigurableAllocationFunction(functionName)) {
          logger.log(Level.INFO, pCfaEdge.getFileLocation(), ":",
              "Calling ", functionName, " and not using the result, resulting in memory leak.");
          newStates = builtins.evaluateConfigurableAllocationFunction(cFCExpression, newState, pCfaEdge).asSMGStateList();

          for (SMGState state : newStates) {
            state.setMemLeak();
          }
        }

        if (builtins.isDeallocationFunction(functionName)) {
          newStates = builtins.evaluateFree(cFCExpression, newState, pCfaEdge);
        }

        if (builtins.isExternalAllocationFunction(functionName)) {
          newStates = builtins.evaluateExternalAllocation(cFCExpression, newState).asSMGStateList();
        }

        switch (functionName) {
        case "__VERIFIER_BUILTIN_PLOT":
          builtins.evaluateVBPlot(cFCExpression, newState);
          break;
        case "__builtin_alloca":
          logger.log(Level.INFO, pCfaEdge.getFileLocation(), ":",
              "Calling alloc and not using the result.");
          newStates = builtins.evaluateAlloca(cFCExpression, newState, pCfaEdge).asSMGStateList();
          break;
        case "memset":
          SMGAddressValueAndStateList result = builtins.evaluateMemset(cFCExpression, newState, pCfaEdge);
          newStates = result.asSMGStateList();
          break;
        case "memcpy":
          result = builtins.evaluateMemcpy(cFCExpression, newState, pCfaEdge);
          newStates = result.asSMGStateList();
          break;
        case "printf":
          return ImmutableList.of(new SMGState(pState, blockOperator, pCfaEdge.getSuccessor()));
        default:
          // nothing to do here
        }

      } else {
        switch (options.getHandleUnknownFunctions()) {
        case STRICT:
          throw new CPATransferException("Unknown function '" + functionName + "' may be unsafe. See the cpa.smg.handleUnknownFunction option.");
        case ASSUME_SAFE:
          return ImmutableList.of(pState);
        default:
          throw new AssertionError("Unhandled enum value in switch: " + options.getHandleUnknownFunctions());
        }
      }
    } else {
      newStates = ImmutableList.of(pState);
    }

    return newStates;
  }

  private List<SMGState> handleAssignment(SMGState pState, CFAEdge cfaEdge, CExpression lValue,
      CRightHandSide rValue) throws CPATransferException {

    SMGState state = pState;
    logger.log(Level.ALL, "Handling assignment:", lValue, "=", rValue);

    List<SMGState> result = new ArrayList<>(4);

    LValueAssignmentVisitor visitor = expressionEvaluator.getLValueAssignmentVisitor(cfaEdge, state);

    List<SMGAddressAndState> addressOfFieldAndStates = lValue.accept(visitor);

    for (SMGAddressAndState addressOfFieldAndState : addressOfFieldAndStates) {
      SMGAddress addressOfField = addressOfFieldAndState.getObject();
      state = addressOfFieldAndState.getSmgState();

      CType fieldType = expressionEvaluator.getRealExpressionType(lValue);

      if (addressOfField.isUnknown()) {
        SMGState resultState = new SMGState(state, blockOperator, cfaEdge.getSuccessor());
        /*Check for dereference errors in rValue*/
        List<SMGState> newStates =
            readValueToBeAssiged(resultState, cfaEdge, rValue).asSMGStateList();
        newStates.forEach((SMGState smgState) -> {
          smgState.unknownWrite();
        });

        result.addAll(newStates);
      } else {
        List<SMGState> newStates =
            handleAssignmentToField(state, cfaEdge, addressOfField.getObject(),
                addressOfField.getOffset().getAsInt(), fieldType, rValue);
        result.addAll(newStates);
      }
    }

    return result;
  }

  /*
   * Creates value to be assigned to given field, by either reading it from the state,
   * or creating it, if an unknown value is returned, and marking it in missing Information.
   * Note that this read may modify the state.
   *
   */
  private SMGValueAndStateList readValueToBeAssiged(SMGState pNewState, CFAEdge cfaEdge, CRightHandSide rValue) throws CPATransferException {

    SMGValueAndStateList valueAndStates = expressionEvaluator.evaluateExpressionValue(pNewState, cfaEdge, rValue);

    List<SMGValueAndState> resultValueAndStates = new ArrayList<>(valueAndStates.size());

    for (SMGValueAndState valueAndState : valueAndStates.getValueAndStateList()) {
      SMGSymbolicValue value = valueAndState.getObject();

      if (value.isUnknown()) {

        value = SMGKnownSymValue.valueOf(SMGValueFactory.getNewValue());
        valueAndState = SMGValueAndState.of(valueAndState.getSmgState(), value);
      }
      resultValueAndStates.add(valueAndState);
    }
    return SMGValueAndStateList.copyOf(resultValueAndStates);
  }

  // assign value of given expression to State at given location
  private List<SMGState> assignFieldToState(SMGState pNewState, CFAEdge cfaEdge,
      SMGObject memoryOfField, int fieldOffset, CType pLFieldType, CRightHandSide rValue)
          throws CPATransferException {

    List<SMGState> result = new ArrayList<>(4);

    CType rValueType = expressionEvaluator.getRealExpressionType(rValue);

    SMGValueAndStateList valueAndStates = readValueToBeAssiged(pNewState, cfaEdge, rValue);

    for (SMGValueAndState valueAndState : valueAndStates.getValueAndStateList()) {
      SMGSymbolicValue value = valueAndState.getObject();
      SMGState newState = valueAndState.getSmgState();


      //TODO (  cast expression)

      //6.5.16.1 right operand is converted to type of assignment expression
      // 6.5.26 The type of an assignment expression is the type the left operand would have after lvalue conversion.
      rValueType = pLFieldType;

      List<Pair<SMGState, SMGKnownSymValue>> newStatesWithMergedValues =
          assignExplicitValueToSymbolicValue(newState, cfaEdge, value, rValue);

      for (Pair<SMGState, SMGKnownSymValue> currentNewStateWithMergedValue : newStatesWithMergedValues) {
        SMGState currentNewState = currentNewStateWithMergedValue.getFirst();
        newState = assignFieldToState(currentNewState, cfaEdge, memoryOfField, fieldOffset, value, rValueType);
        result.add(newState);
      }
    }

    return result;
  }

  // Assign symbolic value to the explicit value calculated from pRvalue
  private List<Pair<SMGState, SMGKnownSymValue>> assignExplicitValueToSymbolicValue(SMGState pNewState,
      CFAEdge pCfaEdge, SMGSymbolicValue value, CRightHandSide pRValue)
          throws CPATransferException {

    SMGExpressionEvaluator expEvaluator = new SMGExpressionEvaluator(logger,
        machineModel);

    List<SMGExplicitValueAndState> expValueAndStates = expEvaluator.evaluateExplicitValue(pNewState, pCfaEdge, pRValue);
    List<Pair<SMGState, SMGKnownSymValue>> result = new ArrayList<>(expValueAndStates.size());

    for (SMGExplicitValueAndState expValueAndState : expValueAndStates) {
      SMGExplicitValue expValue = expValueAndState.getObject();
      SMGState newState = expValueAndState.getSmgState();

      if (!expValue.isUnknown()) {
        SMGKnownSymValue mergedSymValue = newState.putExplicit((SMGKnownSymValue) value, (SMGKnownExpValue) expValue);
        result.add(Pair.of(newState, mergedSymValue));
      } else {
        result.add(Pair.of(newState, null));
      }
    }

    return result;
  }

  private SMGState assignFieldToState(SMGState newState, CFAEdge cfaEdge,
      SMGObject memoryOfField, int fieldOffset, SMGSymbolicValue value, CType rValueType)
      throws UnrecognizedCCodeException, SMGInconsistentException {

    int sizeOfField = expressionEvaluator.getBitSizeof(cfaEdge, rValueType, newState);

    //FIXME Does not work with variable array length.
    if (memoryOfField.getSize() < sizeOfField) {

      logger.log(Level.INFO, () -> {
        String log =
            String.format("%s: Attempting to write %d bytes into a field with size %d bytes: %s",
                cfaEdge.getFileLocation(), sizeOfField, memoryOfField.getSize(),
                cfaEdge.getRawStatement());
        return log;
      });
    }

    if (expressionEvaluator.isStructOrUnionType(rValueType)) {
      return assignStruct(newState, memoryOfField, fieldOffset, rValueType, value, cfaEdge);
    } else {
      return writeValue(newState, memoryOfField, fieldOffset, rValueType, value, cfaEdge);
    }
  }

  private SMGState assignStruct(SMGState pNewState, SMGObject pMemoryOfField,
      int pFieldOffset, CType pRValueType, SMGSymbolicValue pValue,
      CFAEdge pCfaEdge) throws SMGInconsistentException,
      UnrecognizedCCodeException {

    if (pValue instanceof SMGKnownAddVal) {
      SMGKnownAddVal structAddress = (SMGKnownAddVal) pValue;

      SMGObject source = structAddress.getObject();
      int structOffset = structAddress.getOffset().getAsInt();

      //FIXME Does not work with variable array length.
      int structSize = structOffset + expressionEvaluator.getBitSizeof(pCfaEdge, pRValueType, pNewState);
      return pNewState.copy(source, pMemoryOfField,
          structOffset, structSize, pFieldOffset);
    }

    return pNewState;
  }

  SMGState writeValue(SMGState pNewState, SMGObject pMemoryOfField, int pFieldOffset, int pSizeType,
      SMGSymbolicValue pValue, CFAEdge pEdge) throws UnrecognizedCCodeException, SMGInconsistentException {
    return writeValue(pNewState, pMemoryOfField, pFieldOffset, AnonymousTypes.createTypeWithLength(pSizeType), pValue, pEdge);
  }

  public SMGState writeValue(SMGState pNewState, SMGObject pMemoryOfField, int pFieldOffset, CType pRValueType,
      SMGSymbolicValue pValue, CFAEdge pEdge) throws SMGInconsistentException, UnrecognizedCCodeException {

    //FIXME Does not work with variable array length.
    //TODO: write value with bit precise size
    boolean doesNotFitIntoObject = pFieldOffset < 0
        || pFieldOffset + expressionEvaluator.getBitSizeof(pEdge, pRValueType, pNewState) >
        pMemoryOfField.getSize();

    if (doesNotFitIntoObject) {
      // Field does not fit size of declared Memory
      logger.log(Level.INFO, () -> {
        String msg =
            String.format("%s: Field (%d, %s) does not fit object %s.", pEdge.getFileLocation(),
                pFieldOffset, pRValueType.toASTString(""), pMemoryOfField.toString());
        return msg;
      });

      return pNewState.setInvalidWrite();
    }

    if (pValue.isUnknown()) {
      return pNewState;
    }

    return pNewState.writeValue(pMemoryOfField, pFieldOffset, pRValueType, pValue).getState();
  }

  private List<SMGState> handleAssignmentToField(SMGState state, CFAEdge cfaEdge,
      SMGObject memoryOfField, int fieldOffset, CType pLFieldType, CRightHandSide rValue)
      throws CPATransferException {

    SMGState newState = new SMGState(state, blockOperator, cfaEdge.getSuccessor());

    List<SMGState> newStates = assignFieldToState(newState, cfaEdge, memoryOfField, fieldOffset, pLFieldType, rValue);

    // If Assignment contained malloc, handle possible fail with
    // alternate State (don't create state if not enabled)
    if (possibleMallocFail && options.isEnableMallocFailure()) {
      possibleMallocFail = false;
      SMGState otherState = new SMGState(state, blockOperator, cfaEdge.getSuccessor());
      CType rValueType = expressionEvaluator.getRealExpressionType(rValue);
      SMGState mallocFailState =
          writeValue(otherState, memoryOfField, fieldOffset, rValueType, SMGKnownSymValue.ZERO, cfaEdge);
      newStates.add(mallocFailState);
    }

    return newStates;
  }

  private List<SMGState> handleVariableDeclaration(SMGState pState, CVariableDeclaration pVarDecl, CDeclarationEdge pEdge) throws CPATransferException {
    logger.log(Level.ALL, "Handling variable declaration:", pVarDecl);

    String varName = pVarDecl.getName();
    CType cType = expressionEvaluator.getRealExpressionType(pVarDecl);

    SMGObject newObject;

    newObject = pState.getObjectForVisibleVariable(varName);
      /*
     *  The variable is not null if we seen the declaration already, for example in loops. Invalid
     *  occurrences (variable really declared twice) should be caught for us by the parser. If we
     *  already processed the declaration, we do nothing.
     */
    if (newObject == null) {
      int typeSize = expressionEvaluator.getBitSizeof(pEdge, cType, pState);

      if (pVarDecl.isGlobal()) {
        newObject = pState.addGlobalVariable(typeSize, varName);
      } else {
        newObject = pState.addLocalVariable(typeSize, varName);
      }
    }

    List<SMGState> newStates = handleInitializerForDeclaration(pState, newObject, pVarDecl, pEdge);
    return newStates;
  }

  private List<SMGState> handleDeclaration(SMGState smgState, CDeclarationEdge edge) throws CPATransferException {
    logger.log(Level.ALL, ">>> Handling declaration");

    CDeclaration cDecl = edge.getDeclaration();

    if (!(cDecl instanceof CVariableDeclaration)) {
      return ImmutableList.of(smgState);
    }

    SMGState newState = new SMGState(smgState, blockOperator, edge.getSuccessor());

    List<SMGState> newStates = handleVariableDeclaration(newState, (CVariableDeclaration)cDecl, edge);

    return newStates;
  }

  private List<SMGState> handleInitializerForDeclaration(SMGState pState, SMGObject pObject, CVariableDeclaration pVarDecl, CDeclarationEdge pEdge) throws CPATransferException {
    CInitializer newInitializer = pVarDecl.getInitializer();
    CType cType = expressionEvaluator.getRealExpressionType(pVarDecl);

    if (newInitializer != null) {
      logger.log(Level.ALL, "Handling variable declaration: handling initializer");

      return handleInitializer(pState, pVarDecl, pEdge, pObject, 0, cType, newInitializer);
    } else if (pVarDecl.isGlobal()) {

      // Global variables without initializer are nullified in C
      pState = writeValue(pState, pObject, 0, cType, SMGKnownSymValue.ZERO, pEdge);
    }

    return ImmutableList.of(pState);
  }

  private List<SMGState> handleInitializer(SMGState pNewState, CVariableDeclaration pVarDecl, CFAEdge pEdge,
      SMGObject pNewObject, int pOffset, CType pLValueType, CInitializer pInitializer)
      throws UnrecognizedCCodeException, CPATransferException {

    if (pInitializer instanceof CInitializerExpression) {
       return assignFieldToState(pNewState, pEdge, pNewObject,
          pOffset, pLValueType,
          ((CInitializerExpression) pInitializer).getExpression());

    } else if (pInitializer instanceof CInitializerList) {

      return handleInitializerList(pNewState, pVarDecl, pEdge,
          pNewObject, pOffset, pLValueType, ((CInitializerList) pInitializer));
    } else if (pInitializer instanceof CDesignatedInitializer) {
      throw new AssertionError("Error in handling initializer, designated Initializer " + pInitializer.toASTString()
          + " should not appear at this point.");

    } else {
      throw new UnrecognizedCCodeException("Did not recognize Initializer", pInitializer);
    }
  }

  private List<SMGState> handleInitializerList(SMGState pNewState, CVariableDeclaration pVarDecl, CFAEdge pEdge,
      SMGObject pNewObject, int pOffset, CType pLValueType, CInitializerList pNewInitializer)
      throws UnrecognizedCCodeException, CPATransferException {

    CType realCType = pLValueType.getCanonicalType();

    if (realCType instanceof CArrayType) {

      CArrayType arrayType = (CArrayType) realCType;
      return handleInitializerList(pNewState, pVarDecl, pEdge,
          pNewObject, pOffset, arrayType, pNewInitializer);
    } else if (realCType instanceof CCompositeType) {

      CCompositeType structType = (CCompositeType) realCType;
      return handleInitializerList(pNewState, pVarDecl, pEdge,
          pNewObject, pOffset, structType, pNewInitializer);
    }

    // Type cannot be resolved
    logger.log(Level.INFO,() -> {
          String msg =
              String.format("Type %s cannot be resolved sufficiently to handle initializer %s",
                  realCType.toASTString(""), pNewInitializer);
          return msg;
        });

    return ImmutableList.of(pNewState);
  }

  private Pair<Integer, Integer> calculateOffsetAndPositionOfFieldFromDesignator(
      int offsetAtStartOfStruct,
      List<CCompositeTypeMemberDeclaration> pMemberTypes,
      CDesignatedInitializer pInitializer,
      CCompositeType pLValueType) throws UnrecognizedCCodeException {

    // TODO More Designators?
    assert pInitializer.getDesignators().size() == 1;

    String fieldDesignator = ((CFieldDesignator) pInitializer.getDesignators().get(0)).getFieldName();

    int offset = offsetAtStartOfStruct;
    int sizeOfByte = machineModel.getSizeofCharInBits();
    for (int listCounter = 0; listCounter < pMemberTypes.size(); listCounter++) {

      CCompositeTypeMemberDeclaration memberDcl = pMemberTypes.get(listCounter);

      if (memberDcl.getName().equals(fieldDesignator)) {
        return Pair.of(offset, listCounter);
      } else {
        if (pLValueType.getKind() == ComplexTypeKind.STRUCT) {
          int memberSize = machineModel.getBitSizeof(memberDcl.getType());
          if (!(memberDcl.getType() instanceof CBitFieldType)) {
            offset += memberSize;
            int overByte = offset % machineModel.getSizeofCharInBits();
            if (overByte > 0) {
              offset += machineModel.getSizeofCharInBits() - overByte;
            }
            offset +=
                machineModel.getPadding(offset / sizeOfByte, memberDcl.getType()) * sizeOfByte;
          } else {
            // Cf. implementation of {@link MachineModel#getFieldOffsetOrSizeOrFieldOffsetsMappedInBits(...)}
            CType innerType = ((CBitFieldType) memberDcl.getType()).getType();

            if (memberSize == 0) {
              offset = machineModel.calculatePaddedBitsize(0, offset, innerType, sizeOfByte);
            } else {
              offset =
                  machineModel.calculateNecessaryBitfieldOffset(
                      offset, innerType, sizeOfByte, memberSize);
              offset += memberSize;
            }
          }
        }
      }
    }
    throw new UnrecognizedCCodeException("CDesignator field name not in struct.", pInitializer);
  }

  private List<SMGState> handleInitializerList(
      SMGState pNewState, CVariableDeclaration pVarDecl, CFAEdge pEdge,
      SMGObject pNewObject, int pOffset, CCompositeType pLValueType,
      CInitializerList pNewInitializer)
      throws UnrecognizedCCodeException, CPATransferException {

    int listCounter = 0;

    List<CCompositeType.CCompositeTypeMemberDeclaration> memberTypes = pLValueType.getMembers();

    Pair<SMGState, Integer> startOffsetAndState = Pair.of(pNewState, pOffset);

    List<Pair<SMGState, Integer>> offsetAndStates = new ArrayList<>();
    offsetAndStates.add(startOffsetAndState);

    // Move preinitialization of global variable because of unpredictable fields' order within CDesignatedInitializer
    if (pVarDecl.isGlobal()) {

      List<Pair<SMGState, Integer>> result = new ArrayList<>(offsetAndStates.size());

      for (Pair<SMGState, Integer> offsetAndState : offsetAndStates) {

        int offset = offsetAndState.getSecond();
        SMGState newState = offsetAndState.getFirst();

        int sizeOfType = expressionEvaluator.getBitSizeof(pEdge, pLValueType, pNewState);

        if (offset - pOffset < sizeOfType) {
          newState = writeValue(newState, pNewObject, offset,
              AnonymousTypes.createTypeWithLength(sizeOfType - (offset - pOffset)), SMGKnownSymValue.ZERO, pEdge);
        }

        result.add(Pair.of(newState, offset));
      }

      offsetAndStates = result;
    }


    for (CInitializer initializer : pNewInitializer.getInitializers()) {

      if (initializer instanceof CDesignatedInitializer) {
        Pair<Integer, Integer> offsetAndPosition =
            calculateOffsetAndPositionOfFieldFromDesignator(pOffset, memberTypes,
                (CDesignatedInitializer) initializer, pLValueType);
        int offset = offsetAndPosition.getFirst();
        listCounter = offsetAndPosition.getSecond();
        initializer = ((CDesignatedInitializer) initializer).getRightHandSide();

        List<Pair<SMGState, Integer>> resultOffsetAndStatesDesignated = new ArrayList<>();
        resultOffsetAndStatesDesignated.add(Pair.of(pNewState, offset));

        offsetAndStates = resultOffsetAndStatesDesignated;

      }

      if (listCounter >= memberTypes.size()) {
        throw new UnrecognizedCCodeException(
          "More Initializer in initializer list "
              + pNewInitializer.toASTString()
              + " than fit in type "
              + pLValueType.toASTString(""),
          pEdge); }

      CType memberType = memberTypes.get(listCounter).getType();

      List<Pair<SMGState, Integer>> resultOffsetAndStates = new ArrayList<>();

      for (Pair<SMGState, Integer> offsetAndState : offsetAndStates) {

        int offset = offsetAndState.getSecond();
        if (!(memberType instanceof CBitFieldType)) {
          int overByte = offset % machineModel.getSizeofCharInBits();
          if (overByte > 0) {
            offset += machineModel.getSizeofCharInBits() - overByte;
          }
          offset += machineModel.getPadding(offset / machineModel.getSizeofCharInBits(), memberType) * machineModel.getSizeofCharInBits();
        }
        SMGState newState = offsetAndState.getFirst();

        List<SMGState> pNewStates =
            handleInitializer(newState, pVarDecl, pEdge, pNewObject, offset, memberType, initializer);

        offset = offset + machineModel.getBitSizeof(memberType);

        List<? extends Pair<SMGState, Integer>> newStatesAndOffset =
            FluentIterable.from(pNewStates).transform(new ListToListOfPairFunction<SMGState, Integer>(offset))
                .toList();

        resultOffsetAndStates.addAll(newStatesAndOffset);
      }

      offsetAndStates = resultOffsetAndStates;
      listCounter++;
    }

    return FluentIterable.from(offsetAndStates).transform(new Function<Pair<SMGState, Integer>, SMGState>() {

      @Override
      public SMGState apply(Pair<SMGState, Integer> pInput) {
        return pInput.getFirst();
      }
    }).toList();
  }

  private static class ListToListOfPairFunction<F, T> implements Function<F, Pair<F, T>> {

    private final T constant;

    public ListToListOfPairFunction(T pConstant) {
      constant = pConstant;
    }

    @Override
    public Pair<F, T> apply(F listElements) {
      return Pair.of(listElements, constant);
    }
  }

  private List<SMGState> handleInitializerList(
      SMGState pNewState, CVariableDeclaration pVarDecl, CFAEdge pEdge,
      SMGObject pNewObject, int pOffset, CArrayType pLValueType,
      CInitializerList pNewInitializer)
      throws UnrecognizedCCodeException, CPATransferException {

    int listCounter = 0;

    CType elementType = pLValueType.getType();

    int sizeOfElementType = expressionEvaluator.getBitSizeof(pEdge, elementType, pNewState);

    List<SMGState> newStates = new ArrayList<>(4);
    newStates.add(pNewState);

    for (CInitializer initializer : pNewInitializer.getInitializers()) {

      int offset = pOffset + listCounter * sizeOfElementType;

      List<SMGState> result = new ArrayList<>();

      for (SMGState newState : newStates) {
        result.addAll(handleInitializer(newState, pVarDecl, pEdge,
            pNewObject, offset, pLValueType.getType(), initializer));
      }

      newStates = result;
      listCounter++;
    }

    if (pVarDecl.isGlobal()) {

      List<SMGState> result = new ArrayList<>(newStates.size());

      for (SMGState newState : newStates) {
        if (!options.isGCCZeroLengthArray() || pLValueType.getLength() != null) {
          int sizeOfType = expressionEvaluator.getBitSizeof(pEdge, pLValueType, pNewState);

          int offset = pOffset + listCounter * sizeOfElementType;
          if (offset - pOffset < sizeOfType) {
            newState = writeValue(newState, pNewObject, offset,
                AnonymousTypes.createTypeWithLength(sizeOfType - (offset - pOffset)),
                SMGKnownSymValue.ZERO, pEdge);
          }
        }
        result.add(newState);
      }
      newStates = result;
    }

    return ImmutableList.copyOf(newStates);
  }

  @Override
  public Collection<? extends AbstractState> strengthen(AbstractState element, List<AbstractState> elements,
      CFAEdge cfaEdge, Precision pPrecision) throws CPATransferException, InterruptedException {

    ArrayList<SMGState> toStrengthen = new ArrayList<>();
    ArrayList<SMGState> result = new ArrayList<>();
    toStrengthen.add((SMGState) element);
    result.add((SMGState) element);

    for (AbstractState ae : elements) {
      if (ae instanceof AutomatonState) {
        // New result
        result.clear();
        for (SMGState state : toStrengthen) {
          Collection<SMGState> ret = strengthen((AutomatonState) ae, state, cfaEdge);
          if (ret == null) {
            result.add(state);
          } else {
            result.addAll(ret);
          }
        }
        toStrengthen.clear();
        toStrengthen.addAll(result);
      }
    }

    possibleMallocFail = false;
    return result;
  }

  private Collection<SMGState> strengthen(AutomatonState pAutomatonState, SMGState pElement,
      CFAEdge pCfaEdge) throws CPATransferException {

    FluentIterable<CExpression> assumptions =
        from(pAutomatonState.getAssumptions()).filter(CExpression.class);

    if(assumptions.isEmpty()) {
      return Collections.singleton(pElement);
    }

    StringBuilder assumeDesc = new StringBuilder();

    SMGState newElement = pElement;

    for (CExpression assume : assumptions) {
      assumeDesc.append(assume.toASTString());

      // only create new SMGState if necessary
      List<SMGState> newElements =
          handleAssumption(newElement, assume, pCfaEdge, true, pElement == newElement);

      assert newElements.size() < 2;

      if (newElements.isEmpty()) {
        newElement = null;
        break;
      } else {
        newElement = newElements.get(0);
      }
    }

    if (newElement == null) {
      return Collections.emptyList();
    } else {
      SMGUtils.plotWhenConfigured(getDotExportFileName(newElement), newElement, assumeDesc.toString(), logger, SMGExportLevel.EVERY, exportSMGOptions);
      return Collections.singleton(newElement);
    }
  }

  public void changeKindToRefinment() {
    kind = SMGTransferRelationKind.REFINEMENT;
  }
}
