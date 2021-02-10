/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package eu.fasten.maven;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import eu.fasten.core.data.ArrayImmutableDirectedGraph;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.JavaNode;
import eu.fasten.core.data.JavaScope;
import eu.fasten.core.data.JavaType;

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

    private final DirectedGraph stichedGraph;

    private Map<Long, StitchedGraphNode> idToNode = new HashMap<>();

    private Map<Long, Long> globalIdToGraphId = new HashMap<>();

    private Map<FastenURI, Long> localURIToGlobalId = new HashMap<>();

    public StitchedGraph(MavenResolvedCallGraph projectRCG, List<MavenResolvedCallGraph> dependencyRCGs)
    {
        this.projectRCG = projectRCG;
        this.dependenciesRCGs = new ArrayList<>(dependencyRCGs);

        // Build full graph

        ArrayImmutableDirectedGraph.Builder fullBuilder = new ArrayImmutableDirectedGraph.Builder();

        long offset = append(fullBuilder, projectRCG, -1);

        for (MavenResolvedCallGraph dependencyRCG : this.dependenciesRCGs) {
            offset = append(fullBuilder, dependencyRCG, offset);
        }

        this.fullGraph = fullBuilder.build();

        // Build stitched graph

        ArrayImmutableDirectedGraph.Builder stichedBuilder = new ArrayImmutableDirectedGraph.Builder();

        Set<Long> handledNodes = new HashSet<>();

        for (final List<Integer> l : projectRCG.getGraph().getGraph().getInternalCalls().keySet()) {
            appendNodeAndSuccessors(l.get(0).longValue(), stichedBuilder, handledNodes);
        }
        for (final List<Integer> l : projectRCG.getGraph().getGraph().getExternalCalls().keySet()) {
            appendNodeAndSuccessors(l.get(0).longValue(), stichedBuilder, handledNodes);
        }

        this.stichedGraph = stichedBuilder.build();
    }

    private void appendNodeAndSuccessors(long node, ArrayImmutableDirectedGraph.Builder stichedBuilder,
        Set<Long> handledNodes)
    {
        if (!handledNodes.contains(node)) {
            addNode(node, stichedBuilder);

            handledNodes.add(node);

            for (Long successor : this.fullGraph.successors(node)) {
                appendNodeAndSuccessors(successor, stichedBuilder, handledNodes);

                stichedBuilder.addArc(node, successor);
            }
        }
    }

    private void addNode(long node, ArrayImmutableDirectedGraph.Builder stichedBuilder)
    {
        if (this.fullGraph.externalNodes().contains(node)) {
            stichedBuilder.addExternalNode(node);
        } else {
            stichedBuilder.addInternalNode(node);
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
    public DirectedGraph getStichedGraph()
    {
        return this.stichedGraph;
    }

    /**
     * @return the nodes which are part of the stitched graph
     */
    public List<StitchedGraphNode> getStichedNodes()
    {
        List<StitchedGraphNode> nodes = new ArrayList<>();

        for (Long node : this.stichedGraph.nodes()) {
            nodes.add(this.idToNode.get(node));
        }

        return nodes;
    }

    /**
     * @param globalId the global id of the node in the graph
     * @return the node associated to the passed id
     */
    public StitchedGraphNode getNode(long globalId)
    {
        return this.idToNode.get(globalId);
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

        // Nodes
        biggest = Math.max(biggest, addMethods(JavaScope.internalTypes, rcg, offset, false, builder));
        biggest = Math.max(biggest, addMethods(JavaScope.resolvedTypes, rcg, offset, false, builder));
        biggest = Math.max(biggest, addMethods(JavaScope.externalTypes, rcg, offset, true, builder));

        // Arcs
        for (final List<Integer> l : rcg.getGraph().getGraph().getInternalCalls().keySet()) {
            builder.addArc(toGraphId(offset, l.get(0)), toGraphId(offset, l.get(1)));
        }
        for (final List<Integer> l : rcg.getGraph().getGraph().getExternalCalls().keySet()) {
            builder.addArc(toGraphId(offset, l.get(0)), toGraphId(offset, l.get(1)));
        }

        return biggest;
    }

    private long addMethods(JavaScope scope, MavenResolvedCallGraph rcg, long offset, boolean external,
        ArrayImmutableDirectedGraph.Builder builder)
    {
        Map<FastenURI, JavaType> types = rcg.getGraph().getClassHierarchy().get(scope);

        long biggest = offset;

        for (var aClass : types.entrySet()) {
            for (Map.Entry<Integer, JavaNode> methodEntry : aClass.getValue().getMethods().entrySet()) {
                // Calculate global version of the node id
                long globalId = toGlobalId(offset, methodEntry.getKey());

                // Search if the already been hit
                Long graphId = this.localURIToGlobalId.get(methodEntry.getValue().getUri());
                if (graphId == null) {
                    // Create a new node and insert it in the graph
                    addNode(globalId, scope, methodEntry.getValue(), rcg, external, builder);

                    // Increment next id offset
                    biggest = Math.max(biggest, globalId);

                    // Remember the mapping between a global id and its reference graph id (the id registered in the
                    // graph)
                    this.globalIdToGraphId.put(globalId, globalId);
                } else {
                    if (scope == JavaScope.internalTypes) {
                        // Replace the resolved node with its local version
                        this.idToNode.put(graphId, new StitchedGraphNode(graphId, scope, methodEntry.getValue(), rcg));

                        this.globalIdToGraphId.put(globalId, graphId);
                    } else {
                        this.globalIdToGraphId.put(globalId, graphId);
                    }
                }
            }
        }

        return biggest;
    }

    private void addNode(long globalId, JavaScope scope, JavaNode node, MavenResolvedCallGraph rcg, boolean external,
        ArrayImmutableDirectedGraph.Builder builder)
    {
        if (external) {
            builder.addExternalNode(globalId);
        } else {
            builder.addInternalNode(globalId);
        }

        this.idToNode.put(globalId, new StitchedGraphNode(globalId, scope, node, rcg));
        this.localURIToGlobalId.put(node.getUri(), globalId);
    }

    private long toGlobalId(long offset, int localId)
    {
        return offset + localId + 1;
    }

    private long toGraphId(long offset, int localId)
    {
        return this.globalIdToGraphId.get(toGlobalId(offset, localId));
    }
}
