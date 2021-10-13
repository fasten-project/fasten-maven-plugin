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

import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.Maven;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.License;
import org.json.JSONObject;
import org.json.JSONTokener;

import eu.fasten.core.data.ExtendedBuilder;
import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.data.JavaScope;
import eu.fasten.core.data.JavaType;

/**
 * Extends {@link ExtendedRevisionJavaCallGraph} to add Maven specific information.
 * 
 * @version $Id$
 */
public class MavenExtendedRevisionJavaCallGraph extends ExtendedRevisionJavaCallGraph
{
    private final Artifact artifact;

    private final boolean remote;

    private final Map<String, Object> metadata = new HashMap<>();

    private List<License> licenses;

    /**
     * Creates {@link ExtendedRevisionJavaCallGraph} with the given builder.
     *
     * @param artifact the {@link Maven} artifact
     * @param builder builder for {@link ExtendedRevisionJavaCallGraph}
     * @param remote true if the call graph might contain remote metadata
     */
    public MavenExtendedRevisionJavaCallGraph(Artifact artifact,
        ExtendedBuilder<EnumMap<JavaScope, Map<String, JavaType>>> builder, boolean remote)
    {
        super(builder);

        this.artifact = artifact;
        this.remote = remote;
    }

    /**
     * Creates {@link ExtendedRevisionJavaCallGraph} with the given json content.
     * 
     * @param artifact the {@link Maven} artifact
     * @param content the json content to parse
     * @param remote true if the call graph might contain remote metadata
     */
    public MavenExtendedRevisionJavaCallGraph(Artifact artifact, InputStream content, boolean remote)
    {
        super(new JSONObject(new JSONTokener(content)));

        this.artifact = artifact;
        this.remote = remote;
    }

    /**
     * @return the Maven artifact
     */
    public Artifact getArtifact()
    {
        return this.artifact;
    }

    /**
     * @return true if the call graph might contain remote metadata
     */
    public boolean isRemote()
    {
        return this.remote;
    }

    /**
     * @return the metadata associated with the package
     */
    public Map<String, Object> getMetadata()
    {
        return this.metadata;
    }

    /**
     * @return the licenses associated with the artifact
     */
    public List<License> getMavenLicenses()
    {
        return this.licenses;
    }

    /**
     * @param licenses the licenses associated with the artifact
     */
    public void setMavenLicenses(List<License> licenses)
    {
        this.licenses = licenses;
    }
}
