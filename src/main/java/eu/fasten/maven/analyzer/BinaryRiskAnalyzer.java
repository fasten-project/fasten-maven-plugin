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

import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.JavaScope;
import eu.fasten.maven.StitchedGraphNode;

/**
 * Identify binary incompatibilities in the call graph.
 * 
 * @version $Id$
 */
public class BinaryRiskAnalyzer extends AbstractRiskAnalyzer
{
    private static final Set<String> PROVIDED_PACKAGES =
        SetUtils.hashSet("java.", "com.sun.", "sun.", "jdk.", "javax.");

    @Override
    public RiskReport analyze(RiskContext context)
    {
        RiskReport report = new RiskReport(this);

        for (StitchedGraphNode node : context.getGraph().getStichedNodes(JavaScope.externalTypes)) {
            FastenURI uri = node.getLocalNode().getUri();
            String signature = uri.getRawNamespace() + "." + uri.getRawEntity();

            // Check of the class is ignored
            if (!isIgnored(signature)) {
                report.error("The callable " + signature + " cannot be resolved.");
            }
        }

        return report;
    }

    private boolean isIgnored(String signature)
    {
        // Check if the class is a known standard Java class
        if (PROVIDED_PACKAGES.stream().anyMatch(signature::startsWith)) {
            return true;
        }

        // Check if the signature is covered by a configured ignore
        return getConfiguration().getIgnoredCallables().stream().anyMatch(p -> p.matcher(signature).matches());
    }
}
