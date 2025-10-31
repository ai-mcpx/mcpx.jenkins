package io.modelcontextprotocol.jenkins;

import hudson.Util;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class McpxCliClient {
    private final String cliPath;

    public McpxCliClient(String cliPath) {
        String p = Util.fixEmptyAndTrim(cliPath);
        if (p == null) {
            this.cliPath = "mcpx-cli";
        } else {
            this.cliPath = expandHome(p);
        }
    }

    public String getVersion() throws IOException, InterruptedException {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add(cliPath);
        args.add("--version");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int exitCode = execute(args, output, null);
        if (exitCode != 0) {
            throw new IOException("mcpx-cli --version failed with exit code " + exitCode);
        }
        return output.toString(StandardCharsets.UTF_8).trim();
    }

    public String listServers(String baseUrl) throws IOException, InterruptedException {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add(cliPath);
        if (baseUrl != null && !baseUrl.isEmpty()) {
            args.add("--base-url=" + baseUrl);
        }
        args.add("servers");
        args.add("--json");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int exitCode = execute(args, output, null);
        if (exitCode != 0) {
            throw new IOException("mcpx-cli servers failed with exit code " + exitCode);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    public String getServerDetails(String baseUrl, String serverName) throws IOException, InterruptedException {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add(cliPath);
        if (baseUrl != null && !baseUrl.isEmpty()) {
            args.add("--base-url=" + baseUrl);
        }
        args.add("server");
        args.add(serverName);
        args.add("--json");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int exitCode = execute(args, output, null);
        if (exitCode != 0) {
            throw new IOException("mcpx-cli server failed with exit code " + exitCode);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    public void login(String baseUrl, String method) throws IOException, InterruptedException {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add(cliPath);
        if (baseUrl != null && !baseUrl.isEmpty()) {
            args.add("--base-url=" + baseUrl);
        }
        args.add("login");
        args.add("--method");
        args.add(method != null ? method : "anonymous");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int exitCode = execute(args, output, null);
        if (exitCode != 0) {
            throw new IOException("mcpx-cli login failed with exit code " + exitCode + ": " + output.toString(StandardCharsets.UTF_8));
        }
    }

    private static String expandHome(String path) {
        if (path != null && path.startsWith("~/")) {
            String home = System.getProperty("user.home");
            if (home != null && !home.isEmpty()) {
                return home + path.substring(1);
            }
        }
        return path;
    }

    private int execute(ArgumentListBuilder args, ByteArrayOutputStream output, TaskListener listener) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(args.toList());
        Process proc = pb.start();

        // Capture stdout
        Thread outputThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                    if (listener != null) {
                        listener.getLogger().println(line);
                    }
                }
            } catch (IOException e) {
                // Ignore
            }
        });
        outputThread.start();

        // Capture stderr
        Thread errorThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (listener != null) {
                        listener.getLogger().println("ERROR: " + line);
                    }
                }
            } catch (IOException e) {
                // Ignore
            }
        });
        errorThread.start();

        int exitCode = proc.waitFor();
        outputThread.join();
        errorThread.join();

        return exitCode;
    }
}
