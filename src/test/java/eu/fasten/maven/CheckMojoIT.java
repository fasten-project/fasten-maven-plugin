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

import org.junit.jupiter.api.Disabled;

import com.soebes.itf.jupiter.extension.MavenJupiterExtension;
import com.soebes.itf.jupiter.extension.MavenTest;
import com.soebes.itf.jupiter.maven.MavenExecutionResult;
import com.soebes.itf.jupiter.maven.MavenExecutionResult.ExecutionResult;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Integration tests for {@link CheckMojo}.
 * <p>
 * Only works as part of a Maven build. Cannot be debugged in an IDE.
 * 
 * @version $Id$
 */
@MavenJupiterExtension
public class CheckMojoIT
{
    @MavenTest
    @Disabled
    void test1(MavenExecutionResult result)
    {
        assertSame(ExecutionResult.Successful, result.getResult());
    }
}
