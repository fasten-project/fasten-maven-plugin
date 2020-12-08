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

import java.io.File;
import java.util.Map;

import eu.fasten.core.data.ExtendedBuilder;
import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.JavaScope;
import eu.fasten.core.data.JavaType;

/**
 * Extends {@link ExtendedRevisionJavaCallGraph} to add Maven specifc information.
 * 
 * @version $Id$
 */
public class MavenExtendedRevisionJavaCallGraph extends ExtendedRevisionJavaCallGraph
{
    private File graphFile;

    /**
     * Creates {@link ExtendedRevisionJavaCallGraph} with the given builder.
     *
     * @param artifact the artifact from which the graph was built
     * @param builder builder for {@link ExtendedRevisionJavaCallGraph}
     */
    public MavenExtendedRevisionJavaCallGraph(File graphFile,
        ExtendedBuilder<Map<JavaScope, Map<FastenURI, JavaType>>> builder)
    {
        super(builder);

        this.graphFile = graphFile;
    }

    /**
     * @return the artifact from which the graph was built
     */
    public File getGraphFile()
    {
        return this.graphFile;
    }
}
