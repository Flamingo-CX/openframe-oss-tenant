package com.openframe.sdk.tacticalrmm;

import java.util.regex.Pattern;

public class RegistrationSecretParser {

    /**
     * Extracts the registration secret from a Tactical RMM install command.
     *
     * Expected input example:
     * tacticalagent-v2.9.0-windows-amd64.exe /VERYSILENT /SUPPRESSMSGBOXES && ping 127.0.0.1 -n 5 && \"C:\\Program Files\\TacticalAgent\\tacticalrmm.exe\" -m install --api http://tactical-nginx.integrated-tools.svc.cluster.local:8000 --client-id 1 --site-id 1 --agent-type server --auth 15ec17179f1c4db7082386ed631d4bbb2c687b9ad9673d777220d781dd20e6d9
     *
     * Output:
     * 15ec17179f1c4db7082386ed631d4bbb2c687b9ad9673d777220d781dd20e6d9
     *
     * @param command full command string
     * @return extracted secret value following the --auth flag, or null if not found
     */
    public static String parse(String command) {
        if (command == null || command.isBlank()) {
            return "";
        }

        // Case-insensitive search for --auth followed by a value (optionally quoted)
        // Captures non-space token excluding surrounding quotes if present
        Pattern pattern = java.util.regex.Pattern.compile(
                "(?i)--auth\\s+([\"']?)([^\"'\s]+)\\1");
        java.util.regex.Matcher matcher = pattern.matcher(command);
        if (matcher.find()) {
            return matcher.group(2);
        }

        // Fallback: simpler pattern without quotes, last token after --auth
        pattern = Pattern.compile("(?i)--auth\\s+([^\\s]+)");
        matcher = pattern.matcher(command);
        if (matcher.find()) {
            String value = matcher.group(1);
            // Trim possible surrounding quotes just in case
            if ((value.startsWith("\"") && value.endsWith("\"")) ||
                (value.startsWith("'") && value.endsWith("'"))) {
                return value.substring(1, value.length() - 1);
            }
            return value;
        }

        return "";
    }

}
