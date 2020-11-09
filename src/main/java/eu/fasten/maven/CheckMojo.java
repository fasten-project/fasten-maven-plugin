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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import eu.fasten.analyzer.javacgopal.data.CallGraphConstructor;
import eu.fasten.analyzer.javacgopal.data.PartialCallGraph;
import eu.fasten.analyzer.javacgopal.data.exceptions.OPALException;
import eu.fasten.core.data.ExtendedRevisionCallGraph;
import eu.fasten.core.merge.LocalMerger;

/**
 * Build a call graph of the module and its dependencies.
 *
 * @version $Id: 982ced7f89e6c39126d28b2f9e5fcac365250288 $
 */
@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY, requiresDependencyResolution = ResolutionScope.RUNTIME, requiresProject = true, threadSafe = true)
public class CheckMojo extends AbstractMojo
{
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "target/call-graphs/")
    private File outputDirectory;

    @Parameter(defaultValue = "CHA")
    private String genAlgorithm;

    @Parameter(defaultValue = "CHA")
    private String mergeAlgorithm;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        // Only JAR packages are supported right now
        if (!this.project.getPackaging().equals("jar")) {
            getLog().warn("Only project with packaging JAR are supported. Skipping.");

            return;
        }

        getLog().info("Generating local call graph of the project.");

        // Build project call graph
        File projectCallGraphFile = new File(this.outputDirectory, "project.json");
        ExtendedRevisionCallGraph projectCG;
        try {
            projectCG =
                buildCallGraph(new File(this.project.getBuild().getOutputDirectory()), projectCallGraphFile, "project");
        } catch (OPALException e) {
            throw new MojoExecutionException(
                "Failed to build a call graph for directory [" + this.project.getBuild().getOutputDirectory() + "]", e);
        }

        // Build/Get dependencies call graphs
        List<ExtendedRevisionCallGraph> dependencies = new ArrayList<>();
        for (Artifact artifact : this.project.getArtifacts()) {
            getLog().info("Generating call graphs for dependency [" + artifact + "].");
            try {
                dependencies.add(getCallGraph(artifact));
            } catch (Exception e) {
                getLog().warn("Failed to generate a call graph for artifact [" + artifact + "]: "
                    + ExceptionUtils.getRootCauseMessage(e) + "");
            }
        }

        // Merge call graphs
        getLog().info("Merging call graphs.");

        LocalMerger merger = new LocalMerger(projectCG, dependencies);
        ExtendedRevisionCallGraph mergedCG = merger.mergeWithCHA();

        File mergeCallGraphFile = new File(outputDirectory, "merge.json");
        try {
            FileUtils.write(mergeCallGraphFile, mergedCG.toJSON().toString(4), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to serialize the merged call graph", e);
        }
    }

    private ExtendedRevisionCallGraph getCallGraph(Artifact artifact) throws IOException, OPALException
    {
        if (!artifact.getFile().getName().endsWith(".jar")) {
            throw new IOException("Unsupported type ([" + artifact.getType() + "]) for artifact [" + artifact + "]");
        }

        File outputFile = new File(this.outputDirectory,
            artifact.getGroupId() + '/' + artifact.getArtifactId() + '/' + artifact.getFile().getName() + ".json");

        // TODO: Try to find it on the FASTEN server

        // Build it locally

        return buildCallGraph(artifact.getFile(), outputFile, artifact.toString());
    }

    private ExtendedRevisionCallGraph buildCallGraph(File file, File outputFile, String product) throws OPALException
    {
        PartialCallGraph input = new PartialCallGraph(new CallGraphConstructor(file, null, this.genAlgorithm));
        ExtendedRevisionCallGraph cg = ExtendedRevisionCallGraph.extendedBuilder().graph(input.getGraph())
            .product(product).version("").timestamp(0).cgGenerator("").forge("")
            .classHierarchy(input.getClassHierarchy()).nodeCount(input.getNodeCount()).build();

        // Remember the call graph in a file

        // Make sure the parent folder exist
        outputFile.getParentFile().mkdirs();

        try {
            FileUtils.write(outputFile, cg.toJSON().toString(4), StandardCharsets.UTF_8);
        } catch (Exception e) {
            getLog().warn("Failed to serialize the call graph for artifact [" + file + "]: "
                + ExceptionUtils.getRootCauseMessage(e));
        }

        return cg;
    }
}
