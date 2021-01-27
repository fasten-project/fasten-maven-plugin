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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

import org.apache.commons.collections4.SetUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eu.fasten.core.data.JavaScope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link CheckMojo}.
 * 
 * @version $Id$
 */
class CheckMojoTest
{
    private CheckMojo mojo = new CheckMojo();

    private Log log = mock(Log.class);

    private File testWorkDir;

    private File projectWorkDir;

    private File projectWorkDirTarget;

    private File projectWorkDirTargetClasses;

    private MavenProject project = new MavenProject();

    @BeforeEach
    void beforeEach()
    {
        this.testWorkDir = new File("target/test-" + new Date().getTime()).getAbsoluteFile();

        this.mojo.setLog(this.log);

        Model model = new Model();
        model.setGroupId("pgroupid");
        model.setArtifactId("partifactid");
        model.setVersion("1.0-SNAPSHOT");
        this.project.setModel(model);
    }

    private void jar(File classFile, File file) throws FileNotFoundException, IOException
    {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            try (JarOutputStream stream = new JarOutputStream(fos, manifest)) {
                ZipEntry entry = new ZipEntry(classFile.getName());
                stream.putNextEntry(entry);
                FileUtils.copyFile(classFile, stream);
            }
        }
    }

    private Artifact artifact(String groupId, String artifactId, String version, File file)
    {
        DefaultArtifact artifact = new DefaultArtifact(groupId, artifactId, version, null, "jar", "", null);
        artifact.setFile(file);

        return artifact;
    }

    @Test
    void testStitching() throws MojoExecutionException, MojoFailureException, IOException, IllegalAccessException
    {
        this.projectWorkDir = new File(this.testWorkDir, "A/");
        this.projectWorkDirTarget = new File(this.projectWorkDir, "target/");
        this.projectWorkDirTargetClasses = new File(this.projectWorkDirTarget, "classes/");
        File dependencyBDir = new File(this.testWorkDir, "B.jar");
        File dependencyCDir = new File(this.testWorkDir, "C.jar");

        FileUtils.copyDirectory(new File("target/test-classes/eu/fasten/maven/a/"),
            new File(this.projectWorkDirTargetClasses, "eu/fasten/maven/a/"));

        jar(new File("target/test-classes/eu/fasten/maven/b/B.class"), dependencyBDir);
        jar(new File("target/test-classes/eu/fasten/maven/c/C.class"), dependencyCDir);

        FieldUtils.writeField(this.mojo, "project", this.project, true);
        FieldUtils.writeField(this.mojo, "outputDirectory", new File(this.projectWorkDir, "target/call-graphs/"), true);
        FieldUtils.writeField(this.mojo, "genAlgorithm", "CHA", true);

        Build build = new Build();
        build.setOutputDirectory(this.projectWorkDirTargetClasses.toString());
        this.project.setBuild(build);

        Set<Artifact> artifacts = new LinkedHashSet<>();
        artifacts.add(artifact("b", "b", "1.0", dependencyBDir));
        artifacts.add(artifact("c", "c", "1.0", dependencyCDir));
        this.project.setArtifacts(artifacts);

        this.mojo.execute();

        List<StitchedGraphNode> nodes = this.mojo.graph.getStichedNodes();

        // All stitched nodes
        assertEquals(
            SetUtils.hashSet(
                "fasten://mvn!project$1.0-SNAPSHOT/eu.fasten.maven.a/A.%3Cinit%3E()%2Fjava.lang%2FVoidType",
                "fasten://mvn!project$1.0-SNAPSHOT/eu.fasten.maven.a/A.m1()%2Fjava.lang%2FVoidType",
                "fasten://mvn!project$1.0-SNAPSHOT/eu.fasten.maven.a/A.m2()%2Fjava.lang%2FVoidType",
                "fasten://mvn!b:b$1.0/eu.fasten.maven.b/B.mB1()%2Fjava.lang%2FVoidType",
                "fasten://mvn!b:b$1.0/eu.fasten.maven.b/B.mBi()%2Fjava.lang%2FVoidType",
                "fasten://mvn!c:c$1.0/eu.fasten.maven.c/C.mC1()%2Fjava.lang%2FVoidType",
                "/eu.fasten.maven.missing/Missing.mMissing()%2Fjava.lang%2FVoidType",
                "/java.lang/Object.%3Cinit%3E()VoidType"),
            nodes.stream().map(node -> node.getFullURI()).collect(Collectors.toSet()));

        // Resolved node URIs
        assertEquals(
            SetUtils.hashSet(
                "fasten://mvn!project$1.0-SNAPSHOT/eu.fasten.maven.a/A.%3Cinit%3E()%2Fjava.lang%2FVoidType",
                "fasten://mvn!project$1.0-SNAPSHOT/eu.fasten.maven.a/A.m1()%2Fjava.lang%2FVoidType",
                "fasten://mvn!project$1.0-SNAPSHOT/eu.fasten.maven.a/A.m2()%2Fjava.lang%2FVoidType",
                "fasten://mvn!b:b$1.0/eu.fasten.maven.b/B.mB1()%2Fjava.lang%2FVoidType",
                "fasten://mvn!b:b$1.0/eu.fasten.maven.b/B.mBi()%2Fjava.lang%2FVoidType",
                "fasten://mvn!c:c$1.0/eu.fasten.maven.c/C.mC1()%2Fjava.lang%2FVoidType"),
            nodes.stream().filter(node -> node.getScope() != JavaScope.externalTypes).map(node -> node.getFullURI())
                .collect(Collectors.toSet()));
    }

    @Test
    void testMetadata() throws MojoExecutionException, MojoFailureException, IOException, IllegalAccessException
    {
        this.projectWorkDir = new File(this.testWorkDir, "PROJECT/");
        this.projectWorkDirTarget = new File(this.projectWorkDir, "target/");
        this.projectWorkDirTargetClasses = new File(this.projectWorkDirTarget, "classes/");

        FileUtils.copyDirectory(new File("target/test-classes/eu/fasten/maven/project/"),
            new File(this.projectWorkDirTargetClasses, "eu/fasten/maven/project/"));

        FieldUtils.writeField(this.mojo, "project", this.project, true);
        FieldUtils.writeField(this.mojo, "outputDirectory", new File(this.projectWorkDir, "target/call-graphs/"), true);
        FieldUtils.writeField(this.mojo, "genAlgorithm", "CHA", true);

        Build build = new Build();
        build.setOutputDirectory(this.projectWorkDirTargetClasses.toString());
        this.project.setBuild(build);

        Set<Artifact> artifacts = new LinkedHashSet<>();
        artifacts.add(artifact("org.apache.commons", "commons-lang3", "3.9", new File("commons-lang3.jar")));
        this.project.setArtifacts(artifacts);

        this.mojo.execute();

        List<StitchedGraphNode> nodes = this.mojo.graph.getStichedNodes();

        // Resolved node URIs
        assertEquals(SetUtils.hashSet(
            "fasten://mvn!pgroupid:partifactid$1.0-SNAPSHOT/eu.fasten.maven.project/ProjectClass.%3Cinit%3E()%2Fjava.lang%2FVoidType",
            "fasten://mvn!org.apache.commons:commons-lang3$3.9/org.apache.commons.lang3/StringUtils.capitalize(%2Fjava.lang%2FString)%2Fjava.lang%2FString",
            "fasten://mvn!pgroupid:partifactid$1.0-SNAPSHOT/eu.fasten.maven.project/ProjectClass.m1()%2Fjava.lang%2FVoidType"),
            nodes.stream().filter(node -> node.getScope() != JavaScope.externalTypes).map(node -> node.getFullURI())
                .collect(Collectors.toSet()));
    }
}
