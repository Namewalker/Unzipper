package dev.unzipper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class UnzipperPlugin extends JavaPlugin implements CommandExecutor {

    @Override
    public void onEnable() {
        getCommand("unzip").setExecutor(this);
    }

    @Override
    public boolean onCommand(final CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("You must be an operator to use this command.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("Usage: /unzip <file>");
            return true;
        }

        final Path serverRoot = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
        String requestedName = String.join(" ", args).trim();
        final String archiveName = requestedName.toLowerCase(Locale.ROOT).endsWith(".zip")
            ? requestedName : requestedName + ".zip";

        final Path archive = serverRoot.resolve(archiveName).normalize();
        if (!archive.startsWith(serverRoot) || archive.equals(serverRoot)) {
            sender.sendMessage("The ZIP file must be inside the server root.");
            return true;
        }
        if (!Files.isRegularFile(archive) || !Files.isReadable(archive)) {
            sender.sendMessage("ZIP file not found or unreadable: " + archiveName);
            return true;
        }

        sender.sendMessage("Extracting " + archiveName + " into the server root...");
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            String result;
            try {
                extractArchive(serverRoot, archive);
                result = "Finished extracting " + archiveName + ".";
            } catch (IOException | RuntimeException exception) {
                getLogger().warning("Could not extract " + archiveName + ": " + exception.getMessage());
                result = "Could not extract " + archiveName + ": " + exception.getMessage();
            }
            final String message = result;
            Bukkit.getScheduler().runTask(this, () -> sender.sendMessage(message));
        });
        return true;
    }

    private void extractArchive(Path serverRoot, Path archive) throws IOException {
        Path realRoot = serverRoot.toRealPath();
        Path realArchive = archive.toRealPath();
        if (!realArchive.startsWith(realRoot)) {
            throw new IOException("ZIP file must resolve inside the server root.");
        }
        try (InputStream fileInput = new BufferedInputStream(Files.newInputStream(realArchive));
             ZipInputStream zipInput = new ZipInputStream(fileInput)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zipInput.getNextEntry()) != null) {
                String entryName = entry.getName().replace('\\', '/');
                Path relativePath = Paths.get(entryName);
                if (relativePath.isAbsolute()) {
                    throw new IOException("Archive contains an absolute path.");
                }

                Path destination = realRoot.resolve(relativePath).normalize();
                if (!destination.startsWith(realRoot) || destination.equals(realRoot)) {
                    throw new IOException("Archive entry escapes the server root: " + entryName);
                }

                ensureSafeDirectories(realRoot, destination.getParent());
                if (entry.isDirectory()) {
                    ensureSafeDirectories(realRoot, destination);
                } else {
                    if (Files.isSymbolicLink(destination)) {
                        throw new IOException("Archive entry targets a symbolic link: " + entryName);
                    }
                    try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(destination,
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE))) {
                        int bytesRead;
                        while ((bytesRead = zipInput.read(buffer)) != -1) {
                            output.write(buffer, 0, bytesRead);
                        }
                    }
                }
                zipInput.closeEntry();
            }
        }
    }

    private void ensureSafeDirectories(Path root, Path directory) throws IOException {
        Path current = root;
        Path relativeDirectory = root.relativize(directory);
        for (Path component : relativeDirectory) {
            current = current.resolve(component);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Unsafe extraction path: " + current);
                }
            } else {
                Files.createDirectory(current);
            }
        }
    }
}