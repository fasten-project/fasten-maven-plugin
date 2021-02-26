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

import eu.fasten.core.data.Constants;
import eu.fasten.core.data.JavaNode;
import eu.fasten.core.data.JavaScope;
import eu.fasten.core.utils.FastenUriUtils;

/**
 * A {@link JavaNode} with a few additional associated information it the context of {@link StitchedGraph}.
 * 
 * @version $Id$
 */
public class StitchedGraphNode
{
    private final long globalId;

    private final JavaScope scope;

    private final JavaNode localNode;

    private final MavenResolvedCallGraph packageRCG;

    public StitchedGraphNode(long globalId, JavaScope scope, JavaNode node, MavenResolvedCallGraph packageRCG)
    {
        this.globalId = globalId;
        this.scope = scope;
        this.localNode = node;
        this.packageRCG = packageRCG;
    }

    /**
     * @return the globalId
     */
    public long getGlobalId()
    {
        return this.globalId;
    }

    /**
     * @return the scope
     */
    public JavaScope getScope()
    {
        return this.scope;
    }

    /**
     * @return the local node
     */
    public JavaNode getLocalNode()
    {
        return this.localNode;
    }

    /**
     * @return the package resolved call graph
     */
    public MavenResolvedCallGraph getPackageRCG()
    {
        return this.packageRCG;
    }

    public String getFullURI()
    {
        // FIXME: not sure what's best for external type
        if (this.scope == JavaScope.externalTypes) {
            return this.localNode.getUri().toString();
        } else {
            return FastenUriUtils.generateFullFastenUri(Constants.mvnForge, this.packageRCG.getGraph().product,
                this.packageRCG.getGraph().version, this.localNode.getUri().toString());
        }
    }
}
