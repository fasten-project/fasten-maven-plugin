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
package eu.fasten.maven.analyzer;

import eu.fasten.maven.StitchedGraph;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.json.JSONExporter;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class QualityRiskAnalyzer extends AbstractRiskAnalyzer {

    @Override
    public RiskReport analyze(RiskContext context) {
        try {
            exportGraph(context.getGraph());
        } catch (java.io.IOException e) {
            System.err.println("Failed to export the stitched graph: " + e);
        }
        return null;
    }

    @Override
    public Set<String> getMetadatas()
    {
        var metadataAttributes = new HashSet<String>();
        metadataAttributes.add("quality");
        return metadataAttributes;
    }

    private void exportGraph(StitchedGraph graph) throws java.io.IOException {
        Function<Long, Map<String, Attribute>> vertexAttributeProvider = v -> {
            Map<String, Attribute> map = new LinkedHashMap<>();
            var snode = graph.getNode(v);
            var metadata_map = snode.getLocalNode().getMetadata();
            var metadata = new JSONObject(metadata_map);
            map.put("metadata",
                    DefaultAttribute.createAttribute(metadata.toString()));
            map.put("uri", DefaultAttribute.createAttribute(snode.getFullURI()));
            map.put("application_node", DefaultAttribute.createAttribute(Boolean.TRUE));
            return map;
        };
        JSONExporter<Long, LongLongPair> exporter = new JSONExporter(v -> String.valueOf(v));

        exporter.setVertexAttributeProvider(vertexAttributeProvider);
        File e_graph = new File("project.enriched.jgrapht.json");
        var out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(e_graph), StandardCharsets.UTF_8));
        exporter.exportGraph(graph.getStitchedGraph(), out);
        out.close();
    }
}
