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
 */
package eu.fasten.maven;

import java.io.Serializable;

import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.MergedDirectedGraph;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * Add real support for external nodes to {@link MergedDirectedGraph}.
 * 
 * @version $Id$
 */
public class MavenMergedDirectedGraph extends MergedDirectedGraph implements DirectedGraph, Serializable
{
    private final LongSet externalNodes = new LongOpenHashSet();

    @Override
    public LongSet externalNodes()
    {
        return this.externalNodes;
    }

    public boolean addExternalNode(long node)
    {
        boolean result = addVertex(node);
        if (result) {
            this.externalNodes.add(node);
        }

        return result;
    }

    @Override
    public boolean removeVertex(long node)
    {
        boolean result = super.removeVertex(node);
        if (result) {
            this.externalNodes.remove(node);
        }

        return result;
    }
}
