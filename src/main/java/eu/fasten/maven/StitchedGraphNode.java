/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
