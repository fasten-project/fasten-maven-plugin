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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.ListUtils;
import org.apache.maven.plugin.logging.Log;
import org.jgrapht.traverse.DepthFirstIterator;

import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.JavaNode;
import eu.fasten.core.data.JavaScope;
import eu.fasten.core.data.JavaType;
import eu.fasten.core.data.MergedDirectedGraph;
import eu.fasten.core.merge.CGMerger;
import it.unimi.dsi.fastutil.longs.LongLongPair;

/**
 * Create and navigate an optimized call graph.
 * 
 * @version $Id$
 */
public class MavenGraph
{
    private final MavenExtendedRevisionJavaCallGraph projectRCG;

    private final List<MavenExtendedRevisionJavaCallGraph> fullDependenciesCGs;

    private final Map<String, MavenExtendedRevisionJavaCallGraph> packages;

    private final Set<MavenExtendedRevisionJavaCallGraph> optimizedDependenciesCGs;

    private final MergedDirectedGraph fullGraph;

    private final MergedDirectedGraph optimizedGraph;

    private Map<Long, MavenGraphNode> graphIdToNode = new HashMap<>();

    private Map<String, Map<FastenURI, Long>> localProductURIToGraphId = new HashMap<>();

    private final Log log;

    public MavenGraph(MavenExtendedRevisionJavaCallGraph projectRCG,
        List<MavenExtendedRevisionJavaCallGraph> dependencyRCGs, Log log)
    {
        this.log = log;
        this.projectRCG = projectRCG;
        this.fullDependenciesCGs = new ArrayList<>(dependencyRCGs);
        this.optimizedDependenciesCGs = new HashSet<>();

        this.packages = new HashMap<>(this.fullDependenciesCGs.size() + 1);
        this.packages.put(this.projectRCG.product, this.projectRCG);
        for (MavenExtendedRevisionJavaCallGraph dependency : this.fullDependenciesCGs) {
            this.packages.put(dependency.product, dependency);
        }

        ///////////////////////
        // Build full graph

        this.log.info("Creating full call graph");

        this.fullGraph = createFullGraph();

        ///////////////////////
        // Build optimized graph

        this.log.info("Creating optimized call graph");

        this.optimizedGraph = createOptimizeGraph();
    }

    private void index(CGMerger merger)
    {
        for (Map.Entry<Long, String> entry : merger.getAllUris().entrySet()) {
            FastenJavaURI fullURI = FastenJavaURI.create(entry.getValue());

            if (fullURI.getProduct() == null) {
                this.graphIdToNode.put(entry.getKey(),
                    new MavenGraphNode(entry.getKey(), JavaScope.externalTypes, new JavaNode(fullURI, null), null));
            } else {
                MavenExtendedRevisionJavaCallGraph cg = this.packages.get(fullURI.getProduct());

                JavaType type = cg.getClassHierarchy().get(JavaScope.internalTypes).get(FastenJavaURI
                    .createWithoutFunction("/" + fullURI.getNamespace() + "/" + fullURI.getClassName()).toString());
                if (type != null) {
                    Optional<JavaNode> nodeOptional = type.getMethods().values().stream()
                        .filter(n -> n.getUri().getEntity().equals(fullURI.getEntity())).findFirst();

                    if (nodeOptional.isPresent()) {
                        JavaNode node = nodeOptional.get();

                        Map<FastenURI, Long> product =
                            this.localProductURIToGraphId.computeIfAbsent(fullURI.getProduct(), k -> new HashMap<>());

                        product.put(node.getUri(), entry.getKey());
                        this.graphIdToNode.put(entry.getKey(),
                            new MavenGraphNode(entry.getKey(), JavaScope.internalTypes, node, cg));
                    }
                }
            }
        }
    }

    private MergedDirectedGraph createFullGraph()
    {
        CGMerger merger =
            new CGMerger(ListUtils.union(Collections.singletonList(this.projectRCG), this.fullDependenciesCGs), true);

        // Generate the graph
        MergedDirectedGraph graph = (MergedDirectedGraph) merger.mergeAllDeps();

        // Store the generated mapping between the node id and its full URL
        index(merger);

        return graph;
    }

    private MergedDirectedGraph createOptimizeGraph()
    {
        MergedDirectedGraph graph = new MergedDirectedGraph();

        // Get main project sources
        Set<Long> startVertices = this.projectRCG.getGraph().getCallSites().keySet().stream()
            .map(link -> (long) link.leftInt()).collect(Collectors.toSet());

        DepthFirstIterator<Long, LongLongPair> iterator = new DepthFirstIterator<>(this.fullGraph, startVertices);

        while (iterator.hasNext()) {
            long edge = iterator.next();

            getNode(edge).getPackageCG().ifPresent(this.optimizedDependenciesCGs::add);

            addNode(edge, graph);

            for (Long successor : this.fullGraph.successors(edge)) {
                addNode(successor, graph);

                graph.addEdge(edge, successor);
            }
        }

        return graph;
    }

    private void addNode(long node, MergedDirectedGraph optimizedGraph)
    {
        if (this.fullGraph.isExternal(node)) {
            optimizedGraph.addExternalNode(node);
        } else {
            optimizedGraph.addInternalNode(node);
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
     * @return the optimized graph
     */
    public DirectedGraph getOptimizedGraph()
    {
        return this.optimizedGraph;
    }

    /**
     * @return the nodes which are part of the optimized graph
     */
    public List<MavenGraphNode> getOptimizedNodes()
    {
        List<MavenGraphNode> nodes = new ArrayList<>();

        for (Long node : this.optimizedGraph.nodes()) {
            nodes.add(this.graphIdToNode.get(node));
        }

        return nodes;
    }

    public List<MavenGraphNode> getOtimizedNodes(JavaScope scope)
    {
        List<MavenGraphNode> nodes = new ArrayList<>();

        for (Long nodeId : this.optimizedGraph.nodes()) {
            MavenGraphNode node = this.graphIdToNode.get(nodeId);

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
    public MavenGraphNode getNode(long globalId)
    {
        return this.graphIdToNode.get(globalId);
    }

    /**
     * @return the project's {@link ExtendedRevisionJavaCallGraph}
     */
    public MavenExtendedRevisionJavaCallGraph getProjectCG()
    {
        return this.projectRCG;
    }

    /**
     * @return the project's dependencies {@link ExtendedRevisionJavaCallGraph}
     */
    public List<MavenExtendedRevisionJavaCallGraph> getFullDependenciesCGs()
    {
        return this.fullDependenciesCGs;
    }

    /**
     * @return the project's dependencies containing optimized calls
     */
    public Set<MavenExtendedRevisionJavaCallGraph> getOptimizedDependenciesRCGs()
    {
        return this.optimizedDependenciesCGs;
    }

    public MavenGraphNode getNode(FastenURI fastenURI, boolean optimized)
    {
        if (fastenURI.getProduct() != null) {
            Map<FastenURI, Long> product = this.localProductURIToGraphId.get(fastenURI.getProduct());

            long graphId = product.get(toLocalFastenURI(fastenURI));

            if (!optimized || this.optimizedGraph.nodes().contains(graphId)) {
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

    public void serialize(File folder) throws IOException
    {
        // Make sure the folder exist
        folder.mkdirs();

        File mapFile = new File(folder, "nodes.txt");

        try (FileWriter writer = new FileWriter(mapFile)) {
            for (Map.Entry<Long, MavenGraphNode> entry : this.graphIdToNode.entrySet()) {
                writer.append(entry.getKey().toString());
                writer.append(':');
                writer.append(entry.getValue().getFullURI());
                writer.append('\n');
            }
        }

        File fullGraphFile = new File(folder, "fullGraph.txt");

        try (FileWriter writer = new FileWriter(fullGraphFile)) {
            for (LongLongPair edge : this.fullGraph.edgeSet()) {
                writer.append(String.valueOf(edge.leftLong()));
                writer.append(" -> ");
                writer.append(String.valueOf(edge.rightLong()));
                writer.append('\n');
            }
        }

        File optimizedGraphFile = new File(folder, "optimizedGraph.txt");

        try (FileWriter writer = new FileWriter(optimizedGraphFile)) {
            for (LongLongPair edge : this.optimizedGraph.edgeSet()) {
                writer.append(String.valueOf(edge.leftLong()));
                writer.append(" -> ");
                writer.append(String.valueOf(edge.rightLong()));
                writer.append('\n');
            }
        }
    }
}
