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

import java.util.Set;

import org.apache.commons.collections4.SetUtils;

import eu.fasten.maven.StitchedGraphNode;

/**
 * Identify binary incompatibilities in the call graph.
 * 
 * @version $Id$
 */
public class BinaryRiskAnalyzer extends AbstractRiskAnalyzer
{
    private static final Set<String> PROVIDED_PACKAGES =
        SetUtils.hashSet("java.", "com.sun.", "sun.", "jdk.", "javax.", "jakarta.");

    @Override
    public void analyze(RiskContext context, RiskReport report)
    {
        // Report broken calls (unresolved external calls)
        for (long nodeId : context.getGraph().getStitchedGraph().externalNodes()) {
            StitchedGraphNode node = context.getGraph().getNode(nodeId);

            report.error(node.getLocalNode().getUri(), "The callable {} cannot be resolved.");
        }
    }

    @Override
    public boolean isCallableIgnored(String signature)
    {
        // Check if the class is a known standard Java class
        if (PROVIDED_PACKAGES.stream().anyMatch(signature::startsWith)) {
            return true;
        }

        // Fallback on standard filtering
        return super.isCallableIgnored(signature);
    }
}
