package org.techbd.service.http;

import java.net.URL;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SandboxHelpers {

    public record VsCodeEditor(String editor, String distro, String hostname) {
    }

    private final VsCodeEditor vsCodeEditor;

    public SandboxHelpers(
            final @Value("${upi.ws.editor:#{null}}") String editor,
            final @Value("${upi.ws.editor.vscode.remote.distro:#{null}}") String distro,
            final @Value("${upi.ws.editor.vscode.remote.hostname:#{null}}") String hostname) {

        // TODO: should we allow setting the editor via cookies or other user-agent
        // means? for now we assume the server controls the editor but it really should
        // be the user-agent.
        this.vsCodeEditor = new VsCodeEditor(editor, distro, hostname);
    }

    public boolean isEditorAvailable() {
        return vsCodeEditor.editor() != null;
    }

    public static String getEditorUrl(VsCodeEditor vsCodeEditor, String absoluteFilePath) {
        if (vsCodeEditor.editor() == null || absoluteFilePath == null || absoluteFilePath.isEmpty()) {
            throw new IllegalArgumentException("Editor and file path must be provided.");
        }

        String url = switch (vsCodeEditor.editor()) {
            case "vscode-wsl" ->
                String.format("vscode://vscode-remote/wsl+%s%s:1", vsCodeEditor.distro(), absoluteFilePath);
            case "vscode-ssh-remote" ->
                String.format("vscode://vscode-remote/ssh-remote+%s%s:1", vsCodeEditor.hostname(), absoluteFilePath);
            case "vscode-windows", "vscode-linux", "vscode-mac" -> String.format("vscode://file%s:1", absoluteFilePath);
            default -> throw new IllegalArgumentException("Unsupported editor: " + vsCodeEditor.editor());
        };

        return url;
    }

    public String getEditorUrl(URL url) {
        // Assuming the URL is already properly formatted
        return getEditorUrl(this.vsCodeEditor, url.toString());
    }

    public String getEditorUrlFromAbsolutePath(String absoluteFilePath) {
        return getEditorUrl(this.vsCodeEditor, absoluteFilePath);
    }
}
