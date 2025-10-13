import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class pepasm {

    // Each command maps to a single character
    // Address mode is 0 or 1, for i or f
    // Ex: LDBA 0x0000,i is D0, LDBA 0x0000,d is D1

    //STBA, LDBA, STWA, LDWA, ANDA, ASLA, ASRA, STOP, CPBA, BRNE.
    public static final Map<String, ITranslatable> commandMapping = Map.ofEntries(
            Map.entry("CPBA", new AddressableCommand('B')),
            Map.entry("LDWA", new AddressableCommand('C')),
            Map.entry("LDBA", new AddressableCommand('D')),
            Map.entry("STWA", new AddressableCommand('E')),
            Map.entry("STBA", new AddressableCommand('F')),
            Map.entry("ANDA", new AddressableCommand('8')),
            Map.entry("ASLA", new Command("0A")),
            Map.entry("ASRA", new Command("0C")),
            Map.entry("STOP", new Command("00")),
            Map.entry(".END", new Command("zz")),
            Map.entry("BRNE", new Command("1A")),
            Map.entry("BREQ", new Command("18"))
    );

    public static void main(String[] args) {
        pepasm p = new pepasm();
        p.start(args[0]);
    }

    public void start(String fileToLoad) {

        // Create directory to load file from
        String projectDirectory = System.getProperty("user.dir");
        String fileDirectory = projectDirectory + '\\' + fileToLoad;

        // Create file with file directory
        File file = new File(fileDirectory);
        if (!file.exists()) {
            System.out.println("Could not find file at path " + fileDirectory);
            return;
        }

        try (Scanner scanner = new Scanner(file)) {

            StringBuilder builder = new StringBuilder();

            int lineNumber = 0;
            while (scanner.hasNextLine()) {
                lineNumber++;

                // Command is always the first four characters
                String token = scanner.nextLine();
                String command = token.substring(0,4);

                // If command doesn't exist, tell user
                if (!commandMapping.containsKey(command)) {
                    System.out.println("Unknown command " + command);
                    return;
                }
                // translate code and write to string builder, if has an error, return and do not write to file.
                boolean hasError = commandMapping.get(command).translateCode(builder, token, lineNumber);
                if (hasError) return;
            }

            // Get directory and change to pepo
            int extension = fileToLoad.lastIndexOf('.');
            System.out.println(extension);
            String outputFileName = fileToLoad.substring(0, extension);
            outputFileName += ".pepo";
            String outputDirectory = projectDirectory + '\\' + outputFileName;

            // Write to output file
            File outputFile = new File(outputDirectory);
            try (PrintWriter writer = new PrintWriter(outputFile, StandardCharsets.UTF_8);) {
                writer.write(builder.toString());
                System.out.println("Success! Wrote out to " + outputDirectory);
            } catch (IOException e) {
                System.out.println("Error creating file ");
            }
        } catch (FileNotFoundException e) {
            System.out.println("Could not find file at path " + fileDirectory);
        }
    }

    public interface ITranslatable {
        boolean translateCode(StringBuilder builder, String token, int lineNumber);
    }

    public static class Command implements ITranslatable {
        String machineCodeCommand;

        Command(String machineCodeCommand) {
            this.machineCodeCommand = machineCodeCommand;
        }

        @Override
        public boolean translateCode(StringBuilder builder, String token, int lineNumber) {
            builder.append(machineCodeCommand);
            builder.append(" ");
            return false;
        }
    }

    public record Address(String memoryAddress, boolean direct) {
        public String toObjectCode(Character machineCodeCommand) {
            return machineCodeCommand + (direct ? "1" : "0") + " " + memoryAddress + " ";
        }
    }

    public static class AddressableCommand implements ITranslatable {
        Character machineCodeCommand;

        AddressableCommand(Character machineCodeCommand) {
            this.machineCodeCommand = machineCodeCommand;
        }

        @Override
        public boolean translateCode(StringBuilder builder, String token, int lineNumber) {
            int endAddressIndex = token.indexOf(",");

            // Strip from before the comma and after the command to get the address
            String addressCode = token.substring(4, endAddressIndex);
            addressCode = addressCode.stripLeading();

            // Might start with 0x, like 0x0000, machine code doesn't need this, so remove
            if (addressCode.startsWith("0x")) {
                addressCode = addressCode.substring(2);
            }

            String formattedAddress;
            if (addressCode.length() == 2) {
                formattedAddress = "00 " + addressCode;
            } else if (addressCode.length() == 4) {
                formattedAddress = addressCode.substring(0, 2) + " " + addressCode.substring(2, 4);
            } else {
                System.out.println("Invalid Address length for " + addressCode + " at line " + lineNumber);
                return true;
            }

            // Strip from one after the comma and strip leading to get addressing mode
            String addressType = token.substring(endAddressIndex + 1);
            addressType = addressType.stripLeading();

            boolean direct = false;

            if (addressType.isEmpty()) {
                System.out.println("No Addressing mode for " + addressCode + " at line " + lineNumber);
                return true;
            }

            if (addressType.charAt(0) == 'd') {
                direct = true;
            } else if (addressType.charAt(0) != 'i') {
                System.out.println("Invalid Addressing Mode " + addressType + " at line " + lineNumber);
                return true;
            }

            Address address = new Address(formattedAddress, direct);
            builder.append(address.toObjectCode(machineCodeCommand));

            return false;
        }
    }
}
