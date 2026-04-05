package io.github.denyshorman.jdbcli.driver;

import io.github.denyshorman.jdbcli.config.DbDescriptor;
import io.github.denyshorman.jdbcli.exception.JdbcliException;
import io.github.denyshorman.jdbcli.util.FileSystemUtil;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public class DriverInstaller {
    private static final Logger LOGGER = LoggerFactory.getLogger(DriverInstaller.class);
    private static final Pattern RELEASE_PATTERN = Pattern.compile("<release>([^<]+)</release>");
    private static final Pattern LATEST_PATTERN = Pattern.compile("<latest>([^<]+)</latest>");

    public static Path install(DbDescriptor db, @Nullable String urlOverride) throws Exception {
        var downloadUrl = urlOverride;

        if (downloadUrl == null) {
            if (db.driver().url() != null) {
                downloadUrl = db.driver().url();
            } else {
                downloadUrl = resolveLatestMavenUrl(db);
            }
        }

        var jarName = downloadUrl.substring(downloadUrl.lastIndexOf('/') + 1);
        var driversDir = FileSystemUtil.getJdbcliHome().resolve("drivers");

        Files.createDirectories(driversDir);

        var targetFile = driversDir.resolve(jarName);

        if (!Files.exists(targetFile)) {
            LOGGER.info("Downloading {} ...", downloadUrl);

            try (var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()) {
                var req = HttpRequest.newBuilder().uri(URI.create(downloadUrl)).GET().build();
                var res = client.send(req, HttpResponse.BodyHandlers.ofFile(targetFile));

                if (res.statusCode() != 200) {
                    Files.deleteIfExists(targetFile);
                    throw new JdbcliException("Download failed with status " + res.statusCode());
                }
            }
        }

        return targetFile;
    }

    public static String resolveLatestMavenUrl(DbDescriptor db) {
        var groupId = db.driver().groupId();
        var artifactId = db.driver().artifactId();

        if (groupId == null || artifactId == null) {
            throw new JdbcliException("Descriptor " + db.id() + " is missing Maven coordinates and url, cannot resolve driver automatically");
        }

        var groupPath = groupId.replace('.', '/');
        var baseUrl = "https://repo1.maven.org/maven2/" + groupPath + "/" + artifactId;
        var metadataUrl = baseUrl + "/maven-metadata.xml";
        var version = db.driver().defaultVersion();

        if (version == null) {
            try (var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()) {
                var req = HttpRequest.newBuilder().uri(URI.create(metadataUrl)).GET().build();
                var res = client.send(req, HttpResponse.BodyHandlers.ofString());

                if (res.statusCode() == 200) {
                    var body = res.body();
                    var releaseMatcher = RELEASE_PATTERN.matcher(body);

                    if (releaseMatcher.find()) {
                        version = releaseMatcher.group(1);
                    } else {
                        var latestMatcher = LATEST_PATTERN.matcher(body);

                        if (latestMatcher.find()) {
                            version = latestMatcher.group(1);
                        }
                    }
                } else {
                    throw new JdbcliException("Failed to fetch maven-metadata.xml from " + metadataUrl + " (HTTP " + res.statusCode() + ")");
                }
            } catch (Exception e) {
                throw new JdbcliException(e.getMessage(), e);
            }
        }

        if (version == null) {
            throw new JdbcliException("Could not determine dynamic version from " + metadataUrl + " and no defaultVersion provided.");
        }

        var classifier = db.driver().classifier();

        var jarName = classifier != null && !classifier.isBlank()
                ? artifactId + "-" + version + "-" + classifier + ".jar"
                : artifactId + "-" + version + ".jar";

        return baseUrl + "/" + version + "/" + jarName;
    }
}
