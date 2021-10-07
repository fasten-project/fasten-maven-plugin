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

import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.JavaNode;
import eu.fasten.core.data.JavaScope;
import eu.fasten.core.data.JavaType;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntIntPair;
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

    private final List<MavenResolvedCallGraph> fullDependenciesRCGs;

    private final Set<MavenResolvedCallGraph> stitchedDependenciesRCGs;

    private final MavenMergedDirectedGraph fullGraph;

    private final MavenMergedDirectedGraph stitchedGraph;

    private Map<Long, StitchedGraphNode> graphIdToNode = new HashMap<>();

    private Map<Long, Long> globalIdToGraphId = new HashMap<>();

    private Map<FastenURI, Set<Long>> localURIToGlobalId = new HashMap<>();

    private Map<String, Map<FastenURI, Long>> localProductURIToGraphId = new HashMap<>();

    public StitchedGraph(MavenResolvedCallGraph projectRCG, List<MavenResolvedCallGraph> dependencyRCGs)
    {
        this.projectRCG = projectRCG;
        this.fullDependenciesRCGs = new ArrayList<>(dependencyRCGs);
        this.stitchedDependenciesRCGs = new HashSet<>();

        // Build full graph

        this.fullGraph = new MavenMergedDirectedGraph();

        // Add internal calls

        long offset = append(this.fullGraph, projectRCG, -1);

        for (MavenResolvedCallGraph dependencyRCG : this.fullDependenciesRCGs) {
            offset = append(this.fullGraph, dependencyRCG, offset);
        }

        // Get main project calls
        IteratorChain<Long> projectCalls = new IteratorChain<>(
            projectRCG.getGraph().getGraph().getInternalCalls().keySet().stream().map(link -> (long) link.leftInt())
                .iterator(),
            projectRCG.getGraph().getGraph().getExternalCalls().keySet().stream()
                .map(link -> this.globalIdToGraphId.get((long) link.leftInt())).iterator());

        // Build stitched graph

        this.stitchedGraph = new MavenMergedDirectedGraph();

        appendNodeAndSuccessors(new IteratorIterable<>(projectCalls), this.stitchedGraph,
            this.fullGraph.externalNodes());
    }

    private void appendNodeAndSuccessors(Iterable<Long> startVertices, MavenMergedDirectedGraph stitchedGraph,
        LongSet externalCalls)
    {
        DepthFirstIterator<Long, LongLongPair> iterator = new DepthFirstIterator<>(this.fullGraph, startVertices);

        while (iterator.hasNext()) {
            long edge = iterator.next();

            this.stitchedDependenciesRCGs.add(getNode(edge).getPackageRCG());

            addNode(edge, stitchedGraph, externalCalls);

            for (Long successor : this.fullGraph.successors(edge)) {
                addNode(successor, stitchedGraph, externalCalls);

                stitchedGraph.addEdge(edge, successor);
            }
        }
    }

    private void addNode(long node, MavenMergedDirectedGraph stitchedGraph, LongSet externalCalls)
    {
        if (externalCalls.contains(node)) {
            stitchedGraph.addExternalNode(node);
        } else {
            stitchedGraph.addInternalNode(node);
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

    /**
     * @return the project's dependencies {@link ExtendedRevisionJavaCallGraph}
     */
    public List<MavenResolvedCallGraph> getFullDependenciesRCGs()
    {
        return this.fullDependenciesRCGs;
    }

    /**
     * @return the project's dependencies containing stitched calls
     */
    public Set<MavenResolvedCallGraph> getStitchedDependenciesRCGs()
    {
        return this.stitchedDependenciesRCGs;
    }

    private long append(MavenMergedDirectedGraph graph, MavenResolvedCallGraph rcg, long offset)
    {
        long biggest = offset;

        // Add nodes
        biggest = Math.max(biggest, addMethods(JavaScope.internalTypes, rcg, offset, graph));
        biggest = Math.max(biggest, addMethods(JavaScope.resolvedTypes, rcg, offset, graph));
        biggest = Math.max(biggest, addMethods(JavaScope.externalTypes, rcg, offset, graph));

        // Arcs
        for (final IntIntPair link : rcg.getGraph().getGraph().getInternalCalls().keySet()) {
            graph.addEdge(toGraphId(offset, link.leftInt()), toGraphId(offset, link.rightInt()));
        }
        for (final IntIntPair link : rcg.getGraph().getGraph().getResolvedCalls().keySet()) {
            // FIXME: Workaround a bug in fasten-core which sometimes duplicate the local calls as resolved
            // calls
            Long graphIdRight = toGraphId(offset, link.rightInt());
            if (graphIdRight == null) {
                continue;
            }

            graph.addEdge(toGraphId(offset, link.leftInt()), graphIdRight);
        }
        for (final IntIntPair link : rcg.getGraph().getGraph().getExternalCalls().keySet()) {
            // Skip external calls which are duplicated in the resolved calls
            Long graphIdRight = toGraphId(offset, link.rightInt());
            if (graphIdRight == null) {
                continue;
            }

            graph.addEdge(toGraphId(offset, link.leftInt()), graphIdRight);
        }

        return biggest;
    }

    private long addMethods(JavaScope scope, MavenResolvedCallGraph rcg, long offset, MavenMergedDirectedGraph graph)
    {
        Map<String, JavaType> types = rcg.getGraph().getClassHierarchy().get(scope);

        long biggest = offset;

        for (Map.Entry<String, JavaType> aClass : types.entrySet()) {
            for (Int2ObjectMap.Entry<JavaNode> methodEntry : aClass.getValue().getMethods().int2ObjectEntrySet()) {
                // Calculate global version of the node id
                long globalId = toGlobalId(offset, methodEntry.getIntKey());

                // Increment next id offset
                biggest = Math.max(biggest, globalId);

                long graphId;

                if (scope == JavaScope.internalTypes) {
                    // Check if there is a resolved node matching this internal node
                    graphId = getGraphId(rcg.getGraph().product, globalId, methodEntry.getValue());

                    // Always add internal nodes (they are the reference)
                    addNode(graphId, scope, methodEntry.getValue(), rcg, false, graph);
                } else {
                    if (scope == JavaScope.externalTypes) {
                        // Check if there is a resolved node matching this external node signature
                        if (!this.localURIToGlobalId.containsKey(methodEntry.getValue().getUri())) {
                            graphId = globalId;

                            // Always add external nodes
                            addNode(graphId, scope, methodEntry.getValue(), rcg, true, graph);
                        } else {
                            // Forget external nodes which have been resolved
                            graphId = -1;
                        }
                    } else {
                        // FIXME: Workaround a bug in fasten-core which sometimes duplicate the local calls as resolved
                        // calls
                        FastenURI fastenURI = FastenURI.create(aClass.getKey());
                        if (fastenURI.getProduct().equals(rcg.getGraph().product)) {
                            // Forget bad nodes
                            graphId = -1;
                        } else {
                            // Check if there is an internal node matching this resolved node
                            graphId = getGraphId(fastenURI.getProduct(), globalId, methodEntry.getValue());

                            // Add resolved node only if the internal node is not already there
                            if (graphId == globalId) {
                                addNode(graphId, scope, methodEntry.getValue(), rcg, false, graph);
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
        Map<FastenURI, Long> product = this.localProductURIToGraphId.computeIfAbsent(productId, k -> new HashMap<>());

        return product.computeIfAbsent(node.getUri(), k -> globalId);
    }

    private void addNode(long graphId, JavaScope scope, JavaNode node, MavenResolvedCallGraph rcg, boolean external,
        MavenMergedDirectedGraph graph)
    {
        if (external) {
            graph.addExternalNode(graphId);
        } else {
            graph.addInternalNode(graphId);
        }

        this.localURIToGlobalId.computeIfAbsent(node.getUri(), k -> new HashSet<Long>()).add(graphId);

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

    public StitchedGraphNode getNode(FastenURI fastenURI, boolean stitched)
    {
        if (fastenURI.getProduct() != null) {
            Map<FastenURI, Long> product = this.localProductURIToGraphId.get(fastenURI.getProduct());

            long graphId = product.get(toLocalFastenURI(fastenURI));

            if (!stitched || this.stitchedGraph.nodes().contains(graphId)) {
                return getNode(graphId);
            }
        }

        return null;
    }

    private FastenURI toLocalFastenURI(FastenURI fullFastenURI)
    {
        return FastenURI.createSchemeless(null, null, null, fullFastenURI.getRawNamespace(),
            fullFastenURI.getRawEntity());
    }
}
