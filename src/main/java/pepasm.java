import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

public class pepasm {

    StringBuilder builder;
    String token;
    int lineNumber;

    // Each command maps to a single character
    // Address mode is 0 or 1, for i or f
    // Ex: LDBA 0x0000,i is D0, LDBA 0x0000,d is D1

    //STBA, LDBA, STWA, LDWA, ANDA, ASLA, ASRA, STOP, CPBA, BRNE.
    public static final Map<String, Function<pepasm, Integer>> MAPPING = Map.ofEntries(
            Map.entry("CPBA", (p) -> addressableCommand(p, "B", List.of('i', 'd'))),
            Map.entry("LDWA", (p) -> addressableCommand(p, "C", List.of('i', 'd'))),
            Map.entry("LDBA", (p) -> addressableCommand(p, "D", List.of('i', 'd'))),
            Map.entry("STWA", (p) -> addressableCommand(p, "E", List.of('d'))),
            Map.entry("STBA", (p) -> addressableCommand(p, "F", List.of('d'))),
            Map.entry("ADDA", (p) -> addressableCommand(p, "6", List.of('i', 'd'))),
            Map.entry("SUBA", (p) -> addressableCommand(p, "7", List.of('i', 'd'))),
            Map.entry("ANDA", (p) -> addressableCommand(p, "8", List.of('i', 'd'))),
            Map.entry("BRNE", (p) -> addressableCommand(p, "1A", List.of('i'))),
            Map.entry("BREQ", (p) -> addressableCommand(p, "18", List.of('i'))),
            Map.entry("ASLA", (p) -> command(p, "0A")),
            Map.entry("ASRA", (p) -> command(p, "0C")),
            Map.entry("STOP", (p) -> command(p, "00")),
            Map.entry(".END", (p) -> command(p, "zz"))
    );

    public final HashMap<String, String> LABELS = new HashMap<>();

    public static void main(String[] args) {
        pepasm p = new pepasm();
        for (String path : args) {
            p.assemble(path);
        }
    }

    public void assemble(String fileToLoad) {

        // Clear labels for this file
        LABELS.clear();

        // Create directory to load file from
        String projectDirectory = System.getProperty("user.dir");
        String fileDirectory = projectDirectory + '\\' + fileToLoad;

        // Create file with file directory
        File file = new File(fileDirectory);
        if (!file.exists()) {
            System.out.println("Could not find file at path " + fileDirectory);
            return;
        }

        try (Scanner scanner = new Scanner(file)){

            this.builder = new StringBuilder();

            this.lineNumber = 0;
            while (scanner.hasNextLine()) {
                this.lineNumber++;

                this.token = scanner.nextLine().strip();

                // Check for labels
                int labelEnd = this.token.indexOf(':');
                if (labelEnd != -1) {
                    String label = this.token.substring(0, labelEnd);
                    // The address of a label is the hex value of the lineNumber minus one * 3
                    LABELS.put(label, Integer.toHexString((this.lineNumber - 1) * 3));

                    // Remove label from line
                    this.token = this.token.substring(labelEnd + 1);
                    this.token = this.token.stripLeading();
                }

                // Command is always the first four characters
                if (this.token.isEmpty() || this.token.length() < 4) continue;
                String command = token.substring(0,4);

                // If command doesn't exist, tell user
                if (!MAPPING.containsKey(command)) {
                    System.out.println("Unknown command " + command);
                    return;
                }
                // translate code and write to string builder, if has an error, return and do not write to file.
                //boolean hasError = commandMapping.get(command).translateCode(builder, token, lineNumber);
                int result = MAPPING.get(command).apply(this);
                if (result != 0) return;
            }

            // Get directory and change to pepo
            int extension = fileToLoad.lastIndexOf('.');
            String outputFileName = fileToLoad.substring(0, extension);
            outputFileName += ".pepo";
            String outputDirectory = projectDirectory + '\\' + outputFileName;

            // Write to output file
            File outputFile = new File(outputDirectory);
            try (PrintWriter writer = new PrintWriter(outputFile, StandardCharsets.UTF_8);) {
                writer.write(this.builder.toString());
                System.out.println("Success! Wrote out to " + outputDirectory);
            } catch (IOException e) {
                System.out.println("Error creating file ");
            }
        } catch (FileNotFoundException e) {
            System.out.println("Could not find file at path " + fileDirectory);
        }
    }

    public static int addressableCommand(pepasm p, String token, List<Character> allowedAddressingModes) {

        int endAddressIndex = p.token.indexOf(",");

        // Strip from before the comma and after the command to get the address
        String addressCode = p.token.substring(4, endAddressIndex);
        addressCode = addressCode.strip();

        // Check if it is a label first
        if (p.LABELS.containsKey(addressCode)) {
            // Set code to the label value
            addressCode = p.LABELS.get(addressCode);
        }

        // Might start with 0x, like 0x0000, machine code doesn't need this, so remove
        if (addressCode.startsWith("0x")) {
            addressCode = addressCode.substring(2);
        }

        if (addressCode.length() > 4) {
            System.out.println("Invalid Address length for " + addressCode + " at line " + p.lineNumber);
            return 1;
        }

        // Ensure addressCode has length of 4
        while (addressCode.length() < 4) {
            addressCode = "0" + addressCode;
        }

        String formattedAddress = addressCode.substring(0, 2) + " " + addressCode.substring(2);

        // Strip from one after the comma and strip leading to get addressing mode
        String addressType = p.token.substring(endAddressIndex + 1);
        addressType = addressType.stripLeading();

        boolean direct = false;

        if (addressType.isEmpty()) {
            System.out.println("No Addressing mode for " + addressCode + " at line " + p.lineNumber);
            return 1;
        }

        if (addressType.charAt(0) == 'd') {
            direct = true;
        } else if (addressType.charAt(0) != 'i') {
            System.out.println("Invalid Addressing Mode " + addressType + " at line " + p.lineNumber);
            return 1;
        }

        if (!allowedAddressingModes.contains(addressType.charAt(0))) {
            System.out.println("Illegal addressing mode at line " + p.lineNumber);
            return 1;
        }

        // If token is only 1 character, then it needs an addressing mode
        if (token.length() < 2) {
            token = token + (direct ? "1" : "0");
        }

        p.builder.append(token)
                .append(" ")
                .append(formattedAddress)
                .append(" ");

        return 0;
    }

    public static int command(pepasm p, String token) {
        p.builder.append(token);
        p.builder.append(" ");
        return 0;
    }
}
