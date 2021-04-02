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
package eu.fasten.maven;

import java.util.HashMap;
import java.util.Map;

import org.apache.maven.artifact.Artifact;

import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;

/**
 * Wrapp a {@link ExtendedRevisionJavaCallGraph} and add some Maven plugin specific information.
 * 
 * @version $Id$
 */
public class MavenResolvedCallGraph
{
    private final Artifact artifact;

    private final boolean remote;

    private final ExtendedRevisionJavaCallGraph graph;

    private final Map<String, Object> metadata = new HashMap<>();

    /**
     * @param artifact the Maven artifact
     * @param remote true if the call graph originate from a FASTEN server
     * @param graph the actual call graph
     */
    public MavenResolvedCallGraph(Artifact artifact, boolean remote, ExtendedRevisionJavaCallGraph graph)
    {
        this.artifact = artifact;
        this.remote = remote;
        this.graph = graph;
    }

    /**
     * @return the Maven artifact
     */
    public Artifact getArtifact()
    {
        return this.artifact;
    }

    /**
     * @return true if the call graph originate from a FASTEN server
     */
    public boolean isRemote()
    {
        return this.remote;
    }

    /**
     * @return the actual call graph
     */
    public ExtendedRevisionJavaCallGraph getGraph()
    {
        return this.graph;
    }

    /**
     * @return the metadata associated with the package
     */
    public Map<String, Object> getMetadata()
    {
        return this.metadata;
    }
}
