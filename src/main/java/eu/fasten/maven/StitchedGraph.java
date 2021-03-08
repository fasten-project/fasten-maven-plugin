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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.iterators.IteratorChain;
import org.apache.commons.collections4.iterators.IteratorIterable;
import org.jgrapht.traverse.DepthFirstIterator;

import eu.fasten.core.data.ArrayImmutableDirectedGraph;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.JavaNode;
import eu.fasten.core.data.JavaScope;
import eu.fasten.core.data.JavaType;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * Create and navigate a stitched call graph.
 * 
 * @version $Id$
 */
public class StitchedGraph
{
    private final MavenResolvedCallGraph projectRCG;

    private final List<MavenResolvedCallGraph> dependenciesRCGs;

    private final DirectedGraph fullGraph;

    private final DirectedGraph stitchedGraph;

    private Map<Long, StitchedGraphNode> graphIdToNode = new HashMap<>();

    private Map<Long, Long> globalIdToGraphId = new HashMap<>();

    private Map<FastenURI, Set<Long>> localURIToGlobalId = new HashMap<>();

    private Map<String, Map<FastenURI, Long>> resolvedURIToGraphId = new HashMap<>();

    public StitchedGraph(MavenResolvedCallGraph projectRCG, List<MavenResolvedCallGraph> dependencyRCGs)
    {
        this.projectRCG = projectRCG;
        this.dependenciesRCGs = new ArrayList<>(dependencyRCGs);

        // Build full graph

        ArrayImmutableDirectedGraph.Builder fullBuilder = new ArrayImmutableDirectedGraph.Builder();

        // Add internal calls

        long offset = append(fullBuilder, projectRCG, -1);

        for (MavenResolvedCallGraph dependencyRCG : this.dependenciesRCGs) {
            offset = append(fullBuilder, dependencyRCG, offset);
        }

        this.fullGraph = fullBuilder.build();

        // Build stitched graph

        ArrayImmutableDirectedGraph.Builder stitchedBuilder = new ArrayImmutableDirectedGraph.Builder();

        Set<Long> handledNodes = new HashSet<>();

        LongSet externalCalls = this.fullGraph.externalNodes();

        IteratorChain<Long> projectCalls = new IteratorChain<>(
            projectRCG.getGraph().getGraph().getInternalCalls().keySet().stream().map(l -> l.get(0).longValue())
                .iterator(),
            projectRCG.getGraph().getGraph().getExternalCalls().keySet().stream()
                .map(l -> this.globalIdToGraphId.get(l.get(0).longValue())).iterator());

        appendNodeAndSuccessors(new IteratorIterable<>(projectCalls), stitchedBuilder, externalCalls, handledNodes);

        this.stitchedGraph = stitchedBuilder.build();
    }

    private void appendNodeAndSuccessors(Iterable<Long> startVertices,
        ArrayImmutableDirectedGraph.Builder stitchedBuilder, LongSet externalCalls, Set<Long> addedNodes)
    {
        DepthFirstIterator<Long, LongLongPair> iterator = new DepthFirstIterator<>(this.fullGraph, startVertices);

        while (iterator.hasNext()) {
            long edge = iterator.next();

            if (!addedNodes.contains(edge)) {
                addNode(edge, stitchedBuilder, externalCalls);
                addedNodes.add(edge);
            }

            for (Long successor : this.fullGraph.successors(edge)) {
                if (!addedNodes.contains(successor)) {
                    addNode(successor, stitchedBuilder, externalCalls);
                    addedNodes.add(successor);
                }

                stitchedBuilder.addArc(edge, successor);
            }
        }
    }

    private void addNode(long node, ArrayImmutableDirectedGraph.Builder stitchedBuilder, LongSet externalCalls)
    {
        if (externalCalls.contains(node)) {
            stitchedBuilder.addExternalNode(node);
        } else {
            stitchedBuilder.addInternalNode(node);
        }
    }

    /**
     * @return the full graph
     */
    public DirectedGraph getFullGraph()
    {
        return this.fullGraph;
    }

    /**
     * @return the stitched graph
     */
    public DirectedGraph getStitchedGraph()
    {
        return this.stitchedGraph;
    }

    /**
     * @return the nodes which are part of the stitched graph
     */
    public List<StitchedGraphNode> getStitchedNodes()
    {
        List<StitchedGraphNode> nodes = new ArrayList<>();

        for (Long node : this.stitchedGraph.nodes()) {
            nodes.add(this.graphIdToNode.get(node));
        }

        return nodes;
    }

    public List<StitchedGraphNode> getStitchedNodes(JavaScope scope)
    {
        List<StitchedGraphNode> nodes = new ArrayList<>();

        for (Long nodeId : this.stitchedGraph.nodes()) {
            StitchedGraphNode node = this.graphIdToNode.get(nodeId);

            if (node.getScope() == scope) {
                nodes.add(node);
            }
        }

        return nodes;
    }

    /**
     * @param globalId the global id of the node in the graph
     * @return the node associated to the passed id
     */
    public StitchedGraphNode getNode(long globalId)
    {
        return this.graphIdToNode.get(globalId);
    }

    /**
     * @return the project's {@link ExtendedRevisionJavaCallGraph}
     */
    public MavenResolvedCallGraph getProjectRCG()
    {
        return this.projectRCG;
    }

    private long append(ArrayImmutableDirectedGraph.Builder builder, MavenResolvedCallGraph rcg, long offset)
    {
        long biggest = offset;

        // Add internal nodes
        biggest = Math.max(biggest, addMethods(JavaScope.internalTypes, rcg, offset, builder));
        biggest = Math.max(biggest, addMethods(JavaScope.resolvedTypes, rcg, offset, builder));
        biggest = Math.max(biggest, addMethods(JavaScope.externalTypes, rcg, offset, builder));

        // Arcs
        for (final List<Integer> l : rcg.getGraph().getGraph().getInternalCalls().keySet()) {
            builder.addArc(toGraphId(offset, l.get(0)), toGraphId(offset, l.get(1)));
        }
        for (final List<Integer> l : rcg.getGraph().getGraph().getResolvedCalls().keySet()) {
            // FIXME: Workaround a bug in fasten-core which sometimes duplicate the local calls as resolved
            // calls
            Long graphIdRight = toGraphId(offset, l.get(1));
            if (graphIdRight == null) {
                continue;
            }

            builder.addArc(toGraphId(offset, l.get(0)), toGraphId(offset, l.get(1)));
        }
        for (final List<Integer> l : rcg.getGraph().getGraph().getExternalCalls().keySet()) {
            // Skip external calls which are duplicated in the resolved calls
            Long graphIdRight = toGraphId(offset, l.get(1));
            if (graphIdRight == null) {
                continue;
            }

            builder.addArc(toGraphId(offset, l.get(0)), graphIdRight);
        }

        return biggest;
    }

    private long addMethods(JavaScope scope, MavenResolvedCallGraph rcg, long offset,
        ArrayImmutableDirectedGraph.Builder builder)
    {
        Map<FastenURI, JavaType> types = rcg.getGraph().getClassHierarchy().get(scope);

        long biggest = offset;

        for (Map.Entry<FastenURI, JavaType> aClass : types.entrySet()) {
            for (Map.Entry<Integer, JavaNode> methodEntry : aClass.getValue().getMethods().entrySet()) {
                // Calculate global version of the node id
                long globalId = toGlobalId(offset, methodEntry.getKey());

                // Increment next id offset
                biggest = Math.max(biggest, globalId);

                long graphId;

                if (scope == JavaScope.internalTypes) {
                    // Check if there is a resolved node matching this internal node
                    graphId = getGraphId(rcg.getGraph().product, globalId, methodEntry.getValue());

                    // Always add internal nodes (they are the reference)
                    addNode(graphId, scope, methodEntry.getValue(), rcg, false, builder);
                } else {
                    if (scope == JavaScope.externalTypes) {
                        // Check if there is a resolved node matching this external node signature
                        if (!this.localURIToGlobalId.containsKey(methodEntry.getValue().getUri())) {
                            graphId = globalId;

                            // Always add external nodes
                            addNode(graphId, scope, methodEntry.getValue(), rcg, true, builder);
                        } else {
                            // Forget external nodes which have been resolved
                            graphId = -1;
                        }
                    } else {
                        // FIXME: Warkaround a bug in fasten-core which sometimes duplicate the local calls as resolved
                        // calls
                        if (aClass.getKey().getProduct().equals(rcg.getGraph().product)) {
                            // Forget bad nodes
                            graphId = -1;
                        } else {
                            // Check if there is an internal node matching this resolved node
                            graphId = getGraphId(aClass.getKey().getProduct(), globalId, methodEntry.getValue());

                            // Add resolved node only if the internal node is not already there
                            if (graphId == globalId) {
                                addNode(graphId, scope, methodEntry.getValue(), rcg, false, builder);
                            }
                        }
                    }
                }

                if (graphId != -1) {
                    // Remember the mapping between a global id and its reference graph id (the id registered in the
                    // graph)
                    this.globalIdToGraphId.put(globalId, graphId);
                }
            }
        }

        return biggest;
    }

    private long getGraphId(String productId, long globalId, JavaNode node)
    {
        Map<FastenURI, Long> product = this.resolvedURIToGraphId.computeIfAbsent(productId, k -> new HashMap<>());

        return product.computeIfAbsent(node.getUri(), k -> globalId);
    }

    private void addNode(long graphId, JavaScope scope, JavaNode node, MavenResolvedCallGraph rcg, boolean external,
        ArrayImmutableDirectedGraph.Builder builder)
    {
        if (!this.graphIdToNode.containsKey(graphId)) {
            if (external) {
                builder.addExternalNode(graphId);
            } else {
                builder.addInternalNode(graphId);
            }

            this.localURIToGlobalId.computeIfAbsent(node.getUri(), k -> new HashSet<Long>()).add(graphId);
        }

        this.graphIdToNode.put(graphId, new StitchedGraphNode(graphId, scope, node, rcg));
    }

    private long toGlobalId(long offset, int localId)
    {
        return offset + localId + 1;
    }

    private Long toGraphId(long offset, int localId)
    {
        return this.globalIdToGraphId.get(toGlobalId(offset, localId));
    }
}
