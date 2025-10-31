package io.modelcontextprotocol.jenkins;

import hudson.Util;
import hudson.util.Secret;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

public class McpxCliInstaller {

    public void downloadAndInstall(String downloadUrl, String targetPath, String username, Secret password) throws IOException {
        if (Util.fixEmptyAndTrim(downloadUrl) == null) {
            throw new IllegalArgumentException("Download URL is required");
        }
        if (Util.fixEmptyAndTrim(targetPath) == null) {
            throw new IllegalArgumentException("Target path is required");
        }

        File targetFile = new File(targetPath);
        File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        // Download with timeout
        HttpURLConnection conn = (HttpURLConnection) new URL(downloadUrl).openConnection();
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);
        conn.setRequestMethod("GET");
        conn.setInstanceFollowRedirects(true);
        // Optional Basic Auth for protected download endpoints
        if (Util.fixEmptyAndTrim(username) != null && password != null) {
            String userPass = username + ":" + Secret.toString(password);
            String basic = Base64.getEncoder().encodeToString(userPass.getBytes(StandardCharsets.UTF_8));
            conn.setRequestProperty("Authorization", "Basic " + basic);
        }

        int code = conn.getResponseCode();
        if (code != 200) {
            throw new IOException("Failed to download mcpx-cli: HTTP " + code);
        }

        try (InputStream in = conn.getInputStream();
             FileOutputStream out = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }

        // Make executable on Unix-like systems
        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            try {
                Set<PosixFilePermission> perms = new HashSet<>();
                perms.add(PosixFilePermission.OWNER_READ);
                perms.add(PosixFilePermission.OWNER_WRITE);
                perms.add(PosixFilePermission.OWNER_EXECUTE);
                perms.add(PosixFilePermission.GROUP_READ);
                perms.add(PosixFilePermission.GROUP_EXECUTE);
                perms.add(PosixFilePermission.OTHERS_READ);
                perms.add(PosixFilePermission.OTHERS_EXECUTE);
                Files.setPosixFilePermissions(targetFile.toPath(), perms);
            } catch (Exception e) {
                // Ignore permission errors
            }
        }
    }

    public boolean isInstalled(String cliPath) {
        if (Util.fixEmptyAndTrim(cliPath) == null) {
            return false;
        }
        File file = new File(cliPath);
        return file.exists() && file.canExecute();
    }
}
