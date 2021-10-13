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

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.jgrapht.traverse.DepthFirstIterator;

import eu.fasten.maven.MavenGraphNode;
import eu.fasten.maven.analyzer.quality.QualityMetricAnalyzer;
import it.unimi.dsi.fastutil.longs.LongLongPair;

/**
 * Compare quality metric in the dependency with a configured threshold.
 * 
 * @version $Id$
 */
public class QualityRiskAnalyzer extends AbstractRiskAnalyzer
{
    private static final String QUALITY = "quality";

    private static final String QUALITY_METRICS = "metrics";

    private static final Set<String> METADATA = SetUtils.hashSet(QUALITY);

    @Override
    public Set<String> getCallableMetadatas()
    {
        return METADATA;
    }

    @Override
    public void analyze(RiskContext context, RiskReport report) throws MojoExecutionException
    {
        // Get configured metrics analyzers
        List<QualityMetricAnalyzer> metricAnalyzers = getAnalyzers();

        DepthFirstIterator<Long, LongLongPair> iterator =
            new DepthFirstIterator<>(context.getGraph().getOptimizedGraph());

        while (iterator.hasNext()) {
            long edge = iterator.next();

            MavenGraphNode node = context.getGraph().getNode(edge);

            Map<String, Object> quality = (Map<String, Object>) node.getLocalNode().getMetadata().get(QUALITY);

            if (quality != null) {
                Map<String, Object> metrics = (Map<String, Object>) quality.get(QUALITY_METRICS);

                metricAnalyzers.forEach(a -> a.analyze(context, node, metrics, report));
            }
        }
    }

    private List<QualityMetricAnalyzer> getAnalyzers() throws MojoExecutionException
    {
        List<QualityMetricAnalyzer> analyzers = new ArrayList<>(getConfiguration().getProperties().size());

        for (Map.Entry<String, Object> entry : getConfiguration().getProperties().entrySet()) {
            QualityMetricAnalyzer analyzer;
            try {
                analyzer = createAnalyzer(entry.getKey());
            } catch (Exception e) {
                throw new MojoExecutionException("Failed to create an analyzer for type " + entry.getKey(), e);
            }

            if (analyzer == null) {
                throw new MojoExecutionException(
                    "Could not find any quality metric analyzer for type " + entry.getKey());
            }

            analyzer.initialize(entry.getValue());

            analyzers.add(analyzer);
        }

        return analyzers;
    }

    private QualityMetricAnalyzer createAnalyzer(String type) throws InstantiationException, IllegalAccessException,
        IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException
    {
        // Try standard analyzers
        if (type.startsWith("fasten.quality.")) {
            String className = "eu.fasten.maven.analyzer.quality."
                + StringUtils.capitalize(type.substring("fasten.".length())) + "QualityMetricAnalyzer";
            try {
                Class<QualityMetricAnalyzer> clazz =
                    (Class<QualityMetricAnalyzer>) Thread.currentThread().getContextClassLoader().loadClass(className);

                return clazz.getDeclaredConstructor().newInstance();
            } catch (ClassNotFoundException e) {
                // TODO: Log something in debug
            }
        }

        // Try custom analyzer
        try {
            Class<QualityMetricAnalyzer> clazz =
                (Class<QualityMetricAnalyzer>) Thread.currentThread().getContextClassLoader().loadClass(type);

            return clazz.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            // TODO: Log something in debug
        }

        return null;
    }

}
