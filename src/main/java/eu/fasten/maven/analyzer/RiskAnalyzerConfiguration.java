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
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import eu.fasten.core.data.FastenURI;
import eu.fasten.maven.MavenResolvedCallGraph;

/**
 * Configure the behavior of a {@link RiskAnalyzer}.
 * 
 * @version $Id$
 */
public class RiskAnalyzerConfiguration
{
    private String type;

    private Boolean failOnRisk;

    private List<Pattern> ignoredCallables = Collections.emptyList();

    private List<Pattern> ignoredDependencies = Collections.emptyList();

    private RiskAnalyzerProperties properties;

    /**
     * @return the identifier of the risk analyzer
     */
    public String getType()
    {
        return this.type;
    }

    /**
     * @param type the identifier of the risk analyzer
     */
    public void setType(String type)
    {
        this.type = type;
    }

    /**
     * @return true/false if the analyzer have a specific behavior, null to fallback on the general behavior
     */
    public Boolean getFailOnRisk()
    {
        return this.failOnRisk;
    }

    /**
     * @param failOnRisk true/false if the analyzer have a specific behavior, null to fallback on the general behavior
     */
    public void setFailOnRisk(Boolean failOnRisk)
    {
        this.failOnRisk = failOnRisk;
    }

    /**
     * @return the patterns matching callables to ignore in the analyzer
     */
    public List<Pattern> getIgnoredCallables()
    {
        return this.ignoredCallables;
    }

    /**
     * @param ignoredCallables the patterns matching callables to ignore in the analyzer
     */
    public void setIgnoredCallables(List<String> ignoredCallables)
    {
        this.ignoredCallables = ignoredCallables.stream().map(Pattern::compile).collect(Collectors.toList());
    }

    /**
     * @return the patterns matching the dependencies to ignore in the analyzer
     */
    public List<Pattern> getIgnoredDependencies()
    {
        return this.ignoredDependencies;
    }

    /**
     * @param ignoredDependencies the patterns matching the dependencies to ignore in the analyzer
     */
    public void setIgnoredDependencies(List<String> ignoredDependencies)
    {
        this.ignoredDependencies = ignoredDependencies.stream().map(Pattern::compile).collect(Collectors.toList());
    }

    /**
     * @return the custom properties specific to the type
     */
    public RiskAnalyzerProperties getProperties()
    {
        return this.properties;
    }

    /**
     * @param properties the custom properties specific to the type
     */
    public void setProperties(RiskAnalyzerProperties properties)
    {
        this.properties = properties;
    }

    public static String toSignature(FastenURI uri)
    {
        return uri.getRawNamespace() + "." + uri.getRawEntity();
    }

    public boolean isCallableIgnored(String signature)
    {
        // Check if the signature is covered by a configured ignore
        return getIgnoredCallables().stream().anyMatch(p -> p.matcher(signature).matches());
    }

    public boolean isDependencyIgnored(MavenResolvedCallGraph dependency)
    {
        String id = dependency.getArtifact().getGroupId() + ':' + dependency.getArtifact().getArtifactId();

        // Check if the dependency id is covered by a configured ignore
        return getIgnoredDependencies().stream().anyMatch(p -> p.matcher(id).matches());
    }
}
