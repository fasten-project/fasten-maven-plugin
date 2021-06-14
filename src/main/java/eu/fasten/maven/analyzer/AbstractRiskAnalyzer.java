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

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;

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

    @Override
    public Set<String> getMavenExtras()
    {
        return Collections.emptySet();
    }

    @Override
    public RiskReport analyze(RiskContext context) throws MojoExecutionException
    {
        RiskReport report = new RiskReport(this);

        analyze(context, report);

        return report;
    }

    protected abstract void analyze(RiskContext context, RiskReport report) throws MojoExecutionException;

    @Override
    public boolean isCallableIgnored(String signature)
    {
        return getConfiguration().isCallableIgnored(signature);
    }

    @Override
    public boolean isDependencyIgnored(MavenResolvedCallGraph dependency)
    {
        return getConfiguration().isDependencyIgnored(dependency);
    }
}
