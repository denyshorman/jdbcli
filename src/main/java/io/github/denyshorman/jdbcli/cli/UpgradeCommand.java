package io.github.denyshorman.jdbcli.cli;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.denyshorman.jdbcli.exception.JdbcliException;
import io.github.denyshorman.jdbcli.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.List;

@Command(
        name = "upgrade",
        description = {
                "Upgrade jdbcli itself to the latest release from GitHub.",
                "",
                "Fetches release metadata from GitHub, downloads the new jdbcli.jar,",
                "and replaces the running JAR in-place.",
                "",
                "Only supported when running as: java -jar jdbcli.jar",
                "Restart the process after upgrading to use the new version.",
                "",
                "Use --check to only print the latest available version without downloading.",
        },
        mixinStandardHelpOptions = true
)
public class UpgradeCommand implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(UpgradeCommand.class);
    private static final String RELEASES_API = "https://api.github.com/repos/denyshorman/jdbcli/releases/latest";

    @Option(
            names = "--check",
            description = "Print the latest available release version and exit without downloading.",
            defaultValue = "false"
    )
    boolean checkOnly;

    private static void download(String url, Path target) throws Exception {
        try (var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()) {
            var req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            var res = client.send(req, HttpResponse.BodyHandlers.ofFile(target));

            if (res.statusCode() != 200) {
                throw new JdbcliException("Download failed with HTTP " + res.statusCode());
            }
        }
    }

    private static void replace(Path source, Path target) throws Exception {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException | AccessDeniedException e) {
            var backup = target.resolveSibling(target.getFileName() + ".old");

            Files.deleteIfExists(backup);
            Files.move(target, backup);

            try {
                Files.move(source, target);
            } catch (Exception ex) {
                Files.move(backup, target, StandardCopyOption.REPLACE_EXISTING);
                throw ex;
            }
        }
    }

    private static Path resolveRunningJar() {
        try {
            var source = UpgradeCommand.class.getProtectionDomain().getCodeSource();

            if (source == null || source.getLocation() == null) {
                throw new JdbcliException(
                        "Cannot determine the running JAR path (code source location is null). " +
                                "Upgrade is only supported when running as 'java -jar jdbcli.jar'."
                );
            }

            var path = Path.of(source.getLocation().toURI()).toAbsolutePath();

            if (!path.toString().endsWith(".jar")) {
                throw new JdbcliException(
                        "Upgrade is only supported when running as 'java -jar jdbcli.jar' " +
                                "(detected path: " + path + ")."
                );
            }

            if (!Files.isWritable(path)) {
                throw new JdbcliException("Cannot write to " + path + ". Check file permissions.");
            }

            return path;
        } catch (JdbcliException e) {
            throw e;
        } catch (Exception e) {
            throw new JdbcliException("Cannot determine the running JAR path: " + e.getMessage(), e);
        }
    }

    private static void cleanupOldJar(Path jarPath) {
        var backup = jarPath.resolveSibling(jarPath.getFileName() + ".old");

        try {
            if (Files.deleteIfExists(backup)) {
                LOGGER.debug("Removed leftover upgrade backup: {}", backup);
            }
        } catch (Exception ignored) {
        }
    }

    private static String findAssetUrl(GithubRelease release) {
        return release.assets().stream()
                .filter(a -> "jdbcli.jar".equals(a.name()))
                .map(GithubAsset::browserDownloadUrl)
                .findFirst()
                .orElseThrow(() -> new JdbcliException("No jdbcli.jar asset found in release " + release.tagName()));
    }

    private static GithubRelease fetchLatestRelease() throws Exception {
        try (var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()) {
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(RELEASES_API))
                    .header("Accept", "application/vnd.github+json")
                    .GET()
                    .build();

            var res = client.send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() != 200) {
                throw new JdbcliException("GitHub API returned HTTP " + res.statusCode() + " for " + RELEASES_API);
            }

            return JsonUtil.MAPPER.readValue(res.body(), GithubRelease.class);
        }
    }

    @Override
    public void run() {
        try {
            var release = fetchLatestRelease();

            if (checkOnly) {
                LOGGER.info("Latest release: {}", release.tagName());
                return;
            }

            var downloadUrl = findAssetUrl(release);
            var jarPath = resolveRunningJar();

            cleanupOldJar(jarPath);

            LOGGER.info("Downloading {} ...", release.tagName());

            var tempFile = jarPath.resolveSibling(jarPath.getFileName() + ".tmp");

            try {
                download(downloadUrl, tempFile);
                replace(tempFile, jarPath);
            } catch (Exception e) {
                Files.deleteIfExists(tempFile);
                throw e;
            }

            LOGGER.info("Upgraded to {}. Restart jdbcli to use the new version.", release.tagName());
        } catch (JdbcliException e) {
            throw e;
        } catch (Exception e) {
            throw new JdbcliException("Upgrade failed: " + e.getMessage(), e);
        }
    }

    private record GithubRelease(
            @JsonProperty("tag_name") String tagName,
            List<GithubAsset> assets
    ) {
    }

    private record GithubAsset(
            String name,
            @JsonProperty("browser_download_url") String browserDownloadUrl
    ) {
    }
}
