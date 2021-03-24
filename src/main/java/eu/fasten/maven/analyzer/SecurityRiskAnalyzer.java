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

import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.SetUtils;
import org.jgrapht.traverse.DepthFirstIterator;

import eu.fasten.core.data.JavaScope;
import eu.fasten.maven.StitchedGraphNode;
import it.unimi.dsi.fastutil.longs.LongLongPair;

/**
 * Find known security vulnerabilities in the stiched graph.
 * 
 * @version $Id$
 */
public class SecurityRiskAnalyzer extends AbstractRiskAnalyzer
{
    private static final String VULNERABILITIES = "vulnerabilities";

    private static final String VULNERABILITY_DESCRIPTION = "description";

    private static final Set<String> METADATA = SetUtils.hashSet(VULNERABILITIES);

    @Override
    public Set<String> getMetadatas()
    {
        return METADATA;
    }

    @Override
    public RiskReport analyze(RiskContext context)
    {
        RiskReport report = new RiskReport(this);

        DepthFirstIterator<Long, LongLongPair> iterator =
            new DepthFirstIterator<>(context.getGraph().getStitchedGraph());

        while (iterator.hasNext()) {
            long edge = iterator.next();

            StitchedGraphNode node = context.getGraph().getNode(edge);

            if (node.getPackageRCG().isRemote() && node.getScope() == JavaScope.internalTypes) {
                Map<String, Map<String, Object>> vulnerabilities =
                    (Map) node.getLocalNode().getMetadata().get(VULNERABILITIES);

                if (MapUtils.isNotEmpty(vulnerabilities)) {
                    StringBuilder builder = new StringBuilder("Vulnerabilities have been found in the callable {}:");
                    for (Map.Entry<String, Map<String, Object>> vulnerability : vulnerabilities.entrySet()) {
                        builder.append('\n');
                        builder.append(vulnerability.getKey());
                        builder.append(':');
                        builder.append(vulnerability.getValue().get(VULNERABILITY_DESCRIPTION));
                    }

                    reportError(node.getLocalNode().getUri(), builder.toString(), report);
                }
            }
        }

        return new RiskReport(this);
    }
}
