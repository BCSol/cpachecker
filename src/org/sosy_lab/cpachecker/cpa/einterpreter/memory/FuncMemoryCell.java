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
package org.sosy_lab.cpachecker.cpa.einterpreter.memory;

import java.util.HashMap;

import org.sosy_lab.cpachecker.cfa.objectmodel.CFAFunctionDefinitionNode;
import org.sosy_lab.cpachecker.cpa.einterpreter.InterpreterElement;

public class FuncMemoryCell implements MemoryCell {

 private HashMap<InterpreterElement,CFAFunctionDefinitionNode> func;



  public FuncMemoryCell(CFAFunctionDefinitionNode paddr,InterpreterElement pel){
    func= new HashMap<InterpreterElement, CFAFunctionDefinitionNode>();
    func.put(pel, paddr);
  }

  @SuppressWarnings("unchecked")
  private FuncMemoryCell(HashMap<InterpreterElement, CFAFunctionDefinitionNode> paddr){
    func = (HashMap<InterpreterElement, CFAFunctionDefinitionNode>) paddr.clone();
  }

  void setFunctionPoint(CFAFunctionDefinitionNode paddr, InterpreterElement pel){
    func.put(pel,paddr);
  }
  CFAFunctionDefinitionNode getFunctionPoint(InterpreterElement pel){
    while(func.containsKey(pel)== false && pel.getprev()!=null){
      pel =pel.getprev();
    }
    return func.get(pel);
  }


  @Override
  public CellType getType() {
    return CellType.FMC;
  }

  /*@Override
  public FuncMemoryCell clone(){
    if(clone != null){
      return clone;
    }else{
      clone = new FuncMemoryCell(func);
      return clone;
    }
  }*/
  @Override
  public FuncMemoryCell copy(){

   return new FuncMemoryCell(func);

  }

}
