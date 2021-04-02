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

import java.util.Collections;
import java.util.Set;

import eu.fasten.core.data.FastenURI;
import eu.fasten.maven.MavenResolvedCallGraph;

/**
 * @version $Id$
 */
public abstract class AbstractRiskAnalyzer implements RiskAnalyzer
{
    private RiskAnalyzerConfiguration configuration;

    @Override
    public void initialize(RiskAnalyzerConfiguration configuration)
    {
        this.configuration = configuration;
    }

    /**
     * @return the configuration
     */
    public RiskAnalyzerConfiguration getConfiguration()
    {
        return configuration;
    }

    @Override
    public Set<String> getCallableMetadatas()
    {
        return Collections.emptySet();
    }

    @Override
    public Set<String> getPackageMetadatas()
    {
        return Collections.emptySet();
    }

    protected String getSignature(FastenURI uri)
    {
        return uri.getRawNamespace() + "." + uri.getRawEntity();
    }

    protected void reportError(FastenURI uri, String message, RiskReport report)
    {
        String signature = getSignature(uri);

        // Check of the class is ignored
        if (!isCallableIgnored(signature)) {
            report.error(message, signature);
        }
    }

    protected boolean isCallableIgnored(String signature)
    {
        // Check if the signature is covered by a configured ignore
        return getConfiguration().getIgnoredCallables().stream().anyMatch(p -> p.matcher(signature).matches());
    }

    protected boolean isDependencyIgnored(MavenResolvedCallGraph dependency)
    {
        String id = dependency.getArtifact().getGroupId() + ':' + dependency.getArtifact().getArtifactId();

        // Check if the dependency id is covered by a configured ignore
        return getConfiguration().getIgnoredCallables().stream().anyMatch(p -> p.matcher(id).matches());
    }
}
