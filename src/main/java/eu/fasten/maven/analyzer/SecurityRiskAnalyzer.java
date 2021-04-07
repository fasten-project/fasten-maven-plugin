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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.SetUtils;

import eu.fasten.core.data.FastenURI;
import eu.fasten.maven.MavenResolvedCallGraph;
import eu.fasten.maven.StitchedGraphNode;

/**
 * Find known security vulnerabilities in the stiched graph.
 * 
 * @version $Id$
 */
public class SecurityRiskAnalyzer extends AbstractRiskAnalyzer
{
    private static final String VULNERABILITIES = "vulnerabilities";

    private static final String VULNERABILITIES_SEVERITY = "severity";

    private static final String VULNERABILITY_DESCRIPTION = "description";

    private static final String VULNERABILITIES_URIS = "vulnerable_fasten_uris";

    private static final Set<String> METADATA = SetUtils.hashSet(VULNERABILITIES);

    @Override
    public Set<String> getPackageMetadatas()
    {
        return METADATA;
    }

    @Override
    public RiskReport analyze(RiskContext context)
    {
        RiskReport report = new RiskReport(this);

        for (MavenResolvedCallGraph dependency : context.getGraph().getStitchedDependenciesRCGs()) {
            if (dependency.isRemote() && !isDependencyIgnored(dependency)) {
                Map<String, Map<String, Object>> vulnerabilities = (Map) dependency.getMetadata().get(VULNERABILITIES);

                if (MapUtils.isNotEmpty(vulnerabilities)) {
                    // The dependency is affected by a security vulnerability
                    for (Map.Entry<String, Map<String, Object>> entry : vulnerabilities.entrySet()) {
                        String vulnerabilityId = entry.getKey();
                        Map<String, Object> vulnerability = entry.getValue();

                        // Make sure one of the affected methods is part of the stitched call graph
                        Set<StitchedGraphNode> nodes = vulnerableCallables(vulnerability, context);

                        if (nodes != null) {
                            // TODO: report it but as a warning if no known method is affected ?
                            if (!nodes.isEmpty()) {
                                StringBuilder builder = new StringBuilder(
                                    "The vulnerability {} affects dependency {} because of the following used callables:");

                                for (StitchedGraphNode node : nodes) {
                                    builder.append('\n');
                                    builder.append("  * ");
                                    builder.append(getSignature(node.getLocalNode().getUri()));
                                }

                                report.error(builder.toString(), vulnerabilityId, dependency.getArtifact());
                            }
                        } else {
                            report.error("The vulnerability {} affects dependency {}", vulnerabilityId,
                                dependency.getArtifact());
                        }
                    }
                }
            }
        }

        return report;
    }

    private Set<StitchedGraphNode> vulnerableCallables(Map<String, Object> vulnerability, RiskContext context)
    {
        List<String> uris = (List<String>) vulnerability.get(VULNERABILITIES_URIS);

        if (uris == null) {
            // The package does not contain any callable information
            return null;
        }

        Set<StitchedGraphNode> nodes = new LinkedHashSet<>(uris.size());
        for (String uri : uris) {
            FastenURI fastenURI = FastenURI.create(uri);

            // Check if the callable is ignored
            String callableSignature = getSignature(fastenURI);
            if (!isCallableIgnored(callableSignature)) {
                // Find the callable node and make sure it's part of the stiched call graph
                StitchedGraphNode node = context.getGraph().getNode(fastenURI, true);

                if (node != null) {
                    nodes.add(node);
                }
            }
        }

        return nodes;
    }
}
