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

import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;

/**
 * Wrapp a {@link ExtendedRevisionJavaCallGraph} and add some Maven plugin specific information.
 * 
 * @version $Id$
 */
public class MavenResolvedCallGraph
{
    private final boolean remote;

    private final ExtendedRevisionJavaCallGraph graph;

    /**
     * @param remote true if the call graph originate from a FASTEN server
     * @param graph the actual call graph
     */
    public MavenResolvedCallGraph(boolean remote, ExtendedRevisionJavaCallGraph graph)
    {
        this.remote = remote;
        this.graph = graph;
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
}
