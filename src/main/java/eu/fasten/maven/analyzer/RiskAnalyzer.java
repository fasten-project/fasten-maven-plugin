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
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;

import eu.fasten.maven.MavenResolvedCallGraph;

/**
 * An extension point to process the call graph and report high and low risks found in it.
 * 
 * @version $Id$
 */
public interface RiskAnalyzer
{
    /**
     * @return the names of the metadata to retrieve when enriching the stitched call graph callables from remote server
     */
    Set<String> getCallableMetadatas();

    /**
     * @return the names of the metadata to retrieve when enriching the stitched call graph packages from remote server
     */
    Set<String> getPackageMetadatas();

    /**
     * @return extra metadata provided by Maven
     */
    Collection<String> getMavenExtras();

    /**
     * Initialize the analyzer with configuration provided in the project descriptor.
     * 
     * @param configuration the configuration of the analyzer
     */
    void initialize(RiskAnalyzerConfiguration configuration);

    /**
     * Run the analyzer on a given context and report high and low risks.
     * 
     * @param context information about the project and its dependencies like the stitched call graphs
     * @return a report of errors and warning hit while running the analyzer
     * @throws MojoExecutionException when the analyzer fails to execute
     */
    RiskReport analyze(RiskContext context) throws MojoExecutionException;

    boolean isCallableIgnored(String signature);

    boolean isDependencyIgnored(MavenResolvedCallGraph dependency);
}
