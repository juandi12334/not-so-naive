package co.edu.ufps.notsonaive.service;

import co.edu.ufps.notsonaive.exception.InvalidFileException;
import co.edu.ufps.notsonaive.model.TextDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ufps.util.colecciones_seed.ListaCD;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Reads and validates uploaded plain-text files.
 *
 * <p>Validation rules (applied to the raw bytes, before any decoding, so an
 * incompatible file is never silently interpreted as ASCII):</p>
 * <ul>
 *   <li>A file must be present, have the {@code .txt} extension and not be empty.</li>
 *   <li>Its size must not exceed the configured limit.</li>
 *   <li>Every byte must be printable ASCII (0x20-0x7E) or TAB, LF, CR.
 *       Bytes above 0x7F (accented letters, UTF-8, Latin-1...) and other control
 *       bytes (typical of binary files) are rejected with their line and column.</li>
 *   <li>A UTF-8 byte-order mark followed by pure ASCII content is removed and reported.</li>
 * </ul>
 *
 * <p>Line endings CRLF and CR are normalized to LF. All positions reported by
 * the application refer to this normalized text.</p>
 */
@Service
public class FileService {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final long maxBytes;

    public FileService(@Value("${app.max-file-bytes:2097152}") long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    /**
     * Validates the upload and returns its normalized text.
     *
     * @throws InvalidFileException with a user-facing message when invalid
     */
    public TextDocument read(MultipartFile file) {
        if (file == null || file.isEmpty() && (file.getOriginalFilename() == null
                || file.getOriginalFilename().isBlank())) {
            throw new InvalidFileException("No file was selected. Choose a .txt file.");
        }
        String name = file.getOriginalFilename() == null ? "file.txt" : file.getOriginalFilename();
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        if (!name.toLowerCase(Locale.ROOT).endsWith(".txt")) {
            throw new InvalidFileException("Only plain-text files with the .txt extension are accepted (received \""
                    + name + "\").");
        }
        if (file.getSize() > maxBytes) {
            throw new InvalidFileException("The file is too large (" + file.getSize()
                    + " bytes). Maximum allowed: " + maxBytes + " bytes.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new InvalidFileException("The file could not be read.");
        }
        return parse(name, bytes);
    }

    /** Name shown for text typed in the page instead of an uploaded file. */
    public static final String TYPED_TEXT_NAME = "typed-text.txt";

    /**
     * Validates text typed (or pasted) by the user and builds the document. The same
     * rules as for files apply: not empty, the same size limit, ASCII only, and line
     * endings normalized to LF.
     *
     * @throws InvalidFileException with a user-facing message when invalid
     */
    public TextDocument fromTypedText(String text) {
        if (text == null || text.isEmpty()) {
            throw new InvalidFileException("The typed text is empty. Type some text or choose a .txt file.");
        }
        if (text.length() > maxBytes) {
            throw new InvalidFileException("The typed text is too long (" + text.length()
                    + " characters). Maximum allowed: " + maxBytes + " characters.");
        }
        int line = 1;
        int column = 1;
        byte[] bytes = new byte[text.length()];
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean allowed = (c >= 0x20 && c <= 0x7E) || c == '\t' || c == '\n' || c == '\r';
            if (!allowed) {
                throw new InvalidFileException(String.format(Locale.ROOT,
                        "The typed text is not ASCII: character '%s' (U+%04X) at line %d, column %d is not allowed "
                                + "(for example an accented letter or ñ).", c, (int) c, line, column));
            }
            bytes[i] = (byte) c;
            if (c == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return parse(TYPED_TEXT_NAME, bytes);
    }

    /**
     * Validates raw bytes and builds the document. Public so it can be unit-tested
     * without the web layer.
     */
    public TextDocument parse(String name, byte[] bytes) {
        if (bytes.length == 0) {
            throw new InvalidFileException("The file \"" + name + "\" is empty. It must contain text.");
        }
        ListaCD<String> notices = new ListaCD<>();
        int start = 0;
        if (startsWithBom(bytes)) {
            start = UTF8_BOM.length;
            notices.insertarAlFinal("A UTF-8 byte-order mark (BOM) at the start of the file was removed; "
                    + "the rest of the content is pure ASCII.");
        }
        if (start == bytes.length) {
            throw new InvalidFileException("The file \"" + name + "\" is empty. It must contain text.");
        }

        int line = 1;
        int column = 1;
        boolean hasCr = false;
        for (int i = start; i < bytes.length; i++) {
            int b = bytes[i] & 0xFF;
            boolean allowed = (b >= 0x20 && b <= 0x7E) || b == '\t' || b == '\n' || b == '\r';
            if (!allowed) {
                if (b > 0x7F) {
                    throw new InvalidFileException(String.format(Locale.ROOT,
                            "The file is not ASCII: byte 0x%02X at line %d, column %d is outside the ASCII "
                                    + "range (for example an accented letter, ñ or a UTF-8/Latin-1 symbol). "
                                    + "Save the file as plain ASCII text.", b, line, column));
                }
                throw new InvalidFileException(String.format(Locale.ROOT,
                        "The file contains a control byte 0x%02X at line %d, column %d. "
                                + "It looks like a binary file, not plain text.", b, line, column));
            }
            if (b == '\r') {
                hasCr = true;
            }
            if (b == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }

        String content = new String(bytes, start, bytes.length - start, StandardCharsets.US_ASCII);
        if (hasCr) {
            content = content.replace("\r\n", "\n").replace('\r', '\n');
            notices.insertarAlFinal("Line endings were normalized to LF (\\n). Positions refer to the normalized text.");
        }
        int lines = 1;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                lines++;
            }
        }
        if (content.endsWith("\n")) {
            lines--;
        }
        return new TextDocument(name, bytes.length, content, Math.max(lines, 1), notices);
    }

    private static boolean startsWithBom(byte[] bytes) {
        return bytes.length >= 3 && bytes[0] == UTF8_BOM[0] && bytes[1] == UTF8_BOM[1] && bytes[2] == UTF8_BOM[2];
    }
}
