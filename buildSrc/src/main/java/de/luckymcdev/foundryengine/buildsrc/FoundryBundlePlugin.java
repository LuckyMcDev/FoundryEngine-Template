package de.luckymcdev.foundryengine.buildsrc;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.Copy;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.List;

public class FoundryBundlePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        BundleMetadata metadata = readBundleMetadata(project);
        project.setVersion(metadata.version());
        project.getExtensions().add("foundryBundle", metadata);

        FoundryDeployExtension deploy = project.getExtensions().create("foundryDeploy", FoundryDeployExtension.class);

        // Written by the IntelliJ plugin (FoundryEngine tool window).
        FoundryLocalConfig local = FoundryLocalConfig.read(project);
        if (local.foundryDir() != null) {
            deploy.setBundlesDir(new File(local.foundryDir(), "bundles").getAbsolutePath());
        }

        registerModsDependency(project, local);
        registerLibrariesDependency(project, local);
        registerBundleTasks(project, metadata);
    }

    private BundleMetadata readBundleMetadata(Project project) {
        File resourcesDir = project.file("src/main/resources");
        File[] tomlFiles = resourcesDir.listFiles((dir, name) -> name.endsWith(".bundles.toml"));

        if (tomlFiles == null || tomlFiles.length == 0) {
            throw new GradleException("No *.bundles.toml file found in " + resourcesDir);
        }
        if (tomlFiles.length > 1) {
            throw new GradleException("Multiple *.bundles.toml files found in " + resourcesDir + ", expected exactly one");
        }

        File tomlFile = tomlFiles[0];
        CommentedConfig config;
        try (Reader reader = new FileReader(tomlFile)) {
            config = TomlFormat.instance().createParser().parse(reader);
        } catch (IOException e) {
            throw new GradleException("Failed to read " + tomlFile, e);
        }

        List<CommentedConfig> bundleList = config.get("bundles");
        if (bundleList == null || bundleList.isEmpty()) {
            throw new GradleException(tomlFile + " has no [[bundles]] entries");
        }
        if (bundleList.size() > 1) {
            throw new GradleException(tomlFile + " must contain exactly one [[bundles]] entry for a bundle project");
        }

        CommentedConfig entry = bundleList.get(0);

        String bundleId = requireString(entry, "bundleId", tomlFile);
        String displayName = requireString(entry, "displayName", tomlFile);
        String version = requireString(entry, "version", tomlFile);

        List<String> authors = Arrays.stream(entry.getOrElse("authors", "").toString().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        String displayURL = entry.get("displayURL");
        String description = entry.get("description");
        List<String> dependencies = entry.getOrElse("dependencies", List.of());

        return new BundleMetadata(bundleId, version, displayName, authors, displayURL, description, dependencies);
    }

    private String requireString(CommentedConfig entry, String key, File tomlFile) {
        String value = entry.get(key);
        if (value == null || value.isBlank()) {
            throw new GradleException(tomlFile + " is missing required field: " + key);
        }
        return value;
    }

    private void registerModsDependency(Project project, FoundryLocalConfig local) {
        if (local.modsPath() == null) {
            return;
        }

        File modsDir = new File(local.modsPath());
        if (!modsDir.isDirectory()) {
            project.getLogger().warn("Configured mods folder does not exist: {}", modsDir);
            return;
        }

        List<String> selected = local.modJars();
        if (selected != null && selected.isEmpty()) {
            return;
        }

        FileTree modJars = project.fileTree(modsDir, tree -> {
            if (selected == null) {
                tree.include("**/*.jar");
            } else {
                selected.forEach(tree::include);
            }
        });
        project.getDependencies().add("implementation", modJars);
    }

    private void registerLibrariesDependency(Project project, FoundryLocalConfig local) {
        if (local.foundryDir() == null || local.libraryJars() == null || local.libraryJars().isEmpty()) {
            return;
        }

        File libraryCacheDir = new File(local.foundryDir(), ".cache/libraries");
        if (!libraryCacheDir.isDirectory()) {
            return;
        }

        for (String relativePath : local.libraryJars()) {
            File jarFile = new File(libraryCacheDir, relativePath);
            if (jarFile.isFile()) {
                project.getDependencies().add("compileOnly", project.files(jarFile));
            } else {
                project.getLogger().warn("Library jar not found in cache: {}", jarFile);
            }
        }
    }

    private void registerBundleTasks(Project project, BundleMetadata metadata) {
        var buildBundle = project.getTasks().register("buildBundle", Copy.class, task -> {
            task.setGroup("build");
            task.setDescription("Bundle equivalent to jar");

            task.from("src/main/groovy", spec -> spec.into("scripts"));
            task.from("src/main/resources");
            task.into(project.getLayout().getBuildDirectory().dir("bundles/" + metadata.folderName()));
        });

        project.getTasks().register("deployBundle", Copy.class, task -> {
            task.setGroup("build");
            task.setDescription("Deploys the bundle to the configured bundles directory");

            task.from(buildBundle);

            task.into(project.provider(() -> {
                FoundryDeployExtension ext = project.getExtensions().getByType(FoundryDeployExtension.class);
                return project.file(ext.getBundlesDir() + "/" + metadata.folderName());
            }));

            task.doFirst(t -> {
                FoundryDeployExtension ext = project.getExtensions().getByType(FoundryDeployExtension.class);
                File deployDir = project.file(ext.getBundlesDir() + "/" + metadata.folderName());
                if (deployDir.exists()) {
                    project.delete(deployDir);
                    task.getLogger().lifecycle("Deleted old bundle at {}", deployDir);
                }
            });
        });
    }
}