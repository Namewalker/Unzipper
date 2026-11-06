# Unzipper

A Spigot 1.12.2 plugin that lets server operators extract a ZIP archive from
the server root directory.

## Usage

Place the plugin JAR in `plugins/`, and place the ZIP file in the server root
(the directory containing `plugins/`). Then run:

```text
/unzip world backup
```

The command looks for `world backup.zip`; the `.zip` suffix is optional. Paths
are relative to the server root, so a file in a subdirectory can be selected
with a relative path. Archive contents are extracted into the server root.
Existing files with matching names are overwritten.

Extraction runs asynchronously. Keep the ZIP archive in place until the
success or failure message appears. Only operators can use the command.