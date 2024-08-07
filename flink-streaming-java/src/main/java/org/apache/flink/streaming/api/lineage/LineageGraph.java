/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.flink.streaming.api.lineage;

import org.apache.flink.annotation.PublicEvolving;

import java.util.List;

/**
 * Job lineage graph that users can get sources, sinks and relationships from lineage and manage the
 * relationship between jobs and tables.
 */
@PublicEvolving
public interface LineageGraph {
    /* Source lineage vertex list. */
    List<SourceLineageVertex> sources();

    /* Sink lineage vertex list. */
    List<LineageVertex> sinks();

    /* lineage edges from sources to sinks. */
    List<LineageEdge> relations();
}
