package de.luckymcdev.foundryengine.buildsrc;

import org.gradle.api.Project;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Reads {@code <project>/.foundryengine/instance.properties}, written by the FoundryEngine IntelliJ plugin.
 * Read through a file-contents provider so it is tracked by the configuration cache.
 */
record FoundryLocalConfig(String modsPath, List<String> modJars, String foundryDir, List<String> libraryJars) {

    private static final String PATH = ".foundryengine/instance.properties";

    static FoundryLocalConfig read(Project project) {
        byte[] bytes = project.getProviders()
                .fileContents(project.getLayout().getProjectDirectory().file(PATH))
                .getAsBytes()
                .getOrNull();
        if (bytes == null) {
            return new FoundryLocalConfig(null, null, null, null);
        }

        Properties props = new Properties();
        try {
            props.load(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            project.getLogger().warn("Failed to read {}: {}", PATH, e.getMessage());
            return new FoundryLocalConfig(null, null, null, null);
        }

        String foundryDir = blankToNull(props.getProperty("foundryDir"));
        if (foundryDir == null) {
            String oldBundles = blankToNull(props.getProperty("bundlesDir"));
            if (oldBundles != null) {
                if (oldBundles.endsWith("/bundles") || oldBundles.endsWith("\\bundles")) {
                    foundryDir = oldBundles.substring(0, oldBundles.length() - 8);
                } else {
                    foundryDir = oldBundles;
                }
            }
        }

        return new FoundryLocalConfig(
                blankToNull(props.getProperty("modsPath")),
                readList(props, "modJars"),
                foundryDir,
                readList(props, "libraryJars")
        );
    }

    private static List<String> readList(Properties props, String key) {
        String prop = props.getProperty(key);
        if (prop != null) {
            List<String> list = new ArrayList<>();
            for (String s : prop.split(",")) {
                if (!s.isBlank()) {
                    list.add(s.trim());
                }
            }
            return list;
        }
        return null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}