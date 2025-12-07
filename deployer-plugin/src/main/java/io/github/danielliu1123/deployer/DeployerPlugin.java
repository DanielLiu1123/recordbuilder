package io.github.danielliu1123.deployer;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * A Gradle plugin that simplifies publishing artifacts to Maven repositories.
 *
 * @author Freeman
 */
public class DeployerPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        // Create extension
        var extension = project.getExtensions().create("deploy", DeployerPluginExtension.class, project);

        project.getTasks()
                .register("deploy", DeployTask.class, project, extension)
                .configure(task -> {
                    task.setGroup("publishing");
                    task.setDescription("Deploys artifacts to the configured Maven repository.");
                });
    }
}
