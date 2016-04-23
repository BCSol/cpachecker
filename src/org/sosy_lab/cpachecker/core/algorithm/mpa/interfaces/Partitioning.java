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
package org.sosy_lab.cpachecker.core.algorithm.mpa.interfaces;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.sosy_lab.cpachecker.core.algorithm.mpa.budgeting.PartitionBudgeting;
import org.sosy_lab.cpachecker.core.algorithm.mpa.budgeting.PropertyBudgeting;
import org.sosy_lab.cpachecker.core.interfaces.Property;

import java.util.Set;

public interface Partitioning extends Iterable<ImmutableSet<Property>> {

  public static enum PartitioningStatus {
    NONE,
    BREAK,
    ALL_IN_ONE,
    ALL_IN_ONE_SHORT,
    ONE_FOR_EACH,
    K_FOR_EACH,
    IRRELEVANT,
    CHEAPEST_BISECT,
    NOT_EXHAUSTED_ONLY,
    MORE_PARTITIONS
  }

  public ImmutableList<ImmutableSet<Property>> getPartitions();

  public PartitioningStatus getStatus();

  public boolean isEmpty();

  public int partitionCount();

  public Partitioning substract(Set<Property> pProperties);

  public Partitioning withoutFirst();

  public ImmutableSet<Property> getFirstPartition();

  public PropertyBudgeting getPropertyBudgeting();

  public PartitionBudgeting getPartitionBudgeting();

}
