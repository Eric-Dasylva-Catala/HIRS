package hirs.tpm.eventlog;

import hirs.tpm.eventlog.events.EvCompactHash;
import hirs.tpm.eventlog.events.EvConstants;
import hirs.tpm.eventlog.events.EvEfiBootServicesApp;
import hirs.tpm.eventlog.events.EvEfiGptPartition;
import hirs.tpm.eventlog.events.EvEfiHandoffTable;
import hirs.tpm.eventlog.events.EvEfiSpecIdEvent;
import hirs.tpm.eventlog.events.EvEventTag;
import hirs.tpm.eventlog.events.EvIPL;
import hirs.tpm.eventlog.events.EvNoAction;
import hirs.tpm.eventlog.events.EvPostCode;
import hirs.tpm.eventlog.events.EvSCrtmContents;
import hirs.tpm.eventlog.events.EvSCrtmVersion;
import hirs.tpm.eventlog.uefi.UefiConstants;
import hirs.tpm.eventlog.uefi.UefiFirmware;
import hirs.tpm.eventlog.uefi.UefiVariable;
import hirs.utils.HexUtils;
import org.apache.commons.codec.binary.Hex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Arrays;

/**
 * Class to process a TCG_PCR_EVENT.
 * TCG_PCR_EVENT is used when the Event log uses the SHA1 Format as described in the
 * TCG Platform Firmware Profile (PFP) specification.
 * typedef struct {
 * TCG_PCRINDEX  PCRIndex;  //PCR Index value that either
 * //matches the PCRIndex of a
 * //previous extend operation or
 * //indicates that this Event Log
 * //entry is not associated with
 * //an extend operation
 * TCG_EVENTTYPE EventType; //See Log event types defined in toStrng()
 * TCG_DIGEST    digest;    //The hash of the event data
 * UINT32        EventSize; //Size of the event data
 * UINT8         Event[EventSize];  //The event data
 * } TCG_PCR_EVENT;
 */
public class TpmPcrEvent {
    private static final Logger LOGGER = LogManager.getLogger(TpmPcrEvent.class);
    /**
     * Indent Offset.
     */
    private static final int INDENT_3 = 3;
    /**
     * Log format. SHA1=1, Crytpo agile=2.
     * this can be refactored out
     */
    private int logFormat = -1;
    /**
     * PCR index.
     */
    private int pcrIndex = -1;
    /**
     * Event Type (long).
     */
    private long eventType = 0;
    /**
     * Event digest.
     */
    private byte[] digest = null;
    /**
     * Event data (no content).
     */
    private byte[] event;
    /**
     * Event content data.
     */
    private byte[] eventContent;
    /**
     * TCG Event Log spec version.
     */
    private String version = "Unknown";
    /**
     * TCG Event Log errata version.
     */
    private String errata = "Unknown";
    /**
     * Description for toString support.
     */
    private String description = "";
    /**
     * Length (in bytes) of a pcr.
     */
    private int digestLength = 0;
    /**
     * Event hash for SHA1 event logs.
     */
    private byte[] eventDataSha1hash;
    /**
     * Event hash for Crypto Agile events.
     */
    private byte[] eventDataSha256hash;
    private EvPostCode evPostCode;
    private int eventNumber;
    private boolean error = false;

    /**
     * Constructor.
     *
     * @param is ByteArrayInputStream holding the event
     * @throws IOException when event can't be parsed
     */
    public TpmPcrEvent(final ByteArrayInputStream is) throws IOException {

    }

    /**
     * Sets the digest from a  TCG_PCR_EVENT digest field.
     * This can be SHA1 for older event structures or any algorithm for newer structure.
     *
     * @param digestData cryptographic hash
     */
    protected void setEventDigest(final byte[] digestData) {
        digest = new byte[digestLength];
        System.arraycopy(digestData, 0, digest, 0, this.digestLength);
    }

    /**
     * Retrieves the digest from a TCG Event.
     * This can be SHA1 for older event structures or any algorithm for newer structure.
     *
     * @return the digest data for the event
     */
    public byte[] getEventDigest() {
        byte[] digestCopy = new byte[digestLength];
        System.arraycopy(digest, 0, digestCopy, 0, this.digestLength);
        return digestCopy;
    }

    /**
     * Returns a hex representation of the event digest.
     * @return hex string
     */
    public String getEventDigestStr() {
        return Hex.encodeHexString(this.digest);
    }

    /**
     * Sets the event PCR index value from a TCG Event.
     *
     * @param eventIndex TCG Event PCR Index as defined in the PFP
     */
    protected void setPcrIndex(final byte[] eventIndex) {
        pcrIndex = HexUtils.leReverseInt(eventIndex);
    }

    /**
     * Gets the event index value from a TCG Event.
     *
     * @return eventIndex TCG Event Index as defined in the PFP
     */
    public int getPcrIndex() {
        return pcrIndex;
    }

    /**
     * Sets the Log Format for this TCG Event.
     * 1 = SHA1 Format, 2 = Crypto Agile format.
     *
     * @param format indicates log format.
     */
    protected void setLogFormat(final int format) {
        logFormat = format;
    }

    /**
     * Gets the Log Format for this TCG Event.
     * 1 = SHA1 Format, 2 = Crypto Agile format.
     *
     * @return number representing the format.
     */
    public int getLogFormat() {
        return logFormat;
    }

    /**
     * Sets the EventType.
     *
     * @param type byte array holding the PFP defined log event type
     */
    protected void setEventType(final byte[] type) {
        eventType = new BigInteger(1, HexUtils.leReverseByte(type)).longValue();
    }

    /**
     * Returns the EventType for the Event.
     *
     * @return event type
     */
    public long getEventType() {
        return eventType;
    }

    /**
     * Returns a formatted string of the type for the event.
     * @return a string formatted to be human readable
     */
    public String getEventTypeStr() {
        return String.format("0x%s %s", Long.toHexString(eventType), eventString((int) eventType));
    }

    /**
     * Returns a formatted string of the type for the event minus the byte code.
     * @return a string formatted to be human readable
     */
    public String getEventTypeString() {
        return eventString((int) eventType);
    }

    /**
     * Returns the version of the TCG Log Event specification pertaining to the log.
     * only updated if the event is a TCG_EfiSpecIdEvent.
     *
     * @return specification version
     */
    public String getSpecVersion() {
        return version;
    }

    /**
     * Returns the Errata version of the TCG Log Event specification pertaining to the log.
     * only updated if the event is a TCG_EfiSpecIdEvent).
     *
     * @return Errata version
     */
    public String getSpecErrataVersion() {
        return errata;
    }

    /**
     * Sets the event data after processing.
     *
     * @param eventData The PFP defined event content
     */
    protected void setEventData(final byte[] eventData) {
        event = new byte[eventData.length];
        System.arraycopy(eventData, 0, event, 0, eventData.length);
    }

    /**
     * Gets the Event Data (no event content) for the event.
     * event log format.
     *
     * @return byte array holding the event structure.
     */
    public byte[] getEvent() {
        return java.util.Arrays.copyOf(event, event.length);
    }

    /**
     * Getter for the event number for this event.
     * @return the # for this event
     */
    public int getEventNumber() {
        return eventNumber;
    }

    /**
     * Setter for the event number.
     * @param eventNumber position in the list
     */
    public final void setEventNumber(final int eventNumber) {
        this.eventNumber = eventNumber;
    }

    /**
     * Sets the event content after processing.
     *
     * @param eventData The PFP defined event content
     */
    protected void setEventContent(final byte[] eventData) {
        eventContent = new byte[eventData.length];
        evPostCode = new EvPostCode(eventContent);
        System.arraycopy(eventData, 0, eventContent, 0, eventData.length);
    }

    /**
     * Gets the event Content Data (not the entire event structure).
     *
     * @return byte array holding the events content field
     */
    public byte[] getEventContent() {
        return java.util.Arrays.copyOf(eventContent, eventContent.length);
    }

    /**
     * A getter that parses the content based on the type and returns the proper string
     * value for the content.
     * @return an appended string of human readable data
     */
    public String getEventContentStr() {
        StringBuilder sb = new StringBuilder();

        switch ((int) this.eventType) {
            case EvConstants.EV_PREBOOT_CERT:
                sb.append(" EV_PREBOOT_CERT");
                break;
            case EvConstants.EV_POST_CODE:
                sb.append(new EvPostCode(eventContent).toString());
                break;
            case EvConstants.EV_UNUSED:
                break;
            case EvConstants.EV_NO_ACTION:
                EvNoAction noAction = null;
                try {
                    noAction = new EvNoAction(eventContent);
                    sb.append(noAction.toString());
                    if (noAction.isSpecIDEvent()) {
                        // this should be in the constructor
                        EvEfiSpecIdEvent specID = noAction.getEvEfiSpecIdEvent();
                        version = String.format("%s.%s",
                                specID.getVersionMajor(),
                                specID.getVersionMinor());
                        errata = specID.getErrata();
                    }
                } catch (UnsupportedEncodingException ueEx) {
                    LOGGER.error(ueEx);
                    sb.append(ueEx.toString());
                }
                break;
            case EvConstants.EV_SEPARATOR:
                if (EvPostCode.isAscii(eventContent)
                        && !this.isBlank(eventContent)) {
                    sb.append(String.format("Separator event content = %s",
                            new String(eventContent, StandardCharsets.UTF_8)));
                }
                break;
            case EvConstants.EV_EVENT_TAG:
                sb.append(new EvEventTag(eventContent).toString());
                break;
            case EvConstants.EV_S_CRTM_CONTENTS:
                sb.append(new EvSCrtmContents(eventContent).toString());
                break;
            case EvConstants.EV_S_CRTM_VERSION:
                try {
                    sb.append(new EvSCrtmVersion(eventContent).toString());
                } catch (UnsupportedEncodingException ueEx) {
                    LOGGER.error(ueEx);
                    sb.append(ueEx.toString());
                }
                break;
            case EvConstants.EV_CPU_MICROCODE:
            case EvConstants.EV_PLATFORM_CONFIG_FLAGS:
            case EvConstants.EV_TABLE_OF_DEVICES:
                break;
            case EvConstants.EV_COMPACT_HASH:
                try {
                    sb.append(new EvCompactHash(eventContent).toString());
                } catch (UnsupportedEncodingException ueEx) {
                    LOGGER.error(ueEx);
                    sb.append(ueEx.toString());
                }
                break;
            case EvConstants.EV_IPL:
                sb.append(new EvIPL(eventContent).toString());
                break;
            case EvConstants.EV_IPL_PARTITION_DATA:
            case EvConstants.EV_NONHOST_CODE:
            case EvConstants.EV_NONHOST_CONFIG:
            case EvConstants.EV_NONHOST_INFO:
            case EvConstants.EV_EV_OMIT_BOOT_DEVICES_EVENTS:
            case EvConstants.EV_EFI_EVENT_BASE:
                break;
            case EvConstants.EV_EFI_VARIABLE_DRIVER_CONFIG:
                UefiVariable efiVar = null;
                try {
                    efiVar = new UefiVariable(eventContent);
                    String efiVarDescription = efiVar.toString().replace("\n", "\n   ");
                    sb.append(efiVarDescription.substring(0,
                            efiVarDescription.length() - INDENT_3));
                } catch (CertificateException cEx) {
                    LOGGER.error(cEx);
                    sb.append(cEx.toString());
                } catch (NoSuchAlgorithmException noSaEx) {
                    LOGGER.error(noSaEx);
                    sb.append(noSaEx.toString());
                } catch (IOException ioEx) {
                    LOGGER.error(ioEx);
                    sb.append(ioEx.toString());
                }
                break;
            case EvConstants.EV_EFI_VARIABLE_BOOT:
            case EvConstants.EV_EFI_VARIABLE_AUTHORITY:
                try {
                    sb.append(new UefiVariable(eventContent).toString());
                } catch (CertificateException cEx) {
                    LOGGER.error(cEx);
                    sb.append(cEx.toString());
                } catch (NoSuchAlgorithmException noSaEx) {
                    LOGGER.error(noSaEx);
                    sb.append(noSaEx.toString());
                } catch (IOException ioEx) {
                    LOGGER.error(ioEx);
                    sb.append(ioEx.toString());
                }
                break;
            case EvConstants.EV_EFI_BOOT_SERVICES_APPLICATION:
            case EvConstants.EV_EFI_BOOT_SERVICES_DRIVER: // same as EV_EFI_BOOT_SERVICES_APP
                try {
                    sb.append(new EvEfiBootServicesApp(eventContent).toString());
                } catch (UnsupportedEncodingException ueEx) {
                    LOGGER.error(ueEx);
                    sb.append(ueEx.toString());
                }
                break;
            case EvConstants.EV_EFI_RUNTIME_SERVICES_DRIVER:
                break;
            case EvConstants.EV_EFI_GPT_EVENT:
                try {
                    sb.append(new EvEfiGptPartition(eventContent).toString());
                } catch (UnsupportedEncodingException ueEx) {
                    LOGGER.error(ueEx);
                    sb.append(ueEx.toString());
                }
                break;
            case EvConstants.EV_EFI_ACTION:
            case EvConstants.EV_ACTION:
                sb.append(new String(eventContent, StandardCharsets.UTF_8));
                break;
            case EvConstants.EV_EFI_PLATFORM_FIRMWARE_BLOB:
                sb.append(new UefiFirmware(eventContent).toString());
                break;
            case EvConstants.EV_EFI_HANDOFF_TABLES:
                sb.append(new EvEfiHandoffTable(eventContent).toString());
                break;
            case EvConstants.EV_EFI_HCRTM_EVENT:
                break;
            default:
                sb.append("Unknown Event found\n");
        }

        return cleanTextContent(sb.toString());
    }

    /**
     * Sets the Digest Length.
     * Also the number of bytes expected within each PCR.
     *
     * @param length number of bytes in a PCR for the event.
     */
    public void setDigestLength(final int length) {
        digestLength = length;
    }

    /**
     * Gets the length of number of bytes in a PCR for the event.
     *
     * @return Byte Array containing the PFP defined event content
     */
    public int getDigestLength() {
        return digestLength;
    }

    /**
     * Parses the event content and creates a human readable description of each event.
     *
     * @param event        the byte array holding the event data.
     * @param eventContent the byte array holding the event content.
     * @param eventNumber  event position within the event log.
     * @return String description of the event.
     * @throws CertificateException     if the event contains an event that cannot be processed.
     * @throws NoSuchAlgorithmException if an event contains an unsupported algorithm.
     * @throws IOException              if the event cannot be parsed.
     */
    public String processEvent(final byte[] event, final byte[] eventContent, final int eventNumber)
            throws CertificateException, NoSuchAlgorithmException, IOException {
        int eventID = (int) eventType;
        this.eventNumber = eventNumber;
        description += "Event# " + eventNumber + ": ";
        description += "Index PCR[" + getPcrIndex() + "]\n";
        description += "Event Type: 0x" + Long.toHexString(eventType) + " " + eventString(eventID);
        description += "\n";
        if (logFormat == 1) {   // Digest
            description += "digest (SHA-1): " + Hex.encodeHexString(this.digest);
        } else {
            description += "digest (SHA256): " + Hex.encodeHexString(this.digest);
        }
        if (eventID != UefiConstants.SIZE_4) {
            description += "\n";
        }
        // Calculate both the SHA1 and SHA256 on the event since this will equal the digest
        // field of about half the log messages.
        MessageDigest md1 = MessageDigest.getInstance("SHA-1");
        md1.update(event);
        eventDataSha1hash = md1.digest();
        MessageDigest md2 = MessageDigest.getInstance("SHA-256");
        md2.update(event);
        eventDataSha256hash = md2.digest();

        switch (eventID) {
            case EvConstants.EV_PREBOOT_CERT:
                description += " EV_PREBOOT_CERT" + "\n";
                break;
            case EvConstants.EV_POST_CODE:
                EvPostCode postCode = new EvPostCode(eventContent);
                description += "Event Content:\n" + postCode.toString();
                break;
            case EvConstants.EV_UNUSED:
                break;
            case EvConstants.EV_NO_ACTION:
                EvNoAction noAction = new EvNoAction(eventContent);
                description += "Event Content:\n" + noAction.toString();
                if (noAction.isSpecIDEvent()) {
                    EvEfiSpecIdEvent specID = noAction.getEvEfiSpecIdEvent();
                    version = specID.getVersionMajor() + "." + specID.getVersionMinor();
                    errata = specID.getErrata();
                }
                break;
            case EvConstants.EV_SEPARATOR:
                if (EvPostCode.isAscii(eventContent)) {
                    String separatorEventData = new String(eventContent, StandardCharsets.UTF_8);
                    if (!this.isBlank(eventContent)) {
                        description += "Separator event content = " + separatorEventData;
                    }
                }
                break;
            case EvConstants.EV_ACTION:
                description += "Event Content:\n"
                        + new String(eventContent, StandardCharsets.UTF_8);
                break;
            case EvConstants.EV_EVENT_TAG:
                EvEventTag eventTag = new EvEventTag(eventContent);
                description += eventTag.toString();
                break;
            case EvConstants.EV_S_CRTM_CONTENTS:
                EvSCrtmContents sCrtmContents = new EvSCrtmContents(eventContent);
                description += "Event Content:\n   " + sCrtmContents.toString();
                break;
            case EvConstants.EV_S_CRTM_VERSION:
                EvSCrtmVersion sCrtmVersion = new EvSCrtmVersion(eventContent);
                description += "Event Content:\n" + sCrtmVersion.toString();
                break;
            case EvConstants.EV_CPU_MICROCODE:
                break;
            case EvConstants.EV_PLATFORM_CONFIG_FLAGS:
                break;
            case EvConstants.EV_TABLE_OF_DEVICES:
                break;
            case EvConstants.EV_COMPACT_HASH:
                EvCompactHash compactHash = new EvCompactHash(eventContent);
                description += "Event Content:\n" + compactHash.toString();
                break;
            case EvConstants.EV_IPL:
                EvIPL ipl = new EvIPL(eventContent);
                description += "Event Content:\n" + ipl.toString();
                break;
            case EvConstants.EV_IPL_PARTITION_DATA:
                break;
            case EvConstants.EV_NONHOST_CODE:
                break;
            case EvConstants.EV_NONHOST_CONFIG:
                break;
            case EvConstants.EV_NONHOST_INFO:
                break;
            case EvConstants.EV_EV_OMIT_BOOT_DEVICES_EVENTS:
                break;
            case EvConstants.EV_EFI_EVENT_BASE:
                break;
            case EvConstants.EV_EFI_VARIABLE_DRIVER_CONFIG:
                UefiVariable efiVar = new UefiVariable(eventContent);
                String efiVarDescription = efiVar.toString().replace("\n", "\n   ");
                description += "Event Content:\n   " + efiVarDescription.substring(0,
                        efiVarDescription.length() - INDENT_3);
                break;
            case EvConstants.EV_EFI_VARIABLE_BOOT:
                description += "Event Content:\n" + new UefiVariable(eventContent).toString();
                break;
            case EvConstants.EV_EFI_BOOT_SERVICES_APPLICATION:
                EvEfiBootServicesApp bootServices = new EvEfiBootServicesApp(eventContent);
                description += "Event Content:\n" + bootServices.toString();
                break;
            case EvConstants.EV_EFI_BOOT_SERVICES_DRIVER: // same as EV_EFI_BOOT_SERVICES_APP
                EvEfiBootServicesApp bootDriver = new EvEfiBootServicesApp(eventContent);
                description += "Event Content:\n" + bootDriver.toString();
                break;
            case EvConstants.EV_EFI_RUNTIME_SERVICES_DRIVER:
                break;
            case EvConstants.EV_EFI_GPT_EVENT:
                description += "Event Content:\n" + new EvEfiGptPartition(eventContent).toString();
                break;
            case EvConstants.EV_EFI_ACTION:
                description += new String(eventContent, StandardCharsets.UTF_8);
                break;
            case EvConstants.EV_EFI_PLATFORM_FIRMWARE_BLOB:
                description += "Event Content:\n"
                        + new UefiFirmware(eventContent).toString();
                break;
            case EvConstants.EV_EFI_HANDOFF_TABLES:
                EvEfiHandoffTable efiTable = new EvEfiHandoffTable(eventContent);
                description += "Event Content:\n" + efiTable.toString();
                break;
            case EvConstants.EV_EFI_HCRTM_EVENT:
                break;
            case EvConstants.EV_EFI_VARIABLE_AUTHORITY:
                description += "Event Content:\n" + new UefiVariable(eventContent).toString();
                break;
            default:
                description += " Unknown Event found" + "\n";
        }
        return description;
    }

    /**
     * Converts the Event ID into a String As defined in the TCG PC Client FW Profile.
     * Event IDs have values larger than an integer,so a Long is used hold the value.
     *
     * @param event the event id.
     * @return TCG defined String that represents the event id
     */
    private static String eventString(final long event) {

        if (event == EvConstants.EV_PREBOOT_CERT) {
            return "EV_PREBOOT_CERT";
        } else if (event == EvConstants.EV_POST_CODE) {
            return "EV_POST_CODE";
        } else if (event == EvConstants.EV_UNUSED) {
            return "EV_Unused";
        } else if (event == EvConstants.EV_NO_ACTION) {
            return "EV_NO_ACTION";
        } else if (event == EvConstants.EV_SEPARATOR) {
            return "EV_SEPARATOR";
        } else if (event == EvConstants.EV_ACTION) {
            return "EV_ACTION";
        } else if (event == EvConstants.EV_EVENT_TAG) {
            return "EV_EVENT_TAG";
        } else if (event == EvConstants.EV_S_CRTM_CONTENTS) {
            return "EV_S_CRTM_CONTENTS";
        } else if (event == EvConstants.EV_S_CRTM_VERSION) {
            return "EV_S_CRTM_VERSION";
        } else if (event == EvConstants.EV_CPU_MICROCODE) {
            return "EV_CPU_MICROCODE";
        } else if (event == EvConstants.EV_PLATFORM_CONFIG_FLAGS) {
            return "EV_PLATFORM_CONFIG_FLAGS ";
        } else if (event == EvConstants.EV_TABLE_OF_DEVICES) {
            return "EV_TABLE_OF_DEVICES";
        } else if (event == EvConstants.EV_COMPACT_HASH) {
            return "EV_COMPACT_HASH";
        } else if (event == EvConstants.EV_IPL) {
            return "EV_IPL";
        } else if (event == EvConstants.EV_IPL_PARTITION_DATA) {
            return "EV_IPL_PARTITION_DATA";
        } else if (event == EvConstants.EV_NONHOST_CODE) {
            return "EV_NONHOST_CODE";
        } else if (event == EvConstants.EV_NONHOST_CONFIG) {
            return "EV_NONHOST_CONFIG";
        } else if (event == EvConstants.EV_NONHOST_INFO) {
            return "EV_NONHOST_INFO";
        } else if (event == EvConstants.EV_EV_OMIT_BOOT_DEVICES_EVENTS) {
            return "EV_EV_OMIT_BOOT_DEVICES_EVENTS";
        } else if (event == EvConstants.EV_EFI_EVENT_BASE) {
            return "EV_EFI_EVENT_BASE";
        } else if (event == EvConstants.EV_EFI_VARIABLE_DRIVER_CONFIG) {
            return "EV_EFI_VARIABLE_DRIVER_CONFIG";
        } else if (event == EvConstants.EV_EFI_VARIABLE_BOOT) {
            return "EV_EFI_VARIABLE_BOOT";
        } else if (event == EvConstants.EV_EFI_BOOT_SERVICES_APPLICATION) {
            return "EV_EFI_BOOT_SERVICES_APPLICATION";
        } else if (event == EvConstants.EV_EFI_BOOT_SERVICES_DRIVER) {
            return "EV_EFI_BOOT_SERVICES_DRIVER";
        } else if (event == EvConstants.EV_EFI_RUNTIME_SERVICES_DRIVER) {
            return "EV_EFI_RUNTIME_SERVICES_DRIVER";
        } else if (event == EvConstants.EV_EFI_GPT_EVENT) {
            return "EV_EFI_GPT_EVENT";
        } else if (event == EvConstants.EV_EFI_ACTION) {
            return "EV_EFI_ACTION";
        } else if (event == EvConstants.EV_EFI_PLATFORM_FIRMWARE_BLOB) {
            return "EV_EFI_PLATFORM_FIRMWARE_BLOB";
        } else if (event == EvConstants.EV_EFI_HANDOFF_TABLES) {
            return "EV_EFI_HANDOFF_TABLES";
        } else if (event == EvConstants.EV_EFI_HCRTM_EVENT) {
            return "EV_EFI_HCRTM_EVENT";
        } else if (event == EvConstants.EV_EFI_VARIABLE_AUTHORITY) {
            return "EV_EFI_VARIABLE_AUTHORITY";
        } else {
            return "Unknown Event ID " + event + " encountered";
        }
    }

    /**
     * Human readable output of a check of input against the current event hash.
     *
     * @return human readable string.
     */
    private String eventHashCheck() {
        String result = "";
        if (logFormat == 1) {
            if (Arrays.equals(this.digest, eventDataSha1hash)) {
                result
                        += "Event digest matched hash of the event data " + "\n";
            } else {
                result += "Event digest DID NOT match the hash of the event data :"
                        + Hex.encodeHexString(getEventDigest()) + "\n";
            }
        } else {
            if (Arrays.equals(this.digest, eventDataSha256hash)) {
                result += "Event digest matched hash of the event data " + "\n";
            } else {
                result += "Event digest DID NOT match the hash of the event data :"
                        + Hex.encodeHexString(getEventDigest()) + "\n";
            }
        }
        return result;
    }

    /**
     * This method takes in an event and compares the hashes to verify that they match.
     * @param tpmPcrEvent an event to match.
     * @return true if the event # matches and the hash is correct.
     */
    public boolean eventCompare(final TpmPcrEvent tpmPcrEvent) {
        if (tpmPcrEvent.getPcrIndex() != this.getPcrIndex()) {
            return false;
        }

        return Arrays.equals(this.digest, tpmPcrEvent.getEventDigest());
    }

    /**
     * Checks a byte array for all zeros.
     *
     * @param array holds data to check.
     * @return true of all zeros are found.
     */
    public boolean isBlank(final byte[] array) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Getter for error.
     * @return there
     */
    public boolean isError() {
        return error;
    }

    /**
     * Setter for error.
     * @param error parameter
     */
    public void setError(final boolean error) {
        this.error = error;
    }

    /**
     * Human readable string representing the contents of the Event Log.
     *
     * @return Description of the log.
     */
    public String toString() {
        return description + "\n";
    }

    /**
     * Human readable string representing the contents of the Event Log.
     *
     * @param bEvent    event Flag.
     * @param bContent  content flag.
     * @param bHexEvent hex event flag.
     * @return Description of the log.
     */
    public String toString(final boolean bEvent, final boolean bContent, final boolean bHexEvent) {
        StringBuilder sb = new StringBuilder();
        if (bEvent) {
            sb.append(description);
        }
        if (bHexEvent) {
            if (bEvent || bContent) {
                sb.append("\n");
            }
            byte[] eventData = getEvent();
            sb.append("Event (Hex no Content) (" + eventData.length + " bytes): "
                    + Hex.encodeHexString(eventData));
        }
        if (bContent) {
            byte[] evContent = getEventContent();
            if (bEvent) {
                sb.append("\n");
            }
            sb.append("Event content (Hex) (" + evContent.length + " bytes): "
                    + Hex.encodeHexString(evContent));
        }
        return sb.toString() + "\n";
    }

    /**
     * Remove bad visual value text.
     * @param text content to operate over.
     * @return cleared string
     */
    public String cleanTextContent(final String text) {
        String result;
        // strips off all non-ASCII characters
        result = text.replaceAll("[^\\x00-\\x7F]", "");

        // erases all the ASCII control characters
        result = result.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");

        // removes non-printable characters from Unicode
        result = result.replaceAll("\\p{C}", "");

        return result.trim();
    }
}
