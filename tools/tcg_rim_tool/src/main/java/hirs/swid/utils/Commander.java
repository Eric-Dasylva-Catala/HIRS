package hirs.swid.utils;

import com.beust.jcommander.Parameter;
import hirs.swid.SwidTagConstants;

/**
 * Commander is a class that handles the command line arguments for the SWID
 * Tags gateway by implementing the JCommander package.
 */
public class Commander {

    @Parameter(names = {"-h", "--help"}, help = true, description = "Print this help text.")
    private boolean help;
    @Parameter(names = {"-c", "--create \"base\""}, order = 0,
            description = "The type of RIM to create. A base RIM will be created by default.")
    private String createType = "";
    @Parameter(names = {"-a", "--attributes <path>"}, order = 1,
            description = "The configuration file holding attributes "
            + "to populate the base RIM with.")
    private String attributesFile = "";
    @Parameter(names = {"-o", "--out <path>"}, order = 2,
            description = "The file to write the RIM out to. "
            + "The RIM will be written to stdout by default.")
    private String outFile = "";
    @Parameter(names = {"-v", "--verify <path>"}, order = 3,
            description = "Specify a RIM file to verify.")
    private String verifyFile = "";
    @Parameter(names = {"-t", "--truststore <path>"}, order = 4,
            description = "The truststore to sign the base RIM created "
            + "or to validate the signed base RIM.")
    private String truststoreFile = "";
    @Parameter(names = {"-k", "--privateKeyFile <path>"}, order = 5,
            description = "The private key used to sign the base RIM created by this tool.")
    private String privateKeyFile = "";
    @Parameter(names = {"-p", "--publicCertificate <path>"}, order = 6,
            description = "The public key certificate to embed in the base RIM created by "
            + "this tool.")
    private String publicCertificate = "";
    @Parameter(names = {"-l", "--rimel <path>"}, order = 7,
            description = "The TCG eventlog file to use as a support RIM.")
    private String rimEventLog = "";

    public boolean isHelp() {
        return help;
    }

    public String getCreateType() {
        return createType;
    }

    public String getAttributesFile() {
        return attributesFile;
    }

    public String getOutFile() {
        return outFile;
    }

    public String getVerifyFile() {
        return verifyFile;
    }

    public String getTruststoreFile() { return truststoreFile; }

    public String getPrivateKeyFile() {
        return privateKeyFile;
    }

    public String getPublicCertificate() {
        return publicCertificate;
    }

    public String getRimEventLog() { return rimEventLog; }

    public String printHelpExamples() {
        StringBuilder sb = new StringBuilder();
        sb.append("Create a base RIM using the values in attributes.json; " +
                "sign it with the default keystore, alias, and password;\n");
        sb.append("and write the data to base_rim.swidtag:\n\n");
        sb.append("\t\t-c base -a attributes.json -l support_rim.bin -o base_rim.swidtag\n\n\n");
        sb.append("Create a base RIM using the default attribute values; ");
        sb.append("sign it using privateKey.pem; embed cert.pem in the signature block; ");
        sb.append("and write the data to console output:\n\n");
        sb.append("\t\t-c base -l support_rim.bin -k privateKey.pem -p cert.pem\n\n\n");
        sb.append("Validate a base RIM using an external support RIM to override the ");
        sb.append("payload file:\n\n");
        sb.append("\t\t-v base_rim.swidtag -l support_rim.bin\n\n\n");
        sb.append("Validate a base RIM with its own payload file and a PEM truststore ");
        sb.append("containing the signing cert:\n\n");
        sb.append("\t\t-v base_rim.swidtag -t ca.crt\n\n\n");

        return sb.toString();
    }
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Creating: " + this.getCreateType() + System.lineSeparator());
        sb.append("Using attributes file: " + this.getAttributesFile() + System.lineSeparator());
        sb.append("Write to: " + this.getOutFile() + System.lineSeparator());
        sb.append("Verify file: " + this.getVerifyFile() + System.lineSeparator());
        if (!this.getTruststoreFile().isEmpty()) {
            sb.append("Truststore file: " + this.getTruststoreFile() + System.lineSeparator());
        } else if (!this.getPrivateKeyFile().isEmpty() &&
                    !this.getPublicCertificate().isEmpty()) {
            sb.append("Private key file: " + this.getPrivateKeyFile() + System.lineSeparator());
            sb.append("Public certificate: " + this.getPublicCertificate() + System.lineSeparator());
        } else {
            sb.append("Truststore file: default (" + SwidTagConstants.DEFAULT_KEYSTORE_FILE + ")"
                    + System.lineSeparator());
        }
        sb.append("Event log support RIM: " + this.getRimEventLog() + System.lineSeparator());
        return sb.toString();
    }
}
